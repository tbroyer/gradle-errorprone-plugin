package net.ltgt.gradle.errorprone.javacplugin;

import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.stream.Stream;
import org.gradle.api.tasks.Nested;
import org.gradle.process.CommandLineArgumentProvider;

class ErrorProneCompilerArgumentProvider implements CommandLineArgumentProvider {
  private final ErrorProneConvention errorproneConvention;

  public ErrorProneCompilerArgumentProvider(ErrorProneConvention errorproneConvention) {
    this.errorproneConvention = errorproneConvention;
  }

  @Nested
  public ErrorProneConvention getErrorproneConvention() {
    return errorproneConvention;
  }

  @Override
  public Iterable<String> asArguments() {
    return Collections.singleton(
        Stream.concat(
                Stream.of("-Xplugin:ErrorProne"), errorproneConvention.getErrorproneArgs().stream())
            .collect(joining(" ")));
  }
}
