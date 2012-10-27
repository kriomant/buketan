name := "Android SVG resource renderer"

version := "0.1"

scalaVersion := "2.10.0-RC1"

libraryDependencies ++= Seq(
	"org.apache.xmlgraphics" % "batik-transcoder" % "1.7",
	"org.apache.xmlgraphics" % "batik-codec" % "1.7"
)

