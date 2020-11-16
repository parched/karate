/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Results;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.JsonUtils;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author pthomas3
 */
public class Engine {

    private Engine() {
        // only static methods
    }

    private static final double MILLION = 1000000;
    private static final double BILLION = 1000000000;

    public static double nanosToSeconds(long nanos) {
        return (double) nanos / BILLION;
    }

    public static double nanosToMillis(long nanos) {
        return (double) nanos / MILLION;
    }

    public static File saveResultJson(String targetDir, FeatureResult result, String fileName) {
        List<Map> single = Collections.singletonList(result.toMap());
        String json = JsonUtils.toJson(single);
        if (fileName == null) {
            fileName = result.getPackageQualifiedName() + ".json";
        }
        File file = new File(targetDir + File.separator + fileName);
        FileUtils.writeToFile(file, json);
        return file;
    }

    public static String formatNanos(long nanos, DecimalFormat formatter) {
        return formatter.format(nanosToSeconds(nanos));
    }

    public static String formatMillis(double millis, DecimalFormat formatter) {
        return formatter.format(millis / 1000);
    }

    private static Throwable appendSteps(List<StepResult> steps, StringBuilder sb) {
        Throwable error = null;
        for (StepResult sr : steps) {
            int length = sb.length();
            sb.append(sr.getStep().getPrefix());
            sb.append(' ');
            sb.append(sr.getStep().getText());
            sb.append(' ');
            do {
                sb.append('.');
            } while (sb.length() - length < 75);
            sb.append(' ');
            sb.append(sr.getResult().getStatus());
            sb.append('\n');
            if (sr.getResult().isFailed()) {
                sb.append("\nStack Trace:\n");
                StringWriter sw = new StringWriter();
                error = sr.getResult().getError();
                error.printStackTrace(new PrintWriter(sw));
                sb.append(sw.toString());
                sb.append('\n');
            }
        }
        return error;
    }

    public static File saveResultXml(String targetDir, FeatureResult result, String fileName) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("0.######");
        Document doc = XmlUtils.newDocument();
        Element root = doc.createElement("testsuite");
        doc.appendChild(root);
        root.setAttribute("name", result.getDisplayUri()); // will be uri
        root.setAttribute("skipped", "0");
        String baseName = result.getPackageQualifiedName();
        int testCount = 0;
        int failureCount = 0;
        long totalDuration = 0;
        Throwable error;
        Iterator<ScenarioResult> iterator = result.getScenarioResults().iterator();
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            ScenarioResult sr = iterator.next();
            totalDuration += sr.getDurationNanos();
            if (sr.isFailed()) {
                failureCount++;
            }
            Element testCase = doc.createElement("testcase");
            root.appendChild(testCase);
            testCase.setAttribute("classname", baseName);
            testCount++;
            long duration = sr.getDurationNanos();
            error = appendSteps(sr.getStepResults(), sb);
            String name = sr.getScenario().getName();
            if (StringUtils.isBlank(name)) {
                name = testCount + "";
            }
            testCase.setAttribute("name", name);
            testCase.setAttribute("time", formatNanos(duration, formatter));
            Element stepsHolder;
            if (error != null) {
                stepsHolder = doc.createElement("failure");
                stepsHolder.setAttribute("message", error.getMessage());
            } else {
                stepsHolder = doc.createElement("system-out");
            }
            testCase.appendChild(stepsHolder);
            stepsHolder.setTextContent(sb.toString());
        }
        root.setAttribute("tests", testCount + "");
        root.setAttribute("failures", failureCount + "");
        root.setAttribute("time", formatNanos(totalDuration, formatter));
        String xml = XmlUtils.toString(doc, true);
        if (fileName == null) {
            fileName = baseName + ".xml";
        }
        File file = new File(targetDir + File.separator + fileName);
        FileUtils.writeToFile(file, xml);
        return file;
    }

    public static File saveStatsJson(String targetDir, Results results) {
        String json = JsonUtils.toJson(results.toMap());
        String fileName = "results-json.txt";
        File file = new File(targetDir + File.separator + fileName);
        FileUtils.writeToFile(file, json);
        return file;
    }

}
