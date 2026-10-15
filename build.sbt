import Dependencies.*
import sbt.*
import sbt.Keys.*

ThisBuild / organization := "com.evolution.hashira"

ThisBuild / startYear := Some(2026)
ThisBuild / homepage := Some(url("https://github.com/evolution-gaming/hashira"))
ThisBuild / licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT")))

ThisBuild / organizationName := "Evolution"
ThisBuild / organizationHomepage := Some(url("https://evolution.com"))

// Maven Central requires <developers> in published pom.xml files
ThisBuild / developers := List(
  Developer(
    id = "migesok",
    name = "Mikhail Sokolov",
    email = "mikhail.g.sokolov@gmail.com",
    url = url("https://github.com/migesok"),
  ),
)

ThisBuild / scmInfo := Some(ScmInfo(
  browseUrl = url("https://github.com/evolution-gaming/hashira"),
  connection = "git@github.com:evolution-gaming/hashira.git",
))

// if changing binary Scala versions here, change in the ci.yml action as well
ThisBuild / crossScalaVersions := Seq(scala213Version, scala33Version)
ThisBuild / scalaVersion := crossScalaVersions.value.head

// DO NOT CHANGE THIS SETTING UNLESS YOU FULLY UNDERSTAND THE CONSEQUENCES!
//
// WARNING: BinaryCompatible is used instead of BinaryAndSourceCompatible because BinaryAndSourceCompatible fails
// on new methods added to objects, which doesn't really break neither source, nor binary compatibility.
// So the source compatibility should be guaranteed manually.
ThisBuild / versionPolicyIntention := Compatibility.BinaryCompatible

addCommandAlias("fmt", "+all scalafmtRepo")
addCommandAlias("build", "+all scalafmtCheckRepo versionPolicyCheck Compile/doc test")

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-release:17",
    "-deprecation",
  ),
  scalacOptions ++= crossSettings(
    scalaVersion = scalaVersion.value,
    // Good compiler options for Scala 2.13 are coming from com.evolution:sbt-scalac-opts-plugin:0.1.0,
    // but its support for Scala 3 is limited, especially what concerns linting options.
    //
    // If Scala 3 is made the primary target, good linting scalac options for it should be added first.
    if3 = Seq(
      "-Ykind-projector:underscores",

      // disable new brace-less syntax:
      // https://alexn.org/blog/2022/10/24/scala-3-optional-braces/
      "-no-indent",

      // improve error messages:
      "-explain",
      "-explain-types",
    ),
    if2 = Seq(
      "-Xsource:3",
    ),
  ),
  Compile / doc / scalacOptions ++= Seq("-groups", "-implicits", "-no-link-warnings"),
  Compile / doc / scalacOptions -= "-Xfatal-warnings",
  libraryDependencies ++= Seq(
    Slf4j.api,
    scalaLogging,
    ScalaTest.all % Test,
    ScalaTest.scalaCheck % Test,
    Slf4j.simple % Test,
    commonsText % Test,
    commonsIo % Test,
  ),
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "hashira",
    publish / skip := true,
  )
  .aggregate(
    core,
    cassandraDriverSupportTck,
    cassandraDriver3,
    cassandraDriver4,
  )

lazy val core = project
  .configure(asPublishedModule(
    name = "core",
    description = "core types and utilities",
  ))
  // -- to autogenerate config data classes like MigratorParams
  .enablePlugins(ConfigClassGenPlugin)
  .settings(
    configClassGenPackage := "com.evolution.hashira",
  )
  // --
  .settings(
    libraryDependencies ++= Seq(
      classgraph,
      typesafeConfig,
    ),
  )

lazy val cassandraDriverSupportTck = project
  .configure(asPublishedModule(
    name = "cassandra-driver-support-tck",
    description = "Cassandra driver support TCK",
  ))
  .dependsOn(
    core,
  )
  .settings(
    libraryDependencies ++= Seq(
      ScalaTest.all,
      testcontainersCassandra,
    ),
  )

lazy val cassandraDriver3 = project
  .configure(asPublishedModule(
    name = "cassandra-driver3",
    description = "Cassandra driver v3 support",
  ))
  .settings(
    Test / fork := true, // fork the JVM on test due to using testcontainers here
  )
  .dependsOn(
    core,
    cassandraDriverSupportTck % Test,
  )
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.cassandraDriverV3,
    ),
  )

lazy val cassandraDriver4 = project
  .configure(asPublishedModule(
    name = "cassandra-driver4",
    description = "Cassandra driver v4 support",
  ))
  .settings(
    Test / fork := true, // fork the JVM on test due to using testcontainers here
  )
  .dependsOn(
    core,
    cassandraDriverSupportTck % Test,
  )
  .settings(
    libraryDependencies ++= Seq(
      CassandraDriverV4.core,
    ),
  )

def crossSettings[T](scalaVersion: String, if3: T, if2: T): T = {
  scalaVersion match {
    case version if version.startsWith("3") => if3
    case _ => if2
  }
}

def asPublishedModule(
  name: String,
  description: String,
)(
  p: Project,
): Project = {
  p.in(file(name))
    .settings(
      commonSettings,
      Keys.name := name,
      Keys.description := s"Hashira Cassandra migration tool - $description",
    )
}
