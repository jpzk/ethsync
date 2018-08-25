lazy val commonSettings = Seq(
  organization := "com.reebo",
  version := "1.0.0",
  scalaVersion := "2.12.6",
  description := "ethsync")

scalacOptions += "-Ypartial-unification"

lazy val core = (project in file("core")).
  settings(commonSettings: _*).
  settings(
    assemblyJarName in assembly := "ethsync.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "io.netty.versions.properties", xs@_*) => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  ).
  settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % "0.9.3",
      "io.monix" %% "monix" % "3.0.0-RC1",
    ) ++ testDeps ++ sttp ++ log
  )

lazy val testDeps = Seq(
  "org.scalatest" %% "scalatest" % "3.0.4" % Test,
  "org.scalamock" %% "scalamock" % "4.1.0" % Test
)

lazy val sttp = Seq(
  "com.softwaremill.sttp" %% "core" % "1.3.0",
  "com.softwaremill.sttp" %% "async-http-client-backend" % "1.3.0",
  "com.softwaremill.sttp" %% "circe" % "1.3.0",
  "com.softwaremill.sttp" %% "async-http-client-backend-monix" % "1.3.0",
  "com.softwaremill.sttp" %% "monix" % "1.3.0",
)

lazy val log = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "net.logstash.logback" % "logstash-logback-encoder" % "4.11",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
)

