import sbt._
import Keys._

object BuketanBuild extends Build {
	val commonSettings = Defaults.defaultSettings ++ Seq(
		scalaVersion := "2.9.2",

		organization := "net.kriomant.buketan"
	)

	lazy val root = Project("root", file(".")) aggregate (core, cmdline)

	lazy val core = Project(
		"core", file("core"), settings = commonSettings
	) settings (
		libraryDependencies ++= Seq(
			"org.scala-lang" % "scala-compiler" % "2.9.2",
			"org.scala-lang" % "scala-library" % "2.9.2",
			"org.apache.xmlgraphics" % "batik-transcoder" % "1.7",
			"org.apache.xmlgraphics" % "batik-codec" % "1.7",
			"com.jhlabs" % "filters" % "2.0.235",
			"org.slf4j" % "slf4j-api" % "1.6.6",
			"org.scalatest" %% "scalatest" % "1.8" % "test"
		),

		ivyScala ~= { _.map(_.copy(checkExplicit = false, overrideScalaVersion = false, filterImplicit = false)) }
	)

	lazy val cmdline = Project(
		"cmdline", file("cmdline"), settings = commonSettings
	) dependsOn (core) settings (
		libraryDependencies ++= Seq(
			"ch.qos.logback" % "logback-classic" % "0.9.30",
			"net.elehack.argparse4s" %% "argparse4s" % "0.2"
		)
	)

	lazy val sbt_plugin = Project(
		"sbt-plugin", file("sbt-plugin"), settings = commonSettings
	) settings (
		sbtPlugin := true,
		name := "buketan-sbt",
		version := "0.1-SNAPSHOT",

		resolvers += Resolver.url("scalasbt releases", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns),
		addSbtPlugin("org.scala-sbt" % "sbt-android-plugin" % "0.6.2")
	) dependsOn (core)

	lazy val ant_plugin = Project(
		"ant-plugin", file("ant-plugin"), settings = commonSettings
	) settings (
		name := "buketan-ant",
		version := "0.1-SNAPSHOT",

		libraryDependencies ++= Seq(
			"ant" % "ant" % "1.6.5"
		),

		ivyScala ~= { _.map(_.copy(checkExplicit = false, overrideScalaVersion = false, filterImplicit = false)) }

	) dependsOn (core)
}