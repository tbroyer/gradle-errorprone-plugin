package net.ltgt.gradle.errorprone.javacplugin;

import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.stream.Stream;
import org.gradle.api.tasks.Nested;
import org.gradle.process.CommandLineArgumentProvider;

class ErrorProneCompilerArgumentProvider implements CommandLineArgumentProvider {
  private final ErrorProneExtension errorproneExtension;

  public ErrorProneCompilerArgumentProvider(ErrorProneExtension errorproneExtension) {
    this.errorproneExtension = errorproneExtension;
  }

  @Nested
  public ErrorProneExtension getErrorproneExtension() {
    return errorproneExtension;
  }

  @Override
  public Iterable<String> asArguments() {
    return Collections.singleton(
        Stream.concat(
                Stream.of("-Xplugin:ErrorProne"), errorproneExtension.getErrorproneArgs().stream())
            .collect(joining(" ")));
  }
}
