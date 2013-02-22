import sbt._
import Keys._

object BuketanBuild extends Build {
	val commonSettings = Defaults.defaultSettings ++ Seq(
		version := "0.1-SNAPSHOT",
		scalaVersion := "2.9.2",

		organization := "net.kriomant.buketan",

		// Publishing by instructions from http://www.scala-sbt.org/release/docs/Community/Using-Sonatype.html
		publishMavenStyle := true,
		publishTo <<= version { v =>
			val nexus = "https://oss.sonatype.org/"
			Some(
				if (v.endsWith("SNAPSHOT"))
					"snapshots" at nexus + "content/repositories/snapshots"
				else
					"releases" at nexus + "service/local/staging/deploy/maven2"
			)
		},
		publishArtifact in Test := false,
		homepage := Some(url("https://github.com/kriomant/buketan")),
		licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php")),
		pomIncludeRepository := { _ => false },
		pomExtra := (
			<scm>
				<url>git@github.com:kriomant/buketan.git</url>
				<connection>scm:git:git@github.com:kriomant/buketan.git</connection>
			</scm>
			<developers>
				<developer>
					<id>kriomant</id>
					<name>Mikhail Trishchenkov</name>
					<url>http://kriomant.net</url>
				</developer>
			</developers>
		)
	)

	lazy val root = Project(
		"root", file("."), settings = commonSettings
	) settings (
		// This project is virtual, no need to publish it.
		publishArtifact := false
	) aggregate (
		core, cmdline, sbt_plugin, ant_plugin
	)

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
		//organization ~= (_+".sbt"),

		resolvers += Resolver.url("scalasbt releases", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns),
		addSbtPlugin("org.scala-sbt" % "sbt-android-plugin" % "0.6.2")
	) dependsOn (core)

	lazy val ant_plugin = Project(
		"ant-plugin", file("ant-plugin"), settings = commonSettings
	) settings (
		libraryDependencies ++= Seq(
			"ant" % "ant" % "1.6.5"
		),

		ivyScala ~= { _.map(_.copy(checkExplicit = false, overrideScalaVersion = false, filterImplicit = false)) }

	) dependsOn (core)
}
