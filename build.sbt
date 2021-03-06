
lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(SbtTwirl)
  .settings(
    inThisBuild(List(
      organization := "com.poca",
      scalaVersion := "2.13.1"
    )),
    name := "poca-2020"
  )

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "org.scalamock" %% "scalamock" % "4.4.0" % Test,
  "com.typesafe.akka" %% "akka-stream" % "2.6.8",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.8",
  "com.typesafe.akka" %% "akka-http" % "10.2.0",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.2.0",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.8",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.slick" %% "slick" % "3.3.3",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3",
  "org.postgresql" % "postgresql" % "42.2.16",
  "com.typesafe" % "config" % "1.4.0",
  "com.softwaremill.akka-http-session" %% "core" % "0.5.11",
  "com.softwaremill.akka-http-session" %% "jwt"  % "0.5.11",
  "javax.mail" % "mail" % "1.4.7"
)

dockerExposedPorts ++= Seq(8080)
dockerUpdateLatest := true
//dockerRepository = "[repository.host[:repository.port]]"
//dockerUsername = ""

mainClass in Compile := Some("poca.AppHttpServer")
