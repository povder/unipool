import Settings._

import scala.Console._

shellPrompt.in(ThisBuild) := (state => s"${CYAN}project:$GREEN${Project.extract(state).currentRef.project}$RESET> ")

lazy val commonSettings = Vector(
  organization := "io.github.povder.unipool",
  organizationName := "Krzysztof Pado",
  scalaVersion := "2.12.6",
  crossScalaVersions := Vector(scalaVersion.value, "2.11.12"),

  licenses := Vector(
    "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")
  ),
  startYear := Some(Copyright.startYear),

  homepage := Some(url("https://github.com/povder/unipool")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/povder/unipool"),
      "scm:git@github.com:povder/unipool.git"
    )
  ),

  buildInfoKeys := Vector(version, scalaVersion, git.gitHeadCommit, BuildInfoKey.action("buildTime") {
    java.time.Instant.now()
  }),

  scalastyleFailOnError := true
) ++ compilationConf ++ scaladocConf ++ developersConf ++ publishConf ++ testConf

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false
  )
  .aggregate(`unipool-scala`, `unipool-java`)

lazy val `unipool-scala` = (project in file("unipool-scala"))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings: _*)
  .settings(
    name := "unipool-scala",
    libraryDependencies ++= Vector(
      Library.stm,
      Library.scalaLogging,
      Library.scalatest % Test,
      Library.scalamock % Test
    ),
    scalacOptions in(Compile, doc) ++= Vector(
      "-doc-title", "unipool - universal non-blocking resource pool"
    ),
    buildInfoPackage := "io.github.povder.unipool"
  )

lazy val `unipool-java` = (project in file("unipool-java"))
  .settings(commonSettings: _*)
  .settings(
    name := "unipool-java",
    libraryDependencies ++= Vector(
      Library.immutables % Provided,
      Library.java8Compat
    ),
  ).dependsOn(`unipool-scala`)
