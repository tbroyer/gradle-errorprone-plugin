package net.ltgt.gradle.errorprone;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import kotlin.DeprecationLevel;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class ErrorProneOptions {

  public static final String NAME = ErrorPronePlugin.EXTENSION_NAME;

  private static final Predicate<String> IS_WHITE_SPACE =
      Pattern.compile("\\p{IsWhite_Space}").asPredicate();

  private final List<CommandLineArgumentProvider> errorproneArgumentProviders = new ArrayList<>();

  /**
   * Allows disabling Error Prone altogether for the task.
   *
   * <p>Error Prone will still be in the {@linkplain CompileOptions#getAnnotationProcessorPath()
   * annotation processor path}, but {@code -Xplugin:ErrorProne} won't be passed as a {@linkplain
   * CompileOptions#getCompilerArgs()} compiler argument}.
   *
   * <p>Defaults to {@code true} on {@link SourceSet#getCompileJavaTaskName() tasks for source
   * sets}, {@code false} otherwise.
   */
  @Input
  public abstract Property<Boolean> getEnabled();

  /**
   * Disable all Error Prone checks; maps to {@code -XepDisableAllChecks}.
   *
   * <p>This will be the first argument, so checks can then be re-enabled on a case-by-case basis.
   */
  @Input
  public abstract Property<Boolean> getDisableAllChecks();

  /**
   * Disables all Error Prone warnings; maps to {@code -XepDisableAllWarnings}.
   *
   * <p>This will be among the first arguments, so checks can then be re-enabled on a case-by-case
   * basis.
   */
  @Input
  public abstract Property<Boolean> getDisableAllWarnings();

  /**
   * Turns all Error Prone errors into warnings; maps to {@code -XepAllErrorsAsWarnings}.
   *
   * <p>This will be among the first arguments, so checks can then be promoted back to error on a
   * case-by-case basis.
   */
  @Input
  public abstract Property<Boolean> getAllErrorsAsWarnings();

  /**
   * Turn all Error Prone suggestions into warnings; maps to {@code -XepAllSuggestionsAsWarnings}.
   *
   * <p>This will be among the first arguments, so checks can then be demoted back to suggestions on
   * a case-by-case basis.
   */
  @Input
  public abstract Property<Boolean> getAllSuggestionsAsWarnings();

  /**
   * Enables all Error Prone checks, checks that are disabled by default are enabled as warnings;
   * maps to {@code -XepAllDisabledChecksAsWarnings}.
   *
   * <p>This will be among the first arguments, so checks can then be disabled again on a
   * case-by-case basis.
   */
  @Input
  public abstract Property<Boolean> getAllDisabledChecksAsWarnings();

  /**
   * Disables warnings in classes annotated with {@code javax.annotation.processing.Generated} or
   * {@code @javax.annotation.Generated}; maps to {@code -XepDisableWarningsInGeneratedCode}.
   */
  @Input
  public abstract Property<Boolean> getDisableWarningsInGeneratedCode();

  /**
   * Tells Error Prone to ignore unknown check names in {@link #getChecks() checks}; maps to {@code
   * -XepIgnoreUknownCheckNames}.
   */
  @Input
  public abstract Property<Boolean> getIgnoreUnknownCheckNames();

  /**
   * Ignores suppression annotations, such as {@code @SuppressWarnings}; maps to {@code
   * -XepIgnoreSuppressionAnnotations}.
   */
  @Input
  public abstract Property<Boolean> getIgnoreSuppressionAnnotations();

  /**
   * Tells Error Prone that the compilation contains only test code; maps to {@code
   * -XepCompilingTestOnlyCode}.
   *
   * <p>Defaults to {@code true} for a source set inferred as a test source set, {@code false}
   * otherwise.
   */
  @Input
  public abstract Property<Boolean> getCompilingTestOnlyCode();

  /**
   * A regular expression pattern (as a string) of file paths to exclude from Error Prone checking;
   * maps to {@code -XepExcludedPaths}.
   */
  @Input
  @Optional
  public abstract Property<String> getExcludedPaths();

  /**
   * A map of check name to {@link CheckSeverity}, to configure which checks are enabled or
   * disabled, and their severity.
   *
   * <p>Maps each entry to {@code -Xep:<key>:<value>}, or {@code -Xep:<key>} when the value is
   * {@link CheckSeverity#DEFAULT}.
   *
   * @see #check(String, CheckSeverity)
   * @see #check(String, Provider)
   * @see #enable(String...)
   * @see #disable(String...)
   * @see #error(String...)
   * @see #warn(String...)
   */
  @Input
  public abstract MapProperty<String, CheckSeverity> getChecks();

  /**
   * A map of <a href="https://errorprone.info/docs/flags#pass-additional-info-to-bugcheckers">check
   * options</a> to their value.
   *
   * <p>Use an explicit {@code "true"} value for a boolean option.
   *
   * <p>Maps each entry to {@code -XepOpt:<key>=<value>}.
   *
   * @see #option(String)
   * @see #option(String, boolean)
   * @see #option(String, String)
   * @see #option(String, Provider)
   */
  @Input
  public abstract MapProperty<String, String> getCheckOptions();

  /** Additional arguments passed to Error Prone. */
  @Input
  public abstract ListProperty<String> getErrorproneArgs();

  /**
   * A list of {@link CommandLineArgumentProvider} for additional arguments passed to Error Prone.
   */
  @Nested
  public List<CommandLineArgumentProvider> getErrorproneArgumentProviders() {
    return errorproneArgumentProviders;
  }

  @SuppressWarnings("this-escape")
  public ErrorProneOptions() {
    getEnabled().convention(false);
    getDisableAllChecks().convention(false);
    getDisableAllWarnings().convention(false);
    getAllErrorsAsWarnings().convention(false);
    getAllSuggestionsAsWarnings().convention(false);
    getAllDisabledChecksAsWarnings().convention(false);
    getDisableWarningsInGeneratedCode().convention(false);
    getIgnoreUnknownCheckNames().convention(false);
    getIgnoreSuppressionAnnotations().convention(false);
    getCompilingTestOnlyCode().convention(false);
  }

  /**
   * Adds pairs of check name to severity.
   *
   * <p>Equivalent to calling {@code check(first, second)} for each pair.
   *
   * @see #getChecks()
   */
  @SafeVarargs
  public final void check(kotlin.Pair<String, CheckSeverity>... pairs) {
    for (kotlin.Pair<String, CheckSeverity> pair : pairs) {
      check(pair.getFirst(), pair.getSecond());
    }
  }

  /**
   * Adds a check with a given severity.
   *
   * <p>Equivalent to {@code checks.put(checkName, severity)}.
   *
   * @see #getChecks()
   */
  public void check(String checkName, CheckSeverity severity) {
    validateName(checkName);
    getChecks().put(checkName, severity);
  }

  /**
   * Adds a check with a given severity.
   *
   * <p>Equivalent to {@code checks.put(checkName, severity)}.
   *
   * @see #getChecks()
   */
  public void check(String checkName, Provider<CheckSeverity> severity) {
    validateName(checkName);
    getChecks().put(checkName, severity);
  }

  /**
   * Adds checks with their default severity.
   *
   * <p>Useful in combination with {@link #getDisableAllChecks() disableAllChecks} to selectively
   * re-enable checks.
   *
   * <p>Equivalent to calling {@code check(checkName, CheckSeverity.DEFAULT)} for each check name.
   *
   * @see #check(String, CheckSeverity)
   * @see #getChecks()
   */
  public void enable(String... checkNames) {
    set(checkNames, CheckSeverity.DEFAULT);
  }

  /**
   * Disable checks.
   *
   * <p>Equivalent to calling {@code check(checkName, CheckSeverity.OFF)} for each check name.
   *
   * @see #check(String, CheckSeverity)
   * @see #getChecks()
   */
  public void disable(String... checkNames) {
    set(checkNames, CheckSeverity.OFF);
  }

  /**
   * Adds checks with warning severity.
   *
   * <p>Equivalent to calling {@code check(checkName, CheckSeverity.WARN)} for each check name.
   *
   * @see #check(String, CheckSeverity)
   * @see #getChecks()
   */
  public void warn(String... checkNames) {
    set(checkNames, CheckSeverity.WARN);
  }

  /**
   * Adds checks with error severity.
   *
   * <p>Equivalent to calling {@code check(checkName, CheckSeverity.ERROR)} for each check name.
   *
   * @see #check(String, CheckSeverity)
   * @see #getChecks()
   */
  public void error(String... checkNames) {
    set(checkNames, CheckSeverity.ERROR);
  }

  private void set(String[] checkNames, CheckSeverity severity) {
    for (String checkName : checkNames) {
      check(checkName, severity);
    }
  }

  /**
   * Adds a check option with a {@code true} boolean value.
   *
   * <p>Equivalent to {@code checkOptions.put(name, "true")}.
   *
   * @see #getCheckOptions()
   */
  public void option(String name) {
    option(name, true);
  }

  /**
   * @deprecated Kept only for kotlin binary backward-compatibility.
   */
  @Deprecated
  @kotlin.Deprecated(
      message = "Kept only for kotlin binary backward-compatibility",
      level = DeprecationLevel.HIDDEN)
  @SuppressWarnings("InlineMeSuggester")
  public static void option$default(
      ErrorProneOptions options,
      String name,
      @SuppressWarnings("unused") boolean unusedBoolean,
      @SuppressWarnings("unused") int unusedInt,
      @SuppressWarnings("unused") Object unusedObject) {
    options.option(name);
  }

  /**
   * Adds a check option with a given boolean value.
   *
   * <p>Equivalent to {@code checkOptions.put(name, String.valueOf(value))}.
   *
   * @see #getCheckOptions()
   */
  public void option(String name, boolean value) {
    option(name, String.valueOf(value));
  }

  /**
   * Adds a check option with a given value.
   *
   * <p>Equivalent to {@code checkOptions.put(name, value)}.
   *
   * @see #getCheckOptions()
   */
  public void option(String name, String value) {
    getCheckOptions().put(name, value);
  }

  /**
   * Adds a check option with a given value.
   *
   * <p>Equivalent to {@code checkOptions.put(name, value)}.
   *
   * @see #getCheckOptions()
   */
  public void option(String name, Provider<String> value) {
    getCheckOptions().put(name, value);
  }

  @Override
  public String toString() {
    List<String> options = new ArrayList<>();
    maybeAddBooleanOption(options, "-XepDisableAllChecks", getDisableAllChecks());
    maybeAddBooleanOption(options, "-XepDisableAllWarnings", getDisableAllWarnings());
    maybeAddBooleanOption(options, "-XepAllErrorsAsWarnings", getAllErrorsAsWarnings());
    maybeAddBooleanOption(options, "-XepAllSuggestionsAsWarnings", getAllSuggestionsAsWarnings());
    maybeAddBooleanOption(
        options, "-XepAllDisabledChecksAsWarnings", getAllDisabledChecksAsWarnings());
    maybeAddBooleanOption(
        options, "-XepDisableWarningsInGeneratedCode", getDisableWarningsInGeneratedCode());
    maybeAddBooleanOption(options, "-XepIgnoreUnknownCheckNames", getIgnoreUnknownCheckNames());
    maybeAddBooleanOption(
        options, "-XepIgnoreSuppressionAnnotations", getIgnoreSuppressionAnnotations());
    maybeAddBooleanOption(options, "-XepCompilingTestOnlyCode", getCompilingTestOnlyCode());
    maybeAddStringOption(options, "-XepExcludedPaths", getExcludedPaths());

    getChecks()
        .get()
        .forEach(
            (name, severity) -> {
              validateName(name);
              options.add("-Xep:" + name + severityAsArg(severity));
            });
    getCheckOptions().get().forEach((name, value) -> options.add("-XepOpt:" + name + "=" + value));
    options.addAll(getErrorproneArgs().get());
    for (CommandLineArgumentProvider argumentProvider : getErrorproneArgumentProviders()) {
      argumentProvider.asArguments().forEach(options::add);
    }

    options.forEach(this::validate);
    return String.join(" ", options);
  }

  private void validateName(String checkName) {
    if (checkName.contains(":")) {
      throw new InvalidUserDataException(
          String.format(
              "Error Prone check name cannot contain a colon (\":\"): \"%s\".", checkName));
    }
  }

  private void maybeAddBooleanOption(List<String> options, String name, Provider<Boolean> value) {
    if (value.getOrElse(false)) {
      options.add(name);
    }
  }

  private void maybeAddStringOption(List<String> options, String name, Provider<String> value) {
    if (value.isPresent()) {
      options.add(name + ":" + value.get());
    }
  }

  private String severityAsArg(CheckSeverity severity) {
    return severity == CheckSeverity.DEFAULT ? "" : ":" + severity;
  }

  private void validate(String arg) {
    if (IS_WHITE_SPACE.test(arg)) {
      throw new InvalidUserDataException(
          String.format("Error Prone options cannot contain white space: \"%s\".", arg));
    }
  }
}
