import Dependencies.*
import sbt.*
import sbt.Keys.*

ThisBuild / organization := "com.evolution.hashira"

ThisBuild / crossScalaVersions := Seq(scala213Version, scala33Version)
ThisBuild / scalaVersion := crossScalaVersions.value.head

addCommandAlias("fmt", "+all scalafmtRepo")
addCommandAlias("build", "+all scalafmtCheckRepo Compile/doc test")

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
  ),
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "root",
    publish / skip := true,
  )
  .aggregate(
    core,
    cassandraDriver3,
    cassandraDriver4,
  )

lazy val core = project
  .in(file("core"))
  .settings(
    commonSettings,
    name := "hashira-core",
  )

lazy val cassandraDriver3 = project
  .in(file("cassandra-driver3"))
  .settings(
    commonSettings,
    name := "hashira-cassandra-driver3",
  )
  .dependsOn(
    core,
  )
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.cassandraDriverV3,
    ),
  )

lazy val cassandraDriver4 = project
  .in(file("cassandra-driver4"))
  .settings(
    commonSettings,
    name := "hashira-cassandra-driver4",
  )
  .dependsOn(
    core,
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
