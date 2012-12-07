import sbt._
import Keys._

object BuketanBuild extends Build {
	val commonSettings = Defaults.defaultSettings ++ Seq(
		scalaVersion := "2.10.0-RC2"
	)

	lazy val root = Project("root", file(".")) aggregate (core, cmdline)

	lazy val core = Project(
		"core", file("core"), settings = commonSettings
	) settings (
		libraryDependencies ++= Seq(
			"org.apache.xmlgraphics" % "batik-transcoder" % "1.7",
			"org.apache.xmlgraphics" % "batik-codec" % "1.7",
			"com.jhlabs" % "filters" % "2.0.235",
			"org.slf4j" % "slf4j-api" % "1.6.6",
			"org.scalatest" % "scalatest_2.10.0-RC2" % "1.8" % "test"
		)
	)

	lazy val cmdline = Project(
		"cmdline", file("cmdline"), settings = commonSettings
	) dependsOn (core) settings (
		libraryDependencies ++= Seq(
			"ch.qos.logback" % "logback-classic" % "0.9.30",
			"net.elehack.argparse4s" %% "argparse4s" % "0.3-SNAPSHOT"
		)
	)
}