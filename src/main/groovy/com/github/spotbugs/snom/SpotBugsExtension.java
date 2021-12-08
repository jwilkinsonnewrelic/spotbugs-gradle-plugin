/*
 * Copyright 2021 SpotBugs team
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.spotbugs.snom;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The extension to configure the SpotBugs Gradle plugin. Most of properties in this extension will
 * be used as the default property of all {@link SpotBugsTask}. All properties are optional.
 *
 * <p><strong>Usage:</strong>
 *
 * <p>After you apply the SpotBugs Gradle plugin to project, write extension like below:<div><code>
 * spotbugs {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;ignoreFailures = false<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;showStackTraces = true<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;showProgress = false<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;reportLevel = 'default'<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;effort = 'default'<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;visitors = [ 'FindSqlInjection', 'SwitchFallthrough' ]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;omitVisitors = [ 'FindNonShortCircuit' ]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;reportsDir = file("$buildDir/reports/spotbugs")<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;includeFilter = file('spotbugs-include.xml')<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;excludeFilter = file('spotbugs-exclude.xml')<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;onlyAnalyze = ['com.foobar.MyClass', 'com.foobar.mypkg.*']<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;projectName = name<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;release = version<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;extraArgs = [ '-nested:false' ]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;jvmArgs = [ '-Duser.language=ja' ]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;maxHeapSize = '512m'<br>
 * }</code></div>
 *
 * <p>See also <a href="https://spotbugs.readthedocs.io/en/stable/running.html">SpotBugs Manual
 * about configuration</a>.
 */
public abstract class SpotBugsExtension {
  static final String DEFAULT_REPORTS_DIR_NAME = "spotbugs";

  @NonNull
  public abstract Property<Boolean> getIgnoreFailures();

  @NonNull
  public abstract Property<Boolean> getShowStackTraces();
  /** Property to enable progress reporting during the analysis. Default value is {@code false}. */
  @NonNull
  public abstract Property<Boolean> getShowProgress();
  /**
   * Property to enable visitors (detectors) for analysis. Default is empty that means all visitors
   * run analysis.
   */
  @NonNull
  public abstract ListProperty<String> getVisitors();
  /**
   * Property to disable visitors (detectors) for analysis. Default is empty that means SpotBugs
   * omits no visitor.
   */
  @NonNull
  public abstract ListProperty<String> getOmitVisitors();
  /**
   * Property to set the directory to generate report files. Default is {@code
   * "$buildDir/reports/spotbugs"}.
   *
   * <p>Note that each {@link SpotBugsTask} creates own sub-directory in this directory.
   */
  @NonNull
  public abstract DirectoryProperty getReportsDir();
  /**
   * Property to set the filter file to limit which bug should be reported.
   *
   * <p>Note that this property will NOT limit which bug should be detected. To limit the target
   * classes to analyze, use {@link #getOnlyAnalyze()} instead. To limit the visitors (detectors) to
   * run, use {@link #getVisitors()} and {@link #getOmitVisitors()} instead.
   *
   * <p>See also <a href="https://spotbugs.readthedocs.io/en/stable/filter.html">SpotBugs Manual
   * about Filter file</a>.
   */
  @NonNull
  public abstract RegularFileProperty getIncludeFilter();
  /**
   * Property to set the filter file to limit which bug should be reported.
   *
   * <p>Note that this property will NOT limit which bug should be detected. To limit the target
   * classes to analyze, use {@link #getOnlyAnalyze()} instead. To limit the visitors (detectors) to
   * run, use {@link #getVisitors()} and {@link #getOmitVisitors()} instead.
   *
   * <p>See also <a href="https://spotbugs.readthedocs.io/en/stable/filter.html">SpotBugs Manual
   * about Filter file</a>.
   */
  @NonNull
  public abstract RegularFileProperty getExcludeFilter();
  /**
   * Property to set the baseline file. This file is a Spotbugs result file, and all bugs reported
   * in this file will not be reported in the final output.
   */
  @NonNull
  public abstract RegularFileProperty getBaselineFile();
  /**
   * Property to specify the target classes for analysis. Default value is empty that means all
   * classes are analyzed.
   */
  @NonNull
  public abstract ListProperty<String> getOnlyAnalyze();
  /**
   * Property to specify the name of project. Some reporting formats use this property. Default
   * value is the name of your Gradle project.
   */
  @NonNull
  public abstract Property<String> getProjectName();
  /**
   * Property to specify the release identifier of project. Some reporting formats use this
   * property. Default value is the version of your Gradle project.
   */
  @NonNull
  public abstract Property<String> getRelease();
  /**
   * Property to specify the extra arguments for SpotBugs. Default value is empty so SpotBugs will
   * get no extra argument.
   */
  @NonNull
  public abstract ListProperty<String> getExtraArgs();
  /**
   * Property to specify the extra arguments for JVM process. Default value is empty so JVM process
   * will get no extra argument.
   */
  @NonNull
  public abstract ListProperty<String> getJvmArgs();
  /**
   * Property to specify the max heap size ({@code -Xmx} option) of JVM process. Default value is
   * empty so the default configuration made by Gradle will be used.
   */
  @NonNull
  public abstract Property<String> getMaxHeapSize();

  @NonNull
  public abstract Property<String> getToolVersion();

  @NonNull
  public abstract Property<Boolean> getUseAuxclasspathFile();

  @NonNull
  public abstract Property<Boolean> getUseJavaToolchains();

  /** Property to specify the level to report bugs. Default value is {@link Confidence#DEFAULT}. */
  @NonNull
  public abstract Property<Confidence> getReportLevel();

  /** Property to adjust SpotBugs detectors. Default value is {@link Effort#DEFAULT}. */
  @NonNull
  public abstract Property<Effort> getEffort();
}
