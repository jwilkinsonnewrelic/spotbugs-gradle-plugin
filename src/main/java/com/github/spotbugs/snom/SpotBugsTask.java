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

import com.github.spotbugs.snom.internal.SpotBugsHtmlReport;
import com.github.spotbugs.snom.internal.SpotBugsRunnerForHybrid;
import com.github.spotbugs.snom.internal.SpotBugsRunnerForJavaExec;
import com.github.spotbugs.snom.internal.SpotBugsRunnerForWorker;
import com.github.spotbugs.snom.internal.SpotBugsSarifReport;
import com.github.spotbugs.snom.internal.SpotBugsTextReport;
import com.github.spotbugs.snom.internal.SpotBugsXmlReport;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import groovy.lang.Closure;
import java.io.File;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.util.ClosureBackedAction;
import org.gradle.workers.WorkerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Gradle task to run the SpotBugs analysis. All properties are optional.
 *
 * <p><strong>Usage for Java project:</strong>
 *
 * <p>After you apply the SpotBugs Gradle plugin to project, {@code SpotBugsTask} is automatically
 * generated for each sourceSet. If you want to configure generated tasks, write build scripts like
 * below:<div><code>
 * spotbugsMain {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;sourceDirs = sourceSets.main.allSource.srcDirs<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;classDirs = sourceSets.main.output<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;auxClassPaths = sourceSets.main.compileClasspath<br>
 * <br>
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
 * &nbsp;&nbsp;&nbsp;&nbsp;baselineFile = file('spotbugs-baseline.xml')<br>
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
@CacheableTask
public abstract class SpotBugsTask extends DefaultTask implements VerificationTask {
  private static final String FEATURE_FLAG_WORKER_API = "com.github.spotbugs.snom.worker";
  private static final String FEATURE_FLAG_HYBRID_WORKER =
      "com.github.spotbugs.snom.javaexec-in-worker";

  private final Logger log = LoggerFactory.getLogger(SpotBugsTask.class);

  private final WorkerExecutor workerExecutor;

  private final Property<Boolean> ignoreFailures;

  @Override
  public boolean getIgnoreFailures() {
    return ignoreFailures.get();
  }

  @Override
  public void setIgnoreFailures(boolean ignoreFailures) {
    this.ignoreFailures.set(ignoreFailures);
  }

  @Input
  @Optional
  @NonNull
  public abstract Property<Boolean> getShowStackTraces();

  /** Property to enable progress reporting during the analysis. Default value is {@code false}. */
  @Input
  @Optional
  @NonNull
  public abstract Property<Boolean> getShowProgress();
  /** Property to specify the level to report bugs. Default value is {@link Confidence#DEFAULT}. */
  @Input
  @Optional
  @NonNull
  public abstract Property<Confidence> getReportLevel();
  /** Property to adjust SpotBugs detectors. Default value is {@link Effort#DEFAULT}. */
  @Input
  @Optional
  @NonNull
  public abstract Property<Effort> getEffort();
  /**
   * Property to enable visitors (detectors) for analysis. Default is empty that means all visitors
   * run analysis.
   */
  @Input
  @NonNull
  public abstract ListProperty<String> getVisitors();
  /**
   * Property to disable visitors (detectors) for analysis. Default is empty that means SpotBugs
   * omits no visitor.
   */
  @Input
  @NonNull
  public abstract ListProperty<String> getOmitVisitors();

  /**
   * Property to set the directory to generate report files. Default is {@code
   * "$buildDir/reports/spotbugs/$taskName"}.
   */
  @Internal("Refer the destination of each report instead.")
  @NonNull
  public abstract DirectoryProperty getReportsDir();

  private final NamedDomainObjectContainer<SpotBugsReport> reports;

  /**
   * Property to specify which report you need.
   *
   * @see SpotBugsReport
   */
  @Internal
  @NonNull
  public NamedDomainObjectContainer<SpotBugsReport> getReports() {
    return reports;
  }

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
  @Optional
  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
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
  @Optional
  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  @NonNull
  public abstract RegularFileProperty getExcludeFilter();
  /**
   * Property to set the baseline file. This file is a Spotbugs result file, and all bugs reported
   * in this file will not be reported in the final output.
   */
  @Optional
  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  @NonNull
  public abstract RegularFileProperty getBaselineFile();
  /**
   * Property to specify the target classes for analysis. Default value is empty that means all
   * classes are analyzed.
   */
  @Input
  @NonNull
  public abstract ListProperty<String> getOnlyAnalyze();
  /**
   * Property to specify the name of project. Some reporting formats use this property. Default
   * value is {@code "${project.name} (${task.name})"}. <br>
   * Note that this property, if treated as a task input, can break cacheability.<br>
   * As such, it has been marked {@link Internal} to exclude it from task up-to-date and
   * cacheability checks.
   */
  @Internal
  @NonNull
  public abstract Property<String> getProjectName();
  /**
   * Property to specify the release identifier of project. Some reporting formats use this
   * property. Default value is the version of your Gradle project.
   */
  @Input
  @NonNull
  public abstract Property<String> getRelease();
  /**
   * Property to specify the extra arguments for SpotBugs. Default value is empty so SpotBugs will
   * get no extra argument.
   */
  @Optional
  @Input
  @NonNull
  public abstract ListProperty<String> getExtraArgs();
  /**
   * Property to specify the extra arguments for JVM process. Default value is empty so JVM process
   * will get no extra argument.
   */
  @Optional
  @Input
  @NonNull
  public abstract ListProperty<String> getJvmArgs();
  /**
   * Property to specify the max heap size ({@code -Xmx} option) of JVM process. Default value is
   * empty so the default configuration made by Gradle will be used.
   */
  @Optional
  @Input
  @NonNull
  public abstract Property<String> getMaxHeapSize();

  @Nullable private FileCollection sourceDirs;

  /**
   * Property to specify the directories that contain the source of target classes to analyze.
   * Default value is the source directory of the target sourceSet.
   */
  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public FileCollection getSourceDirs() {
    return this.sourceDirs;
  }

  public void setSourceDirs(FileCollection sourceDirs) {
    this.sourceDirs = sourceDirs;
  }

  @Nullable private FileCollection classDirs;

  /**
   * Property to specify the directories that contains the target classes to analyze. Default value
   * is the output directory of the target sourceSet.
   */
  @Internal
  public FileCollection getClassDirs() {
    return this.classDirs;
  }

  public void setClassDirs(FileCollection classDirs) {
    this.classDirs = classDirs;
  }

  @Nullable private FileCollection auxClassPaths;

  /**
   * Property to specify the aux class paths that contains the libraries to refer during analysis.
   * Default value is the compile-scope dependencies of the target sourceSet.
   */
  @Classpath
  public FileCollection getAuxClassPaths() {
    return this.auxClassPaths;
  }

  public void setAuxClassPaths(FileCollection auxClassPaths) {
    this.auxClassPaths = auxClassPaths;
  }

  /**
   * Property to enable auxclasspathFromFile and prevent Argument List Too Long issues in java
   * processes. Default value is {@code false}.
   */
  @Input
  @Optional
  @NonNull
  public abstract Property<Boolean> getUseAuxclasspathFile();

  @Nullable private FileCollection classes;

  private boolean enableWorkerApi;
  private boolean enableHybridWorker;

  public void setClasses(FileCollection fileCollection) {
    this.classes = fileCollection;
  }

  /**
   * Property to specify the target classes to analyse by SpotBugs. Default value is the all
   * existing {@code .class} files in {@link #getClassDirs}.
   */
  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  @SkipWhenEmpty
  @NonNull
  public FileCollection getClasses() {
    if (classes == null) {
      return getClassDirs().getAsFileTree().filter(file -> file.getName().endsWith(".class"));
    } else {
      return classes;
    }
  }

  @Nested
  @Optional
  public abstract Property<JavaLauncher> getLauncher();

  @Inject
  public SpotBugsTask(ObjectFactory objects, WorkerExecutor workerExecutor) {
    this.workerExecutor = Objects.requireNonNull(workerExecutor);
    this.ignoreFailures = objects.property(Boolean.class);
    this.auxClassPaths = objects.fileCollection();
    this.sourceDirs = objects.fileCollection();
    this.classDirs = objects.fileCollection();
    reports =
        objects.domainObjectContainer(
            SpotBugsReport.class,
            name -> {
              switch (name) {
                case "html":
                  return objects.newInstance(SpotBugsHtmlReport.class, objects, this);
                case "xml":
                  return objects.newInstance(SpotBugsXmlReport.class, objects, this);
                case "text":
                  return objects.newInstance(SpotBugsTextReport.class, objects, this);
                case "sarif":
                  return objects.newInstance(SpotBugsSarifReport.class, objects, this);
                default:
                  throw new InvalidUserDataException(name + " is invalid as the report name");
              }
            });
    setDescription("Run SpotBugs analysis.");
    setGroup(JavaBasePlugin.VERIFICATION_GROUP);
  }

  /**
   * Set properties from extension right after the task creation. User may overwrite these
   * properties by build script.
   *
   * @param extension the source extension to copy the properties.
   */
  public void init(
      SpotBugsExtension extension, boolean enableWorkerApi, boolean enableHybridWorker) {
    ignoreFailures.convention(extension.getIgnoreFailures());
    getShowStackTraces().convention(extension.getShowStackTraces());
    getShowProgress().convention(extension.getShowProgress());
    getReportLevel().convention(extension.getReportLevel());
    getEffort().convention(extension.getEffort());
    getVisitors().convention(extension.getVisitors());
    getOmitVisitors().convention(extension.getOmitVisitors());
    // the default reportsDir is "$buildDir/reports/spotbugs/"
    getReportsDir().convention(extension.getReportsDir());
    getIncludeFilter().convention(extension.getIncludeFilter());
    getExcludeFilter().convention(extension.getExcludeFilter());
    getBaselineFile().convention(extension.getBaselineFile());
    getOnlyAnalyze().convention(extension.getOnlyAnalyze());
    getProjectName()
        .convention(extension.getProjectName().map(p -> String.format("%s (%s)", p, getName())));
    getRelease().convention(extension.getRelease());
    getJvmArgs().convention(extension.getJvmArgs());
    getExtraArgs().convention(extension.getExtraArgs());
    getMaxHeapSize().convention(extension.getMaxHeapSize());
    getUseAuxclasspathFile().convention(extension.getUseAuxclasspathFile());

    if (extension.getUseJavaToolchains().isPresent() && extension.getUseJavaToolchains().get()) {
      configureJavaLauncher();
    }

    this.enableWorkerApi = enableWorkerApi;
    this.enableHybridWorker = enableHybridWorker;
  }

  /** Set convention for default java launcher based on Toolchain configuration */
  private void configureJavaLauncher() {
    JavaToolchainSpec toolchain =
        getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain();
    JavaToolchainService service =
        getProject().getExtensions().getByType(JavaToolchainService.class);
    Provider<JavaLauncher> defaultLauncher = service.launcherFor(toolchain);
    getLauncher().convention(defaultLauncher);
  }

  @TaskAction
  public void run() {
    if (!enableWorkerApi) {
      log.info("Running SpotBugs by JavaExec...");
      new SpotBugsRunnerForJavaExec(getLauncher()).run(this);
    } else if (enableHybridWorker) {
      log.info("Running SpotBugs by Gradle no-isolated Worker...");
      new SpotBugsRunnerForHybrid(workerExecutor, getLauncher()).run(this);
    } else {
      log.info("Running SpotBugs by Gradle process-isolated Worker...");
      new SpotBugsRunnerForWorker(workerExecutor, getLauncher()).run(this);
    }
  }

  public final NamedDomainObjectContainer<? extends SpotBugsReport> reports(
      Closure<NamedDomainObjectContainer<? extends SpotBugsReport>> closure) {
    return reports(
        new ClosureBackedAction<NamedDomainObjectContainer<? extends SpotBugsReport>>(closure));
  }

  public final NamedDomainObjectContainer<? extends SpotBugsReport> reports(
      Action<NamedDomainObjectContainer<? extends SpotBugsReport>> configureAction) {
    configureAction.execute(reports);
    return reports;
  }

  @NonNull
  @Internal
  public Set<File> getPluginJar() {
    return getProject()
        .getConfigurations()
        .getByName(SpotBugsPlugin.PLUGINS_CONFIG_NAME)
        .getFiles();
  }

  @NonNull
  @Internal
  public FileCollection getSpotbugsClasspath() {
    Configuration config = getProject().getConfigurations().getByName(SpotBugsPlugin.CONFIG_NAME);
    Configuration spotbugsSlf4j =
        getProject().getConfigurations().getByName(SpotBugsPlugin.SLF4J_CONFIG_NAME);

    return getProject().files(config, spotbugsSlf4j);
  }

  @Nullable
  @Optional
  @Nested
  public SpotBugsReport getFirstEnabledReport() {
    java.util.Optional<SpotBugsReport> report =
        reports.stream().filter(SpotBugsReport::isEnabled).findFirst();
    return report.orElse(null);
  }

  @NonNull
  @Optional
  @Nested
  public Set<SpotBugsReport> getEnabledReports() {
    return reports.stream().filter(SpotBugsReport::isEnabled).collect(Collectors.toSet());
  }

  @Internal
  public String getBaseName() {
    String prunedName = getName().replaceFirst("spotbugs", "");
    if (prunedName.isEmpty()) {
      prunedName = getName();
    }
    return new StringBuilder()
        .append(Character.toLowerCase(prunedName.charAt(0)))
        .append(prunedName.substring(1))
        .toString();
  }
}
