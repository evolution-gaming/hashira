import sbt.*

object Dependencies {
  lazy val scala213Version = "2.13.18"
  lazy val scala33Version = "3.3.8" // Scala 3 LTS

  object Slf4j {
    private val v = "2.0.19"

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

  lazy val cassandraDriverV3 = "com.datastax.cassandra" % "cassandra-driver-core" % "3.11.5"

  object CassandraDriverV4 {
    private val v = "4.19.3"

    val core = "org.apache.cassandra" % "java-driver-core" % v
  }
}
