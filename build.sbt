name := "Android SVG resource renderer"

version := "0.1"

scalaVersion := "2.10.0-RC2"

libraryDependencies ++= Seq(
	"org.apache.xmlgraphics" % "batik-transcoder" % "1.7",
	"org.apache.xmlgraphics" % "batik-codec" % "1.7",
	"ch.qos.logback" % "logback-classic" % "0.9.30",
	"com.jhlabs" % "filters" % "2.0.235",
	"org.scalatest" % "scalatest_2.10.0-RC2" % "1.8" % "test"
)

