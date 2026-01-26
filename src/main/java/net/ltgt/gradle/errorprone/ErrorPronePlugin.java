package net.ltgt.gradle.errorprone;

import static java.util.Collections.emptyList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Named;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Plugin} that configures {@link JavaCompile} tasks to use the <a
 * href="https://errorprone.info/">Error Prone compiler</a>.
 */
public abstract class ErrorPronePlugin implements Plugin<Project> {
  public static final String PLUGIN_ID = "net.ltgt.errorprone";
  public static final String CONFIGURATION_NAME = "errorprone";

  static final String EXTENSION_NAME = "errorprone";

  static final String TOO_OLD_TOOLCHAIN_ERROR_MESSAGE =
      "Must not enable ErrorProne when compiling with JDK < 11";

  static final String TEST_SOURCE_SET_NAME_REGEX = "^(t|.*T)est(\\p{javaUpperCase}.*)?$";

  static final List<String> JVM_ARGS_STRONG_ENCAPSULATION =
      Collections.unmodifiableList(
          Arrays.asList(
              "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
              "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
              "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
              "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
              "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
              "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
              "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
              "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
              "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
              "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"));

  @Override
  public void apply(Project project) {
    if (GradleVersion.current().compareTo(GradleVersion.version("7.1")) < 0) {
      throw new UnsupportedOperationException("net.ltgt.errorprone requires at least Gradle 7.1");
    }

    Provider<Configuration> errorproneConfiguration = registerConfiguration(project);
    configureJavaCompileTasks(project);
    project
        .getPlugins()
        .withType(
            JavaBasePlugin.class,
            unused -> configureForJavaPlugin(project, errorproneConfiguration));
  }

  @SuppressWarnings("deprecation") // Configuration.setVisible for backwards-compatibility
  private Provider<Configuration> registerConfiguration(Project project) {
    return project
        .getConfigurations()
        .register(
            CONFIGURATION_NAME,
            configuration -> {
              configuration.setDescription(
                  "Error Prone dependencies, will be extended by all source sets' annotationProcessor configurations");
              configuration.setVisible(false);
              configuration.setCanBeConsumed(false);
              configuration.setCanBeResolved(false);

              Map<String, String> exclude = new HashMap<>(2);
              exclude.put("group", "com.google.errorprone");
              exclude.put("module", "javac");
              configuration.exclude(exclude);
            });
  }

  private void configureJavaCompileTasks(Project project) {
    project
        .getTasks()
        .withType(JavaCompile.class)
        .configureEach(this::configureJavaCompileTaskDefaults);
  }

  private void configureJavaCompileTaskDefaults(JavaCompile task) {
    ErrorProneOptions errorproneOptions =
        ((ExtensionAware) task.getOptions())
            .getExtensions()
            .create(EXTENSION_NAME, ErrorProneOptions.class);

    task.getOptions()
        .getCompilerArgumentProviders()
        .add(new ErrorProneCompilerArgumentProvider(errorproneOptions));

    ErrorProneJvmArgumentProvider jvmArgumentProvider =
        new ErrorProneJvmArgumentProvider(task, errorproneOptions);
    task.getOptions().getForkOptions().getJvmArgumentProviders().add(jvmArgumentProvider);
    task.doFirst(
        "Configure forking for errorprone",
        new ConfigureForkingTaskAction(errorproneOptions, jvmArgumentProvider, task.getOptions()));
  }

  private void configureForJavaPlugin(
      Project project, Provider<Configuration> errorproneConfiguration) {
    project
        .getExtensions()
        .getByType(SourceSetContainer.class)
        .configureEach(
            sourceSet -> configureForSourceSet(project, errorproneConfiguration, sourceSet));
  }

  private void configureForSourceSet(
      Project project, Provider<Configuration> errorproneConfiguration, SourceSet sourceSet) {
    project
        .getConfigurations()
        .named(
            sourceSet.getAnnotationProcessorConfigurationName(),
            configuration -> configuration.extendsFrom(errorproneConfiguration.get()));
    project
        .getTasks()
        .named(
            sourceSet.getCompileJavaTaskName(),
            JavaCompile.class,
            task -> configureTaskForSourceSet(sourceSet, task));
  }

  private void configureTaskForSourceSet(SourceSet sourceSet, JavaCompile task) {
    ((ExtensionAware) task.getOptions())
        .getExtensions()
        .configure(
            ErrorProneOptions.class,
            errorproneOptions ->
                configureSourceSetCompileJavaTask(sourceSet, task, errorproneOptions));
  }

  private void configureSourceSetCompileJavaTask(
      SourceSet sourceSet, JavaCompile task, ErrorProneOptions errorproneOptions) {
    errorproneOptions
        .getEnabled()
        .convention(
            task.getJavaCompiler()
                .map(
                    javaCompile ->
                        javaCompile.getMetadata().getLanguageVersion().canCompileOrRun(11))
                .orElse(true));
    errorproneOptions
        .getCompilingTestOnlyCode()
        .convention(sourceSet.getName().matches(TEST_SOURCE_SET_NAME_REGEX));
  }

  private static class ErrorProneCompilerArgumentProvider
      implements CommandLineArgumentProvider, Named {
    private final ErrorProneOptions errorproneOptions;

    ErrorProneCompilerArgumentProvider(ErrorProneOptions errorproneOptions) {
      this.errorproneOptions = errorproneOptions;
    }

    @Internal
    @Override
    public String getName() {
      return EXTENSION_NAME;
    }

    @SuppressWarnings("unused")
    @Nested
    @Optional
    @Nullable ErrorProneOptions getErrorproneOptions() {
      return errorproneOptions.getEnabled().getOrElse(false) ? errorproneOptions : null;
    }

    @Override
    public Iterable<String> asArguments() {
      if (!errorproneOptions.getEnabled().getOrElse(false)) {
        return emptyList();
      }
      return Arrays.asList(
          "-Xplugin:ErrorProne " + errorproneOptions,
          "-XDcompilePolicy=simple",
          "--should-stop=ifError=FLOW",
          // Error Prone 2.46.0 requires it for JDK 21 (and it helps NullAway too even with previous
          // Error Prone versions)
          // It's only useful for JDK 21, but safe to pass to any version.
          // See https://github.com/google/error-prone/issues/5426
          "-XDaddTypeAnnotationsToSymbol=true");
    }
  }

  private static class ErrorProneJvmArgumentProvider implements CommandLineArgumentProvider, Named {
    private final JavaCompile task;
    private final ErrorProneOptions errorproneOptions;

    ErrorProneJvmArgumentProvider(JavaCompile task, ErrorProneOptions errorproneOptions) {
      this.task = task;
      this.errorproneOptions = errorproneOptions;
    }

    @Internal
    @Override
    public String getName() {
      return EXTENSION_NAME;
    }

    @Input
    @Optional
    @Nullable JavaVersion getCompilerVersion() {
      JavaCompiler javaCompiler = task.getJavaCompiler().getOrNull();
      if (javaCompiler == null) {
        return isCommandLine(task.getOptions()) ? null : JavaVersion.current();
      }
      return JavaVersion.toVersion(javaCompiler.getMetadata().getLanguageVersion().asInt());
    }

    private boolean isCommandLine(CompileOptions options) {
      return options.isFork()
          && (options.getForkOptions().getJavaHome() != null
              || options.getForkOptions().getExecutable() != null);
    }

    @Override
    public Iterable<String> asArguments() {
      if (!errorproneOptions.getEnabled().getOrElse(false)) {
        return emptyList();
      }
      JavaVersion compilerVersion = getCompilerVersion();
      if (compilerVersion == null || !compilerVersion.isCompatibleWith(JavaVersion.VERSION_11)) {
        return emptyList();
      }
      return JVM_ARGS_STRONG_ENCAPSULATION;
    }
  }

  private static class ConfigureForkingTaskAction implements Action<Task> {
    private final ErrorProneOptions errorproneOptions;
    private final ErrorProneJvmArgumentProvider jvmArgumentProvider;
    private final CompileOptions options;

    ConfigureForkingTaskAction(
        ErrorProneOptions errorproneOptions,
        ErrorProneJvmArgumentProvider jvmArgumentProvider,
        CompileOptions options) {
      this.errorproneOptions = errorproneOptions;
      this.jvmArgumentProvider = jvmArgumentProvider;
      this.options = options;
    }

    @Override
    public void execute(Task unused) {
      if (!errorproneOptions.getEnabled().getOrElse(false)) {
        return;
      }
      JavaVersion compilerVersion = jvmArgumentProvider.getCompilerVersion();
      if (compilerVersion == null) {
        return;
      }
      if (!compilerVersion.isCompatibleWith(JavaVersion.VERSION_11)) {
        throw new UnsupportedOperationException(TOO_OLD_TOOLCHAIN_ERROR_MESSAGE);
      }
      if (!options.isFork()
          && compilerVersion.equals(JavaVersion.current())
          && StrongEncapsulationHelperJava.CURRENT_JVM_NEEDS_FORKING) {
        options.setFork(true);
      }
    }
  }

  private static class StrongEncapsulationHelperJava {
    static final boolean CURRENT_JVM_NEEDS_FORKING = currentJvmNeedsForking();

    private static boolean currentJvmNeedsForking() {
      if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16)) {
        return false;
      }
      try {
        // Only needed because we compile with --release 8
        // XXX: remove reflection when bumping the minimum required JDK, or using a Multi-Release
        // JAR
        Method getUnnamedModule = ClassLoader.class.getMethod("getUnnamedModule");
        Method getModule = Class.class.getMethod("getModule");
        Method getPackageName = Class.class.getMethod("getPackageName");
        Class<?> moduleClass = getModule.getReturnType();
        Method isExported = moduleClass.getMethod("isExported", String.class, moduleClass);
        Method isOpen = moduleClass.getMethod("isOpen", String.class, moduleClass);

        Object unnamedModule =
            getUnnamedModule.invoke(StrongEncapsulationHelperJava.class.getClassLoader());
        for (String className :
            new String[] {
              "com.sun.tools.javac.api.BasicJavacTask",
              "com.sun.tools.javac.api.JavacTrees",
              "com.sun.tools.javac.file.JavacFileManager",
              "com.sun.tools.javac.main.JavaCompiler",
              "com.sun.tools.javac.model.JavacElements",
              "com.sun.tools.javac.parser.JavacParser",
              "com.sun.tools.javac.processing.JavacProcessingEnvironment",
              "com.sun.tools.javac.tree.JCTree",
              "com.sun.tools.javac.util.JCDiagnostic",
            }) {
          Class<?> klass = Class.forName(className);
          if (Boolean.FALSE.equals(
              isExported.invoke(
                  getModule.invoke(klass), getPackageName.invoke(klass), unnamedModule))) {
            return true;
          }
        }
        for (String className :
            new String[] {
              "com.sun.tools.javac.code.Symbol", //
              "com.sun.tools.javac.comp.Enter",
            }) {
          Class<?> klass = Class.forName(className);
          if (Boolean.FALSE.equals(
              isOpen.invoke(
                  getModule.invoke(klass), getPackageName.invoke(klass), unnamedModule))) {
            return true;
          }
        }
      } catch (ClassNotFoundException ignored) {
        return true;
      } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
        throw new LinkageError("Shouldn't happen", e);
      }
      return false;
    }
  }
}
