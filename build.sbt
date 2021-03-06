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
      case PathList("com", "typesafe", "scalalogging", xs@_*) => MergeStrategy.first
      case PathList("org", "xerial", xs@_*) => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  ).
  enablePlugins(DockerPlugin).
  settings(buildOptions in docker := BuildOptions(cache = false)).
  settings(
    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from("anapsix/alpine-java")
        add(artifact, artifactTargetPath)
        copy(baseDirectory(_ / "src" / "main" / "resources" / "logback.xml").value, "/src/main/resources/")
        entryPoint("java", "-Dlogback.configurationFile=/config/logback.xml", "-cp",
          artifactTargetPath, "com.reebo.ethsync.core.utils.Main")
      }
    }
  ).
  settings(
    resolvers += "confluent.io" at "http://packages.confluent.io/maven/"
  ).
  settings(
    libraryDependencies ++= Seq(
      "io.dropwizard.metrics" % "metrics-core" % "4.0.3",
      "io.dropwizard" % "dropwizard-metrics-graphite" % "1.3.7",
      "io.circe" %% "circe-generic" % "0.9.3",
      "io.monix" %% "monix" % "3.0.0-RC1",
      "com.sksamuel.avro4s" %% "avro4s-core" % "1.9.0"
    ) ++ testDeps ++ sttp ++ log ++ kafka
  )

lazy val kafka = Seq(
  "io.monix" %% "monix-kafka-1x" % "1.0.0-RC1",
  "io.confluent" % "kafka-avro-serializer" % "5.0.0"
    excludeAll(
    ExclusionRule(organization = "org.slf4j"),
    ExclusionRule(organization = "log4j")
  )
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


