package net.ltgt.gradle.errorprone.javacplugin;

import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.tasks.compile.CompileOptions;

public class ErrorProneJavacPlugin {
  public static void apply(CompileOptions options) {
    ErrorProneExtension errorproneExtension =
        new DslObject(options)
            .getExtensions()
            .create(ErrorProneExtension.NAME, ErrorProneExtension.class);
    options
        .getCompilerArgumentProviders()
        .add(new ErrorProneCompilerArgumentProvider(errorproneExtension));
  }
}
