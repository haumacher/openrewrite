/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.gradle;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.withToolingApi;

class UpgradeDependencyVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi())
          .recipe(new UpgradeDependencyVersion("com.google.guava", "guava", "30.x", "-jre"));
    }

    @DocumentExample
    @Test
    void guava() {
        rewriteRun(
          buildGradle(
            """
              plugins {
                id 'java-library'
              }
              
              repositories {
                mavenCentral()
              }
              
              dependencies {
                compileOnly 'com.google.guava:guava:29.0-jre'
                runtimeOnly ('com.google.guava:guava:29.0-jre') {
                    force = true
                }
              }
              """,
            """
              plugins {
                id 'java-library'
              }
              
              repositories {
                mavenCentral()
              }
              
              dependencies {
                compileOnly 'com.google.guava:guava:30.1.1-jre'
                runtimeOnly ('com.google.guava:guava:30.1.1-jre') {
                    force = true
                }
              }
              """,
            spec -> spec.afterRecipe(after -> {
                Optional<GradleProject> maybeGp = after.getMarkers().findFirst(GradleProject.class);
                assertThat(maybeGp).isPresent();
                GradleProject gp = maybeGp.get();
                GradleDependencyConfiguration compileClasspath = gp.getConfiguration("compileClasspath");
                assertThat(compileClasspath).isNotNull();
                assertThat(
                  compileClasspath.getRequested().stream()
                    .filter(dep -> "com.google.guava".equals(dep.getGroupId()) && "guava".equals(dep.getArtifactId()) && "30.1.1-jre".equals(dep.getVersion()))
                    .findAny())
                  .as("GradleProject requested dependencies should have been updated with the new version of guava")
                  .isPresent();
                assertThat(
                  compileClasspath.getResolved().stream()
                    .filter(dep -> "com.google.guava".equals(dep.getGroupId()) && "guava".equals(dep.getArtifactId()) && "30.1.1-jre".equals(dep.getVersion()))
                    .findAny())
                  .as("GradleProject requested dependencies should have been updated with the new version of guava")
                  .isPresent();
            })
          )
        );
    }

    @Test
    void updateVersionInVariable() {
        rewriteRun(
          buildGradle(
            """
              plugins {
                id 'java-library'
              }
              
              def guavaVersion = '29.0-jre'
              def otherVersion = "latest.release"
              repositories {
                mavenCentral()
              }
              
              dependencies {
                implementation ("com.google.guava:guava:$guavaVersion") {
                    force = true
                }
                implementation "com.fasterxml.jackson.core:jackson-databind:$otherVersion"
              }
              """,
            """
              plugins {
                id 'java-library'
              }
              
              def guavaVersion = '30.1.1-jre'
              def otherVersion = "latest.release"
              repositories {
                mavenCentral()
              }
              
              dependencies {
                implementation ("com.google.guava:guava:$guavaVersion") {
                    force = true
                }
                implementation "com.fasterxml.jackson.core:jackson-databind:$otherVersion"
              }
              """
          )
        );
    }

    @Test
    void mapNotationVariable() {
        rewriteRun(
          buildGradle(
            """
              plugins {
                id 'java-library'
              }
              
              def guavaVersion = '29.0-jre'
              repositories {
                mavenCentral()
              }
              
              dependencies {
                implementation group: "com.google.guava", name: "guava", version: guavaVersion
              }
              """,
            """
              plugins {
                id 'java-library'
              }
              
              def guavaVersion = '30.1.1-jre'
              repositories {
                mavenCentral()
              }
              
              dependencies {
                implementation group: "com.google.guava", name: "guava", version: guavaVersion
              }
              """
          )
        );
    }

    @Test
    void mapNotationLiteral() {
        rewriteRun(
          buildGradle(
            """
              plugins {
                id 'java-library'
              }
              
              repositories {
                mavenCentral()
              }
              
              dependencies {
                implementation (group: "com.google.guava", name: "guava", version: '29.0-jre') {
                  force = true
                }
              }
              """,
            """
              plugins {
                id 'java-library'
              }
              
              repositories {
                mavenCentral()
              }
              
              dependencies {
                implementation (group: "com.google.guava", name: "guava", version: '30.1.1-jre') {
                  force = true
                }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/3227")
    @Test
    void worksWithPlatform() {
        rewriteRun(
          buildGradle(
            """
              plugins {
                id 'java-library'
              }
              
              repositories {
                mavenCentral()
              }
              
              dependencies {
                implementation platform("com.google.guava:guava:29.0-jre")
              }
              """,
            """
              plugins {
                id 'java-library'
              }
              
              repositories {
                mavenCentral()
              }
              
              dependencies {
                implementation platform("com.google.guava:guava:30.1.1-jre")
              }
              """
          )
        );
    }

    @Test
    void upgradesVariablesDefinedInExtraProperties() {
        rewriteRun(
          buildGradle(
            """
              buildscript {
                  ext {
                      guavaVersion = "29.0-jre"
                  }
                  repositories {
                      mavenCentral()
                  }
                  dependencies {
                      classpath("com.google.guava:guava:${guavaVersion}")
                  }
              }

              plugins {
                  id "java"
              }

              repositories {
                  mavenCentral()
              }

              ext {
                  guavaVersion2 = "29.0-jre"
              }

              dependencies {
                  implementation "com.google.guava:guava:${guavaVersion2}"
              }
              """,
            """
              buildscript {
                  ext {
                      guavaVersion = "30.1.1-jre"
                  }
                  repositories {
                      mavenCentral()
                  }
                  dependencies {
                      classpath("com.google.guava:guava:${guavaVersion}")
                  }
              }

              plugins {
                  id "java"
              }

              repositories {
                  mavenCentral()
              }

              ext {
                  guavaVersion2 = "30.1.1-jre"
              }

              dependencies {
                  implementation "com.google.guava:guava:${guavaVersion2}"
              }
              """
          )
        );
    }

    @Test
    void matchesGlobs() {
        rewriteRun(
          spec -> spec.recipe(new UpgradeDependencyVersion("com.google.*", "gua*", "30.x", "-jre")),
          buildGradle(
            """
              plugins {
                  id "java"
              }

              repositories {
                  mavenCentral()
              }

              ext {
                  guavaVersion = "29.0-jre"
              }

              def guavaVersion2 = "29.0-jre"
              dependencies {
                  implementation("com.google.guava:guava:29.0-jre")
                  implementation group: "com.google.guava", name: "guava", version: "29.0-jre"
                  implementation "com.google.guava:guava:${guavaVersion}"
                  implementation group: "com.google.guava", name: "guava", version: guavaVersion2
              }
              """,
            """
              plugins {
                  id "java"
              }

              repositories {
                  mavenCentral()
              }

              ext {
                  guavaVersion = "30.1.1-jre"
              }

              def guavaVersion2 = "30.1.1-jre"
              dependencies {
                  implementation("com.google.guava:guava:30.1.1-jre")
                  implementation group: "com.google.guava", name: "guava", version: "30.1.1-jre"
                  implementation "com.google.guava:guava:${guavaVersion}"
                  implementation group: "com.google.guava", name: "guava", version: guavaVersion2
              }
              """
          )
        );
    }

    @Test
    void defaultsToLatestRelease() {
        rewriteRun(
          spec -> spec.recipe(new UpgradeDependencyVersion("com.google.guava", "guava", null, "-jre")),
          buildGradle(
            """
              plugins {
                id 'java-library'
              }
              
              repositories {
                mavenCentral()
              }
              
              dependencies {
                implementation 'com.google.guava:guava:29.0-jre'
              }
              """,
            spec -> spec.after(after -> {
                Matcher versionMatcher = Pattern.compile("implementation 'com\\.google\\.guava:guava:(.*?)'").matcher(after);
                assertThat(versionMatcher.find()).isTrue();
                String version = versionMatcher.group(1);
                assertThat(version).isNotEqualTo("29.0-jre");

                return """
                  plugins {
                    id 'java-library'
                  }
                  
                  repositories {
                    mavenCentral()
                  }
                  
                  dependencies {
                    implementation 'com.google.guava:guava:%s'
                  }
                  """.formatted(version);
            })
          )
        );
    }
}
