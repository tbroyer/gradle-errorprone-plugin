package net.ltgt.gradle.errorprone.javacplugin;

import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.tasks.compile.CompileOptions;

public class ErrorProneJavacPlugin {
  public static void apply(CompileOptions options) {
    ErrorProneOptions errorproneOptions =
        new DslObject(options)
            .getExtensions()
            .create(ErrorProneOptions.NAME, ErrorProneOptions.class);
    options
        .getCompilerArgumentProviders()
        .add(new ErrorProneCompilerArgumentProvider(errorproneOptions));
  }
}
