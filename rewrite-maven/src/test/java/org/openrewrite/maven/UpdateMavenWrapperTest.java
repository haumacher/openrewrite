/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.maven;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.ipc.http.HttpUrlConnectionSender;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.maven.utilities.MavenWrapper;
import org.openrewrite.remote.Remote;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;
import org.openrewrite.text.PlainText;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openrewrite.maven.utilities.MavenWrapper.*;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.*;

class UpdateMavenWrapperTest implements RewriteTest {
    private final UnaryOperator<@Nullable String> notEmpty = actual -> {
        assertThat(actual).isNotNull();
        return actual + "\n";
    };

    // Maven wrapper script text for 3.1.1
    private static final String MVNW_TEXT = StringUtils.readFully(UpdateMavenWrapperTest.class.getResourceAsStream("/mvnw"));
    private static final String MVNW_CMD_TEXT = StringUtils.readFully(UpdateMavenWrapperTest.class.getResourceAsStream("/mvnw.cmd"));

    private final SourceSpecs mvnw = text("", spec -> spec.path(WRAPPER_SCRIPT_LOCATION).after(notEmpty));
    private final SourceSpecs mvnwCmd = text("", spec -> spec.path(WRAPPER_BATCH_LOCATION).after(notEmpty));
    private final SourceSpecs mvnWrapperJarQuark = other("", spec -> spec.path(WRAPPER_JAR_LOCATION).after(notEmpty));

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpdateMavenWrapper("3.1.x", null, "3.8.x", null, null));
    }

    @Test
    @DocumentExample("Add a new Maven wrapper")
    void addMavenWrapper() {
        rewriteRun(
          spec -> spec.afterRecipe(run -> {
              assertThat(run.getChangeset().getAllResults()).hasSize(4);

              var mvnw = result(run, PlainText.class, "mvnw");
              assertThat(mvnw.getSourcePath()).isEqualTo(WRAPPER_SCRIPT_LOCATION);
              assertThat(mvnw.getText()).isEqualTo(MVNW_TEXT);
              assertThat(mvnw.getFileAttributes()).isNotNull();
              assertThat(mvnw.getFileAttributes().isReadable()).isTrue();
              assertThat(mvnw.getFileAttributes().isWritable()).isTrue();

              var mvnwCmd = result(run, PlainText.class, "mvnw.cmd");
              assertThat(mvnwCmd.getSourcePath()).isEqualTo(WRAPPER_BATCH_LOCATION);
              assertThat(mvnwCmd.getText()).isEqualTo(MVNW_CMD_TEXT);

              var mavenWrapperJar = result(run, Remote.class, "maven-wrapper.jar");
              assertThat(mavenWrapperJar.getSourcePath()).isEqualTo(WRAPPER_JAR_LOCATION);
              assertThat(mavenWrapperJar.getUri()).isEqualTo(URI.create("https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.1/maven-wrapper-3.1.1.jar"));
              assertThat(isValidWrapperJar(mavenWrapperJar)).as("Wrapper jar is not valid").isTrue();
          }),
          properties(
            null,
            withLicenseHeader("""
              distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.zip
              distributionSha256Sum=2e181515ce8ae14b7a904c40bb4794831f5fd1d9641107a13b916af15af4001a
              wrapperUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.1/maven-wrapper-3.1.1.jar
              wrapperSha256Sum=ff7f21f2ef81723377e3d42d06661c4e3af60cf4bdfb7579ac8f22051399942d
              """),
            spec -> spec.path(".mvn/wrapper/maven-wrapper.properties")
          )
        );
    }

    @Test
    @DocumentExample("Update existing Maven wrapper")
    void updateWrapper() {
        rewriteRun(
          spec -> spec.allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.8.0")))
            .afterRecipe(run -> {
                var mvnw = result(run, PlainText.class, "mvnw");
                assertThat(mvnw.getSourcePath()).isEqualTo(WRAPPER_SCRIPT_LOCATION);
                assertThat(mvnw.getText()).isEqualTo(MVNW_TEXT);
                assertThat(mvnw.getFileAttributes()).isNotNull();
                assertThat(mvnw.getFileAttributes().isReadable()).isTrue();
                assertThat(mvnw.getFileAttributes().isWritable()).isTrue();

                var mvnwCmd = result(run, PlainText.class, "mvnw.cmd");
                assertThat(mvnwCmd.getSourcePath()).isEqualTo(WRAPPER_BATCH_LOCATION);
                assertThat(mvnwCmd.getText()).isEqualTo(MVNW_CMD_TEXT);

                var mavenWrapperJar = result(run, Remote.class, "maven-wrapper.jar");
                assertThat(mavenWrapperJar.getSourcePath()).isEqualTo(WRAPPER_JAR_LOCATION);
                assertThat(mavenWrapperJar.getUri()).isEqualTo(URI.create("https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.1/maven-wrapper-3.1.1.jar"));
                assertThat(isValidWrapperJar(mavenWrapperJar)).as("Wrapper jar is not valid").isTrue();
            }),
          properties(
            withLicenseHeader("""
              distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.0/apache-maven-3.8.0-bin.zip
              wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.0/maven-wrapper-3.1.0.jar
              """),
            withLicenseHeader("""
              distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.zip
              wrapperUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.1/maven-wrapper-3.1.1.jar
              distributionSha256Sum=2e181515ce8ae14b7a904c40bb4794831f5fd1d9641107a13b916af15af4001a
              wrapperSha256Sum=ff7f21f2ef81723377e3d42d06661c4e3af60cf4bdfb7579ac8f22051399942d
              """),
            spec -> spec.path(".mvn/wrapper/maven-wrapper.properties")
              .afterRecipe(mavenWrapperProperties ->
                assertThat(mavenWrapperProperties.getMarkers().findFirst(BuildTool.class)).hasValueSatisfying(buildTool -> {
                    assertThat(buildTool.getType()).isEqualTo(BuildTool.Type.Maven);
                    assertThat(buildTool.getVersion()).isEqualTo("3.8.8");
                }))
          ),
          mvnw,
          mvnwCmd,
          mvnWrapperJarQuark
        );
    }

    @Test
    void updateVersionUsingSourceDistribution() {
        rewriteRun(
          spec -> spec.recipe(new UpdateMavenWrapper("3.1.x", "source", "3.8.x", null, null))
            .allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.8.0")))
            .afterRecipe(run -> {
                var mvnw = result(run, PlainText.class, "mvnw");
                assertThat(mvnw.getText()).isNotBlank();

                var mvnwCmd = result(run, PlainText.class, "mvnw.cmd");
                assertThat(mvnwCmd.getText()).isNotBlank();

                assertThatThrownBy(() -> result(run, SourceFile.class, "maven-wrapper.jar"))
                  .isInstanceOf(NoSuchElementException.class)
                  .hasMessage("No value present");

                var mvnwDownloaderJava = result(run, Remote.class, "MavenWrapperDownloader.java");
                assertThat(mvnwDownloaderJava.getSourcePath()).isEqualTo(WRAPPER_DOWNLOADER_LOCATION);
                assertThat(mvnwDownloaderJava.getUri()).isEqualTo(URI.create("https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.1.1/maven-wrapper-distribution-3.1.1-source.zip"));
            }),
          properties(
            withLicenseHeader("""
              distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.0/apache-maven-3.8.0-bin.zip
              wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.0/maven-wrapper-3.1.0.jar
              """),
            withLicenseHeader("""
              distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.zip
              wrapperUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.1/maven-wrapper-3.1.1.jar
              distributionSha256Sum=2e181515ce8ae14b7a904c40bb4794831f5fd1d9641107a13b916af15af4001a
              wrapperSha256Sum=ff7f21f2ef81723377e3d42d06661c4e3af60cf4bdfb7579ac8f22051399942d
              """),
            spec -> spec.path(".mvn/wrapper/maven-wrapper.properties")
          ),
          mvnw,
          mvnwCmd,
          other(
            "",
            null,
            spec -> spec.path(".mvn/wrapper/maven-wrapper.jar")
          )
        );
    }

    @Test
    void updateVersionUsingScriptDistribution() {
        rewriteRun(
          spec -> spec.recipe(new UpdateMavenWrapper("3.1.x", "script", "3.8.x", null, null))
            .allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.8.0")))
            .afterRecipe(run -> {
                var mvnw = result(run, PlainText.class, "mvnw");
                assertThat(mvnw.getText()).isNotBlank();

                var mvnwCmd = result(run, PlainText.class, "mvnw.cmd");
                assertThat(mvnwCmd.getText()).isNotBlank();

                assertThatThrownBy(() -> result(run, SourceFile.class, "maven-wrapper.jar"))
                  .isInstanceOf(NoSuchElementException.class)
                  .hasMessage("No value present");

                assertThatThrownBy(() -> result(run, Remote.class, "MavenWrapperDownloader.java"))
                  .isInstanceOf(NoSuchElementException.class)
                  .hasMessage("No value present");
            }),
          properties(
            withLicenseHeader("""
              distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.0/apache-maven-3.8.0-bin.zip
              wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.0/maven-wrapper-3.1.0.jar
              """),
            withLicenseHeader("""
              distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.zip
              wrapperUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.1/maven-wrapper-3.1.1.jar
              distributionSha256Sum=2e181515ce8ae14b7a904c40bb4794831f5fd1d9641107a13b916af15af4001a
              wrapperSha256Sum=ff7f21f2ef81723377e3d42d06661c4e3af60cf4bdfb7579ac8f22051399942d
              """),
            spec -> spec.path(".mvn/wrapper/maven-wrapper.properties")
          ),
          mvnw,
          mvnwCmd,
          other(
            "",
            null,
            spec -> spec.path(".mvn/wrapper/maven-wrapper.jar")
          )
        );
    }

    @Test
    void updateVersionUsingOnlyScriptDistribution() {
        rewriteRun(
          spec -> spec.recipe(new UpdateMavenWrapper(null, "only-script", "3.8.x", null, null))
            .allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.8.0")))
            .afterRecipe(run -> {
                var mvnw = result(run, PlainText.class, "mvnw");
                assertThat(mvnw.getText()).isNotBlank();

                var mvnwCmd = result(run, PlainText.class, "mvnw.cmd");
                assertThat(mvnwCmd.getText()).isNotBlank();

                assertThatThrownBy(() -> result(run, Remote.class, "maven-wrapper.jar"))
                  .isInstanceOf(NoSuchElementException.class)
                  .hasMessage("No value present");

                assertThatThrownBy(() -> result(run, Remote.class, "MavenWrapperDownloader.java"))
                  .isInstanceOf(NoSuchElementException.class)
                  .hasMessage("No value present");
            }),
          properties(
            withLicenseHeader("""
              distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.0/apache-maven-3.8.0-bin.zip
              """),
            withLicenseHeader("""
              distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.zip
              distributionSha256Sum=2e181515ce8ae14b7a904c40bb4794831f5fd1d9641107a13b916af15af4001a
              """),
            spec -> spec.path(".mvn/wrapper/maven-wrapper.properties")
          ),
          mvnw,
          mvnwCmd,
          other(
            "",
            null,
            spec -> spec.path(".mvn/wrapper/maven-wrapper.jar")
          )
        );
    }

    @Test
    void dontAddMissingWrapper() {
        rewriteRun(
          spec -> spec.recipe(new UpdateMavenWrapper("3.1.x", null, "3.8.x", null, Boolean.FALSE))
            .allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.8.0")))
            .afterRecipe(run -> assertThat(run.getChangeset().getAllResults()).isEmpty())
        );
    }

    @Test
    void updateMultipleWrappers() {
        rewriteRun(
          spec -> spec.recipe(new UpdateMavenWrapper("3.1.x", null, "3.8.x", null, Boolean.FALSE))
            .allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.8.0"))),
          dir("example1",
            properties(
              withLicenseHeader("""
                distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.0/apache-maven-3.8.0-bin.zip
                wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.0/maven-wrapper-3.1.0.jar
                """),
              withLicenseHeader("""
                distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.zip
                wrapperUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.1/maven-wrapper-3.1.1.jar
                distributionSha256Sum=2e181515ce8ae14b7a904c40bb4794831f5fd1d9641107a13b916af15af4001a
                wrapperSha256Sum=ff7f21f2ef81723377e3d42d06661c4e3af60cf4bdfb7579ac8f22051399942d
                """),
              spec -> spec.path(".mvn/wrapper/maven-wrapper.properties")
            ),
            mvnw,
            mvnwCmd,
            mvnWrapperJarQuark
          ),
          dir("example2",
            properties(
              withLicenseHeader("""
                distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.0/apache-maven-3.8.0-bin.zip
                wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.0/maven-wrapper-3.1.0.jar
                """),
              withLicenseHeader("""
                distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.zip
                wrapperUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.1/maven-wrapper-3.1.1.jar
                distributionSha256Sum=2e181515ce8ae14b7a904c40bb4794831f5fd1d9641107a13b916af15af4001a
                wrapperSha256Sum=ff7f21f2ef81723377e3d42d06661c4e3af60cf4bdfb7579ac8f22051399942d
                """),
              spec -> spec.path(".mvn/wrapper/maven-wrapper.properties")
            ),
            mvnw,
            mvnwCmd,
            mvnWrapperJarQuark
          )
        );
    }

    @Test
    void doNotDowngrade() {
        rewriteRun(
          spec -> spec.allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.9.0"))),
          properties(
            withLicenseHeader("""
              distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.0/apache-maven-3.9.0-bin.zip
              wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
              """),
            spec -> spec.path(".mvn/wrapper/maven-wrapper.properties")
          ),
          text("", spec -> spec.path("mvnw")),
          text("", spec -> spec.path("mvnw.cmd")),
          other("", spec -> spec.path(".mvn/wrapper/maven-wrapper.jar"))
        );
    }

    @Test
    void allowUpdatingDistributionTypeWhenSameVersion() {
        rewriteRun(
          spec -> spec.recipe(new UpdateMavenWrapper("3.1.x", "script", "3.8.x", null, null))
            .allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.8.8")))
            .afterRecipe(run -> {
                var mvnw = result(run, PlainText.class, "mvnw");
                assertThat(mvnw.getText()).isNotBlank();

                var mvnwCmd = result(run, PlainText.class, "mvnw.cmd");
                assertThat(mvnwCmd.getText()).isNotBlank();

                assertThatThrownBy(() -> result(run, SourceFile.class, "maven-wrapper.jar"))
                  .isInstanceOf(NoSuchElementException.class)
                  .hasMessage("No value present");

                assertThatThrownBy(() -> result(run, SourceFile.class, "MavenWrapperDownloader.java"))
                  .isInstanceOf(NoSuchElementException.class)
                  .hasMessage("No value present");
            }),
          properties(
            withLicenseHeader("""
              distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.zip
              wrapperUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.1/maven-wrapper-3.1.1.jar
              distributionSha256Sum=2e181515ce8ae14b7a904c40bb4794831f5fd1d9641107a13b916af15af4001a
              wrapperSha256Sum=ff7f21f2ef81723377e3d42d06661c4e3af60cf4bdfb7579ac8f22051399942d
              """),
            spec -> spec.path(".mvn/wrapper/maven-wrapper.properties")
          ),
          mvnw,
          mvnwCmd,
          other(
            "",
            null,
            spec -> spec.path(".mvn/wrapper/maven-wrapper.jar")
          ),
          other(
            "",
            null,
            spec -> spec.path(".mvn/wrapper/MavenWrapperDownloader.java")
          )
        );
    }

    @Test
    void defaultsToLatestRelease() {
        rewriteRun(
          spec -> spec.recipe(new UpdateMavenWrapper(null, null, null, null, null))
            .allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.8.0")))
            .afterRecipe(run -> {
                var mvnw = result(run, PlainText.class, "mvnw");
                assertThat(mvnw.getSourcePath()).isEqualTo(WRAPPER_SCRIPT_LOCATION);
                assertThat(mvnw.getText()).isNotBlank();
                assertThat(mvnw.getFileAttributes()).isNotNull();
                assertThat(mvnw.getFileAttributes().isReadable()).isTrue();
                assertThat(mvnw.getFileAttributes().isWritable()).isTrue();

                var mvnwCmd = result(run, PlainText.class, "mvnw.cmd");
                assertThat(mvnwCmd.getSourcePath()).isEqualTo(WRAPPER_BATCH_LOCATION);
                assertThat(mvnwCmd.getText()).isNotBlank();

                var mavenWrapperJar = result(run, Remote.class, "maven-wrapper.jar");
                assertThat(mavenWrapperJar.getSourcePath()).isEqualTo(WRAPPER_JAR_LOCATION);
                Matcher wrapperVersionMatcher = Pattern.compile("maven-wrapper-(.*?)\\.jar").matcher(mavenWrapperJar.getUri().toString());
                assertThat(wrapperVersionMatcher.find()).isTrue();
                String wrapperVersion = wrapperVersionMatcher.group(1);
                assertThat(wrapperVersion).isNotEqualTo("3.1.1");
                assertThat(mavenWrapperJar.getUri()).isEqualTo(URI.create("https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/" + wrapperVersion + "/maven-wrapper-" + wrapperVersion + ".jar"));
                assertThat(isValidWrapperJar(mavenWrapperJar)).as("Wrapper jar is not valid").isTrue();
            }),
          properties(
            withLicenseHeader("""
              distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.0/apache-maven-3.8.0-bin.zip
              wrapperUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.1/maven-wrapper-3.1.1.jar
              distributionSha256Sum=2e181515ce8ae14b7a904c40bb4794831f5fd1d9641107a13b916af15af4001a
              wrapperSha256Sum=ff7f21f2ef81723377e3d42d06661c4e3af60cf4bdfb7579ac8f22051399942d
              """),
            spec -> spec.path(".mvn/wrapper/maven-wrapper.properties")
              .after(after -> {
                  Matcher distributionVersionMatcher = Pattern.compile("apache-maven-(.*?)-bin\\.zip").matcher(after);
                  assertThat(distributionVersionMatcher.find()).isTrue();
                  String mavenDistributionVersion = distributionVersionMatcher.group(1);
                  assertThat(mavenDistributionVersion).isNotEqualTo("3.8.0");

                  Matcher distributionChecksumMatcher = Pattern.compile("distributionSha256Sum=(.*)").matcher(after);
                  assertThat(distributionChecksumMatcher.find()).isTrue();
                  String distributionChecksum = distributionChecksumMatcher.group(1);
                  assertThat(distributionChecksum).isNotBlank();

                  Matcher wrapperVersionMatcher = Pattern.compile("maven-wrapper-(.*?)\\.jar").matcher(after);
                  assertThat(wrapperVersionMatcher.find()).isTrue();
                  String wrapperVersion = wrapperVersionMatcher.group(1);
                  assertThat(wrapperVersion).isNotEqualTo("3.1.1");

                  Matcher wrapperChecksumMatcher = Pattern.compile("wrapperSha256Sum=(.*)").matcher(after);
                  assertThat(wrapperChecksumMatcher.find()).isTrue();
                  String wrapperChecksum = wrapperChecksumMatcher.group(1);
                  assertThat(wrapperChecksum).isNotBlank();

                  return withLicenseHeader("""
                    distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%s/apache-maven-%s-bin.zip
                    wrapperUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/%s/maven-wrapper-%s.jar
                    distributionSha256Sum=%s
                    wrapperSha256Sum=%s
                    """.formatted(mavenDistributionVersion, mavenDistributionVersion, wrapperVersion, wrapperVersion, distributionChecksum, wrapperChecksum));
              })
          ),
          mvnw,
          mvnwCmd,
          mvnWrapperJarQuark
        );
    }

    private String withLicenseHeader(@Language("properties") String original) {
        return MavenWrapper.ASF_LICENSE_HEADER + original;
    }

    private <S extends SourceFile> S result(RecipeRun run, Class<S> clazz, String endsWith) {
        return run.getChangeset().getAllResults().stream()
          .map(Result::getAfter)
          .filter(Objects::nonNull)
          .filter(r -> r.getSourcePath().endsWith(endsWith))
          .findFirst()
          .map(clazz::cast)
          .orElseThrow();
    }

    private boolean isValidWrapperJar(Remote gradleWrapperJar) {
        try {
            Path testWrapperJar = Files.createTempFile("maven-wrapper", "jar");
            ExecutionContext ctx = new InMemoryExecutionContext();
            HttpSenderExecutionContextView.view(ctx).setHttpSender(new HttpUrlConnectionSender(Duration.ofSeconds(5), Duration.ofSeconds(5)));
            try (InputStream is = gradleWrapperJar.getInputStream(ctx)) {
                Files.copy(is, testWrapperJar, StandardCopyOption.REPLACE_EXISTING);
                try (FileSystem fs = FileSystems.newFileSystem(testWrapperJar)) {
                    return Files.exists(fs.getPath("org/apache/maven/wrapper/MavenWrapperMain.class"));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
