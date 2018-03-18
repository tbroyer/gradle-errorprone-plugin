package net.ltgt.gradle.errorprone.javacplugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.util.GradleVersion;

public class ErrorProneJavacPluginPlugin implements Plugin<Project> {
  public static final String PLUGIN_ID = "net.ltgt.errorprone-javacplugin";

  public static final String CONFIGURATION_NAME = "errorprone";

  public static final String DEFAULT_DEPENDENCY =
      "com.google.errorprone:error_prone_core:latest.release";

  @Override
  public void apply(Project project) {
    if (GradleVersion.current().compareTo(GradleVersion.version("4.6")) < 0) {
      throw new UnsupportedOperationException(PLUGIN_ID + " requires at least Gradle 4.6");
    }

    Configuration errorproneConfiguration =
        project
            .getConfigurations()
            .create(
                CONFIGURATION_NAME,
                configuration -> {
                  configuration.setVisible(false);
                  configuration.defaultDependencies(
                      dependencies ->
                          dependencies.add(project.getDependencies().create(DEFAULT_DEPENDENCY)));
                });

    project
        .getPlugins()
        .withType(
            JavaBasePlugin.class,
            plugin -> {
              JavaPluginConvention javaConvention =
                  project.getConvention().getPlugin(JavaPluginConvention.class);
              javaConvention
                  .getSourceSets()
                  .all(
                      sourceSet -> {
                        project
                            .getConfigurations()
                            .getByName(sourceSet.getAnnotationProcessorConfigurationName())
                            .extendsFrom(errorproneConfiguration);
                        ErrorProneJavacPlugin.apply(
                            ((JavaCompile)
                                    project
                                        .getTasks()
                                        .getByName(sourceSet.getCompileJavaTaskName()))
                                .getOptions());
                      });
            });
  }
}
