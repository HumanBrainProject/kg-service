
name := """kg-service"""
organization := "eu.humanbrainproject"

version := "1.0-SNAPSHOT"

lazy val common = (project in file("modules/common"))
  .enablePlugins(PlayScala)

lazy val data_import = (project in file("modules/data_import"))
  .enablePlugins(PlayScala)
  .dependsOn(common, nexus)

lazy val auth = (project in file("modules/authentication"))
  .enablePlugins(PlayScala)
  .dependsOn(common)

lazy val nexus = (project in file("modules/nexus"))
  .enablePlugins(PlayScala)
  .dependsOn(common, auth)

lazy val kg_service = (project in file("."))
  .enablePlugins(PlayScala)
  .aggregate(common, auth, data_import, nexus)
  .dependsOn(common, auth, data_import, nexus)

Common.settings

javaOptions in Universal ++= Seq("-Dpidfile.path=/dev/null")

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

scalacOptions += "-Ypartial-unification"
