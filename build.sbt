name := "auction-algorithm"
organization := "com.evan"
version := "1.0.0"

scalaVersion := "3.3.4"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
  "-Werror"
)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",
  "org.scalameta" %% "munit" % "1.0.2" % Test
)

Compile / run / connectInput := true
Compile / mainClass := Some("com.evan.auctionalgorithm.Main")
