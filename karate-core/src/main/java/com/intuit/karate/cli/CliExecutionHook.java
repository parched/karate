/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.cli;

import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.HtmlFeatureReport;
import com.intuit.karate.core.HtmlSummaryReport;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.RuntimeHook;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.Suite;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author pthomas3
 */
public class CliExecutionHook implements RuntimeHook {
    
    private final boolean htmlReport;
    private final String targetDir;
    private final boolean intellij;
    private final ReentrantLock LOCK = new ReentrantLock();
    private final HtmlSummaryReport summary;
    
    public CliExecutionHook(boolean htmlReport, String targetDir, boolean intellij) {
        this.htmlReport = htmlReport;
        this.targetDir = targetDir;
        this.intellij = intellij;
        if (intellij) {
            log(String.format(TEMPLATE_ENTER_THE_MATRIX, getCurrentTime()));
        }
        summary = htmlReport ? new HtmlSummaryReport() : null;
    }
    
    @Override
    public void beforeSuite(Suite suite) {
        
    }    
    
    @Override
    public void afterSuite(Suite suite) {
        if (htmlReport) {
            summary.save(targetDir);
        }
    }    
    
    @Override
    public boolean beforeStep(Step step, ScenarioRuntime sr) {
        return true;
    }
    
    @Override
    public void afterStep(StepResult result, ScenarioRuntime sr) {
        
    }    
    
    @Override
    public boolean beforeScenario(ScenarioRuntime sr) {
        if (intellij && sr.caller.depth == 0) {
            Scenario scenario = sr.scenario;
            Path absolutePath = scenario.getFeature().getResource().getPath().toAbsolutePath();
            log(String.format(TEMPLATE_TEST_STARTED, getCurrentTime(), absolutePath + ":" + scenario.getLine(), escape(scenario.getNameForReport())));
            // log(String.format(TEMPLATE_SCENARIO_STARTED, getCurrentTime()));
        }
        return true;
    }
    
    @Override
    public void afterScenario(ScenarioRuntime sr) {
        if (intellij && sr.caller.depth == 0) {
            Scenario scenario = sr.scenario;
            if (sr.result.isFailed()) {
                StringUtils.Pair error = details(sr.result.getError());
                log(String.format(TEMPLATE_TEST_FAILED, getCurrentTime(), escape(error.right), escape(error.left), escape(scenario.getNameForReport()), ""));
            }
            log(String.format(TEMPLATE_TEST_FINISHED, getCurrentTime(), sr.result.getDurationNanos() / 1000000, escape(scenario.getNameForReport())));
        }
    }
    
    @Override
    public boolean beforeFeature(FeatureRuntime fr) {
        if (intellij && fr.caller.depth == 0) {
            Feature feature = fr.feature;
            Path absolutePath = feature.getResource().getPath().toAbsolutePath();
            log(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), absolutePath + ":" + feature.getLine(), escape(feature.getNameForReport())));
        }
        return true;
    }
    
    @Override
    public void afterFeature(FeatureRuntime fr) {
        if (fr.caller.depth > 0) {
            return;
        }
        if (intellij) {
            log(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), escape(fr.feature.getNameForReport())));
        }
        if (fr.result.getScenarioCount() == 0) {
            return;
        }
        if (htmlReport && !fr.result.isEmpty()) {
            HtmlFeatureReport.saveFeatureResult(targetDir, fr.result);
            summary.addFeatureResult(fr.result);
        }
        if (LOCK.tryLock()) {
            Engine.saveStatsJson(targetDir, fr.suite.results);
            LOCK.unlock();
        }
    }
    
    private static void log(String s) {
        System.out.println(s);
    }
    
    private static String getCurrentTime() {
        return DATE_FORMAT.format(new Date());
    }
    
    private static String escape(String source) {
        if (source == null) {
            return "";
        }
        return source.replace("|", "||").replace("\n", "|n").replace("\r", "|r").replace("'", "|'").replace("[", "|[").replace("]", "|]");
    }
    
    private static StringUtils.Pair details(Throwable error) {
        String fullMessage = error.getMessage().replace("\r", "").replace("\t", "  ");
        String[] messageInfo = fullMessage.split("\n", 2);
        if (messageInfo.length == 2) {
            return StringUtils.pair(messageInfo[0].trim(), messageInfo[1].trim());
        } else {
            return StringUtils.pair(fullMessage, "");
        }
    }
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSZ");
    
    private static final String TEAMCITY_PREFIX = "##teamcity";
    private static final String TEMPLATE_TEST_STARTED = TEAMCITY_PREFIX + "[testStarted timestamp = '%s' locationHint = '%s' captureStandardOutput = 'true' name = '%s']";
    private static final String TEMPLATE_TEST_FAILED = TEAMCITY_PREFIX + "[testFailed timestamp = '%s' details = '%s' message = '%s' name = '%s' %s]";
    private static final String TEMPLATE_TEST_FINISHED = TEAMCITY_PREFIX + "[testFinished timestamp = '%s' duration = '%s' name = '%s']";
    private static final String TEMPLATE_ENTER_THE_MATRIX = TEAMCITY_PREFIX + "[enteredTheMatrix timestamp = '%s']";
    private static final String TEMPLATE_TEST_SUITE_STARTED = TEAMCITY_PREFIX + "[testSuiteStarted timestamp = '%s' locationHint = 'file://%s' name = '%s']";
    private static final String TEMPLATE_TEST_SUITE_FINISHED = TEAMCITY_PREFIX + "[testSuiteFinished timestamp = '%s' name = '%s']";
    
}
