package net.ltgt.gradle.errorprone.javacplugin;

import java.util.Collections;
import org.gradle.api.tasks.Nested;
import org.gradle.process.CommandLineArgumentProvider;

class ErrorProneCompilerArgumentProvider implements CommandLineArgumentProvider {
  private final ErrorProneOptions errorproneOptions;

  public ErrorProneCompilerArgumentProvider(ErrorProneOptions errorproneOptions) {
    this.errorproneOptions = errorproneOptions;
  }

  @Nested
  public ErrorProneOptions getErrorproneOptions() {
    return errorproneOptions;
  }

  @Override
  public Iterable<String> asArguments() {
    return Collections.singleton(errorproneOptions.toString());
  }
}
