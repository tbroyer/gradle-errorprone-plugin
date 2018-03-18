package net.ltgt.gradle.errorprone.javacplugin;

import java.util.Collections;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;

class ErrorProneCompilerArgumentProvider implements CommandLineArgumentProvider {
  private final ErrorProneOptions errorproneOptions;

  public ErrorProneCompilerArgumentProvider(ErrorProneOptions errorproneOptions) {
    this.errorproneOptions = errorproneOptions;
  }

  @Nested
  @Optional
  public ErrorProneOptions getErrorproneOptions() {
    return errorproneOptions.isEnabled() ? errorproneOptions : null;
  }

  @Override
  public Iterable<String> asArguments() {
    return errorproneOptions.isEnabled()
        ? Collections.singletonList(errorproneOptions.toString())
        : Collections.emptyList();
  }
}
