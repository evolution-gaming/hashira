import sbt.*

object Dependencies {
  lazy val scala213Version = "2.13.18"
  lazy val scala33Version = "3.3.8" // Scala 3 LTS

  object Slf4j {
    private val v = "2.0.20"

    val api = "org.slf4j" % "slf4j-api" % v
    val simple = "org.slf4j" % "slf4j-simple" % v
  }

  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6"

  object ScalaTest {
    private val v = "3.2.20"

    val all = "org.scalatest" %% "scalatest" % v
    val scalaCheck = "org.scalatestplus" %% "scalacheck-1-19" % s"$v.0"
  }

  lazy val commonsText = "org.apache.commons" % "commons-text" % "1.15.0"

  lazy val commonsIo = "commons-io" % "commons-io" % "2.22.0"

  lazy val cassandraDriverV3 = "com.datastax.cassandra" % "cassandra-driver-core" % "3.11.5"

  object CassandraDriverV4 {
    private val v = "4.19.4"

    val core = "org.apache.cassandra" % "java-driver-core" % v
  }

  // https://github.com/classgraph/classgraph/releases#release-classgraph-5.0.0
  // migrate to 6.x when we move to JDK 25 LTS
  lazy val classgraph = "io.github.classgraph" % "classgraph" % "5.0.0"

  lazy val typesafeConfig = "com.typesafe" % "config" % "1.4.9"

  lazy val testcontainersCassandra = ("org.testcontainers" % "testcontainers-cassandra" % "2.0.5")
    // the driver is used only by the deprecated org.testcontainers.containers.CassandraContainer,
    // excluding it to keep driver 3 classes off the classpath of the tests for other drivers
    .exclude("com.datastax.cassandra", "cassandra-driver-core")

  // https://github.com/evolution-gaming/hashira/security/dependabot
  // Version overrides to fix dependabot alerts go here:
  object SecurityAlertOverrides {
    private val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % "2.21.8"
    private val guava = "com.google.guava" % "guava" % "33.7.2-jre"
    private val nettyHandler = "io.netty" % "netty-handler" % "4.1.139.Final"

    val dependencies: Seq[ModuleID] = Seq(
      jacksonDatabind,
      guava,
      nettyHandler,
    )
  }

}
