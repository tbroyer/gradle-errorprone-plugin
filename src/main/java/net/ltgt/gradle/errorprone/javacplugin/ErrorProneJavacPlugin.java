package net.ltgt.gradle.errorprone.javacplugin;

import static net.ltgt.gradle.errorprone.javacplugin.ErrorProneJavacPluginPlugin.PLUGIN_ID;

import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.tasks.compile.CompileOptions;

public class ErrorProneJavacPlugin {

  public static void apply(CompileOptions options) {
    ErrorProneConvention errorproneConvention = new ErrorProneConvention();
    new DslObject(options).getConvention().getPlugins().put(PLUGIN_ID, errorproneConvention);
    options
        .getCompilerArgumentProviders()
        .add(new ErrorProneCompilerArgumentProvider(errorproneConvention));
  }
}
