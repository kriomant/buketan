package net.kriomant.android_svg_res

import org.apache.batik.transcoder.image.PNGTranscoder
import org.apache.batik.transcoder._
import java.io.{File, FileOutputStream, FileInputStream}
import org.slf4j.LoggerFactory

object Main {
	val logger = LoggerFactory.getLogger(getClass)

	def array(values: Any*): Array[AnyRef] = values.map(_.asInstanceOf[AnyRef]).toArray

	object ImageKind extends Enumeration {
		val ActionBar = Value("action-bar")
	}

	val DENSITIES = Seq("ldpi", "mdpi", "hdpi", "xhdpi")

	/** Get image sizes for given image kind for ldpi, mdpi, hdpi, xhdpi densities.
	  */
	def getImageSizes(kind: ImageKind.Value): Seq[(Int, Int)] = kind match {
		case ImageKind.ActionBar => Seq((18,18), (24,24), (36,36), (48,48))
	}

	def main(args: Array[String]) {
		if (args.length == 3) {
			val Array(sourceFilePath, kindName, resourcesDirectoryPath) = args

			val sourceFile = new File(sourceFilePath)
			val fileName = sourceFile.getName
			if (! (fileName endsWith ".svg"))
				throw new IllegalArgumentException("Source file must have .SVG extension")
			val baseName = fileName.dropRight(4)

			val kind = ImageKind.withName(kindName)
			val sizes = getImageSizes(kind)
			logger.debug("Sizes: {}", sizes)

			val transcoder = new PNGTranscoder

			val resourcesDirectory = new File(resourcesDirectoryPath)
			for ((density, (width, height)) <- DENSITIES zip sizes) {
				val drawableDirectory = new File(resourcesDirectory, "drawable-%s" format density)
				val targetFile = new File(drawableDirectory, "%s.png" format baseName)

				val input = new TranscoderInput(new FileInputStream(sourceFile))
				val output = new TranscoderOutput(new FileOutputStream(targetFile))

				transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, width.toFloat)
				transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, height.toFloat)
				logger.info("Render {} to {} with size {}x{}", array(sourceFilePath, targetFile, width, height))
				transcoder.transcode(input, output)
			}

		} else {
			println("""Usage:
				|sbt "run <file.svg> <resource-type> <path/to/resources>"
				|where
				|  <resource-type> is one of: action-bar;
				|  <path/to/directory> is path to resources directory containing 'drawable-*' directories.
			""".stripMargin)
		}
	}
}