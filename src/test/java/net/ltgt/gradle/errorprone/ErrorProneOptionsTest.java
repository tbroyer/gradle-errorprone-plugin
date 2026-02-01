package net.ltgt.gradle.errorprone;

import static com.google.common.truth.Truth.assertThat;
import static net.ltgt.gradle.errorprone.ErrorPronePlugin.TEST_SOURCE_SET_NAME_REGEX;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.errorprone.ErrorProneOptions.Severity;
import com.google.errorprone.InvalidCommandLineOptionException;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import kotlin.Pair;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.component.external.model.TestFixturesSupport;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ErrorProneOptionsTest {
  ObjectFactory objects;
  ProviderFactory providers;

  @BeforeAll
  public void setup(@TempDir File projectDir) {
    var project = ProjectBuilder.builder().withProjectDir(projectDir).build();
    objects = project.getObjects();
    providers = project.getProviders();
  }

  @Test
  public void detectsTestSourceSets() {
    assertThat(SourceSet.MAIN_SOURCE_SET_NAME).doesNotMatch(TEST_SOURCE_SET_NAME_REGEX);
    assertThat("testing").doesNotMatch(TEST_SOURCE_SET_NAME_REGEX);
    assertThat("fooTester").doesNotMatch(TEST_SOURCE_SET_NAME_REGEX);

    assertThat(SourceSet.TEST_SOURCE_SET_NAME).matches(TEST_SOURCE_SET_NAME_REGEX);
    assertThat(TestFixturesSupport.TEST_FIXTURE_SOURCESET_NAME).matches(TEST_SOURCE_SET_NAME_REGEX);
    // E.g. from nebula.integtest plugin
    assertThat("integTest").matches(TEST_SOURCE_SET_NAME_REGEX);
    assertThat("fooTestBar").matches(TEST_SOURCE_SET_NAME_REGEX);
  }

  @Test
  public void generatesCorrectErrorProneOptions() {
    doTestOptions(options -> options.getDisableAllChecks().set(true));
    doTestOptions(options -> options.getDisableAllWarnings().set(true));
    doTestOptions(options -> options.getAllErrorsAsWarnings().set(true));
    doTestOptions(options -> options.getAllSuggestionsAsWarnings().set(true));
    doTestOptions(options -> options.getAllDisabledChecksAsWarnings().set(true));
    doTestOptions(options -> options.getDisableWarningsInGeneratedCode().set(true));
    doTestOptions(options -> options.getIgnoreUnknownCheckNames().set(true));
    doTestOptions(options -> options.getIgnoreSuppressionAnnotations().set(true));
    doTestOptions(options -> options.getCompilingTestOnlyCode().set(true));
    doTestOptions(options -> options.getExcludedPaths().set(".*/build/generated/.*"));
    doTestOptions(options -> options.enable("ArrayEquals"));
    doTestOptions(options -> options.disable("ArrayEquals"));
    doTestOptions(options -> options.warn("ArrayEquals"));
    doTestOptions(options -> options.error("ArrayEquals"));
    doTestOptions(options -> options.check(new Pair<>("ArrayEquals", CheckSeverity.OFF)));
    doTestOptions(options -> options.check("ArrayEquals", CheckSeverity.WARN));
    doTestOptions(options -> options.getChecks().put("ArrayEquals", CheckSeverity.ERROR));
    doTestOptions(options -> options.getChecks().set(Map.of("ArrayEquals", CheckSeverity.ERROR)));
    doTestOptions(options -> options.option("Foo"));
    doTestOptions(options -> options.option("Foo", "Bar"));
    doTestOptions(options -> options.getCheckOptions().put("Foo", "Bar"));
    doTestOptions(options -> options.getCheckOptions().set(Map.of("Foo", "Bar")));

    doTestOptions(
        options -> {
          options.getDisableAllChecks().set(true);
          options.getDisableAllWarnings().set(true);
          options.getAllErrorsAsWarnings().set(true);
          options.getAllSuggestionsAsWarnings().set(true);
          options.getAllDisabledChecksAsWarnings().set(true);
          options.getDisableWarningsInGeneratedCode().set(true);
          options.getIgnoreUnknownCheckNames().set(true);
          options.getIgnoreSuppressionAnnotations().set(true);
          options.getCompilingTestOnlyCode().set(true);
          options.getExcludedPaths().set(".*/build/generated/.*");
          options.enable("ArrayEquals");
          options.check("ArrayEquals", CheckSeverity.ERROR);
          options.option("Foo");
          options.option("NullAway:AnnotatedPackages", "net.ltgt.gradle.errorprone");
        });
  }

  private void doTestOptions(Consumer<ErrorProneOptions> configure) {
    var options = objects.newInstance(ErrorProneOptions.class);
    configure.accept(options);
    var parsedOptions = parseOptions(options);
    assertOptionsEqual(options, parsedOptions);
  }

  @Test
  public void correctlyPassesFreeArguments() {
    // We cannot test arguments that are not yet covered, and couldn't check patching options (due
    // to class visibility issue), so we're testing equivalence between free-form args vs. args
    // generated by flags (that we already test above on their own)
    doTestOptions(
        options -> options.getErrorproneArgs().add("-XepDisableAllChecks"),
        reference -> reference.getDisableAllChecks().set(true));

    doTestOptions(
        options -> options.getErrorproneArgs().set(List.of("-XepDisableAllChecks", "-Xep:BetaApi")),
        reference -> {
          reference.getDisableAllChecks().set(true);
          reference.enable("BetaApi");
        });

    doTestOptions(
        options ->
            options
                .getErrorproneArgumentProviders()
                .add(
                    () ->
                        List.of(
                            "-Xep:NullAway:ERROR",
                            "-XepOpt:NullAway:AnnotatedPackages=net.ltgt.gradle.errorprone")),
        reference -> {
          reference.check("NullAway", CheckSeverity.ERROR);
          reference.option("NullAway:AnnotatedPackages", "net.ltgt.gradle.errorprone");
        });
  }

  @Test
  public void correctlyAllowsLazyConfiguration() {
    doTestOptions(
        options ->
            options.check(
                "NullAway",
                options
                    .getCompilingTestOnlyCode()
                    .map(b -> b ? CheckSeverity.WARN : CheckSeverity.ERROR)),
        reference -> reference.error("NullAway"));

    doTestOptions(
        options -> {
          options.check(
              "NullAway",
              options
                  .getCompilingTestOnlyCode()
                  .map(b -> b ? CheckSeverity.WARN : CheckSeverity.ERROR));
          options.getCompilingTestOnlyCode().set(providers.provider(() -> true));
        },
        reference -> {
          reference.getCompilingTestOnlyCode().set(true);
          reference.warn("NullAway");
        });

    doTestOptions(
        options -> {
          var annotatedPackages = objects.property(String.class);
          options.option("NullAway:AnnotatedPackages", annotatedPackages);
          annotatedPackages.set(providers.provider(() -> "net.ltgt.gradle.errorprone"));
        },
        reference -> reference.option("NullAway:AnnotatedPackages", "net.ltgt.gradle.errorprone"));
  }

  private void doTestOptions(
      Consumer<ErrorProneOptions> configure, Consumer<ErrorProneOptions> reference) {
    var referenceOptions = objects.newInstance(ErrorProneOptions.class);
    reference.accept(referenceOptions);
    var options = objects.newInstance(ErrorProneOptions.class);
    configure.accept(options);
    var parsedOptions = parseOptions(options);
    assertOptionsEqual(referenceOptions, parsedOptions);
  }

  @Test
  public void rejectsSpaces() {
    doTestSpaces(
        "-XepExcludedPaths:",
        options ->
            options
                .getExcludedPaths()
                .set("/home/user/My Projects/project-name/build/generated sources/.*"));
    doTestSpaces("-Xep:", options -> options.enable("Foo Bar"));
    doTestSpaces("-XepOpt:", options -> options.option("Foo Bar"));
    doTestSpaces("-XepOpt:", options -> options.option("Foo", "Bar Baz"));
    doTestSpaces(
        "-Xep:Foo -Xep:Bar", options -> options.getErrorproneArgs().add("-Xep:Foo -Xep:Bar"));
    doTestSpaces(
        "-Xep:Foo -Xep:Bar",
        options ->
            options.getErrorproneArgumentProviders().add(() -> List.of("-Xep:Foo -Xep:Bar")));
  }

  private void doTestSpaces(String argPrefix, Consumer<ErrorProneOptions> configure) {
    var exception =
        assertThrows(
            InvalidUserDataException.class,
            () -> {
              var options = objects.newInstance(ErrorProneOptions.class);
              configure.accept(options);
              options.toString();
            });
    assertThat(exception)
        .hasMessageThat()
        .startsWith("Error Prone options cannot contain white space: \"%s".formatted(argPrefix));
  }

  @Test
  public void rejectsColonInCheckName() {
    var exception =
        assertThrows(
            InvalidUserDataException.class,
            () -> {
              var options = objects.newInstance(ErrorProneOptions.class);
              options.enable("ArrayEquals:OFF");
              options.toString();
            });
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Error Prone check name cannot contain a colon (\":\"): \"ArrayEquals:OFF\".");

    // Won't analyze free-form arguments, but those should be caught (later) by argument parsing
    // This test asserts that we're not being too restrictive, and only try to fail early.
    assertThrows(
        InvalidCommandLineOptionException.class,
        () -> {
          var options = objects.newInstance(ErrorProneOptions.class);
          options.getIgnoreUnknownCheckNames().set(true);
          options.getErrorproneArgs().add("-Xep:Foo:Bar");
          parseOptions(options);
        });
  }

  private com.google.errorprone.ErrorProneOptions parseOptions(ErrorProneOptions options) {
    return com.google.errorprone.ErrorProneOptions.processArgs(splitArgs(options.toString()));
  }

  // This is how JavaC "parses" the -Xplugin: values: https://git.io/vx8yI
  private String[] splitArgs(String args) {
    return args.split("\\s+");
  }

  private void assertOptionsEqual(
      ErrorProneOptions options, com.google.errorprone.ErrorProneOptions parsedOptions) {
    assertThat(parsedOptions.isDisableAllChecks()).isEqualTo(options.getDisableAllChecks().get());
    assertThat(parsedOptions.isDisableAllWarnings())
        .isEqualTo(options.getDisableAllWarnings().get());
    assertThat(parsedOptions.isDropErrorsToWarnings())
        .isEqualTo(options.getAllErrorsAsWarnings().get());
    assertThat(parsedOptions.isSuggestionsAsWarnings())
        .isEqualTo(options.getAllSuggestionsAsWarnings().get());
    assertThat(parsedOptions.isEnableAllChecksAsWarnings())
        .isEqualTo(options.getAllDisabledChecksAsWarnings().get());
    assertThat(parsedOptions.disableWarningsInGeneratedCode())
        .isEqualTo(options.getDisableWarningsInGeneratedCode().get());
    assertThat(parsedOptions.ignoreUnknownChecks())
        .isEqualTo(options.getIgnoreUnknownCheckNames().get());
    assertThat(parsedOptions.isIgnoreSuppressionAnnotations())
        .isEqualTo(options.getIgnoreSuppressionAnnotations().get());
    assertThat(parsedOptions.isTestOnlyTarget())
        .isEqualTo(options.getCompilingTestOnlyCode().get());
    assertThat(
            Optional.ofNullable(parsedOptions.getExcludedPattern())
                .map(Pattern::pattern)
                .orElse(null))
        .isEqualTo(options.getExcludedPaths().getOrNull());
    assertThat(parsedOptions.getSeverityMap())
        .containsExactlyEntriesIn(
            options.getChecks().get().entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), toSeverity(entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    assertThat(parsedOptions.getFlags().getFlagsMap())
        .containsExactlyEntriesIn(options.getCheckOptions().get());
    assertThat(parsedOptions.getRemainingArgs()).isEmpty();
  }

  private Severity toSeverity(CheckSeverity checkSeverity) {
    return switch (checkSeverity) {
      case DEFAULT -> Severity.DEFAULT;
      case OFF -> Severity.OFF;
      case WARN -> Severity.WARN;
      case ERROR -> Severity.ERROR;
    };
  }
}
