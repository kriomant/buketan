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
		val Notification = Value("notification")
		val Launcher = Value("launcher")
		val ListView = Value("list-view")
		val Tab = Value("tab")
	}

	val DENSITIES = Seq("ldpi", "mdpi", "hdpi", "xhdpi")

	/** Get image sizes for given image kind for ldpi, mdpi, hdpi, xhdpi densities.
	  */
	def getImageSizes(kind: ImageKind.Value, platformVersion: Option[Int]): Seq[(Int, Int)] = (kind, platformVersion) match {
		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_action_bar.html#size11
		case (ImageKind.ActionBar, _) => Seq((18,18), (24,24), (36,36), (48,48))

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_status_bar.html
		case (ImageKind.Notification, Some(v)) if v >= 11 => Seq((18,18), (24,24), (36,36), (48,48))
		case (ImageKind.Notification, Some(v)) if v >=  9 => Seq((12,19), (16,25), (24,38), (32,50))
		case (ImageKind.Notification, _      )            => Seq((12,19), (16,25), (24,38), (32,50))

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_launcher.html#size
		case (ImageKind.Launcher, _) => Seq((36,36), (48,48), (72,72), (96,96))

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_list.html
		case (ImageKind.ListView, _) => Seq((24,24), (32,32), (48,48), (64,64))

		case (ImageKind.Tab, _) => Seq((24,24), (32,32), (48,48), (64,64))
	}

	def main(args: Array[String]) {
		if ((3 to 4) contains args.length) {
			val Array(sourceFilePath, kindName, resourcesDirectoryPath) = args.take(3)
			val baseQualifiers = if (args.length >= 4) ResourceQualifiers.parse(args(3)) else ResourceQualifiers()

			val sourceFile = new File(sourceFilePath)
			val fileName = sourceFile.getName
			if (! (fileName endsWith ".svg"))
				throw new IllegalArgumentException("Source file must have .SVG extension")
			val baseName = fileName.dropRight(4)

			val kind = ImageKind.withName(kindName)
			val sizes = getImageSizes(kind, baseQualifiers.platformVersion)
			logger.debug("Sizes: {}", sizes)

			val transcoder = new PNGTranscoder

			val resourcesDirectory = new File(resourcesDirectoryPath)
			for ((density, (width, height)) <- DENSITIES zip sizes) {
				val qualifiers = baseQualifiers.copy(screenPixelDensity = Some(density))

				val drawableDirectory = new File(resourcesDirectory, "drawable-%s" format qualifiers)
				if (!drawableDirectory.exists) {
					logger.debug("Create '{}' resources directory", drawableDirectory.getName)
					drawableDirectory.mkdir()
				}

				val targetFile = new File(drawableDirectory, "%s.png" format baseName)

				val input = new TranscoderInput(new FileInputStream(sourceFile))
				val output = new TranscoderOutput(new FileOutputStream(targetFile))

				transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, width.toFloat)
				transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, height.toFloat)
				logger.info("Render {} to {} with size {}x{}", array(sourceFilePath, targetFile, width, height))
				transcoder.transcode(input, output)
			}

		} else {
			println(s"""Usage:
				|sbt "run <file.svg> <resource-type> <path/to/resources> [<resource-qualifiers>]"
				|where
				|  <resource-type> is one of: ${ImageKind.values.mkString(", ")};
				|  <path/to/directory> is path to resources directory containing 'drawable-*' directories.
				|  <resource-qualifiers> are qualifiers added to name of 'drawable-*' directory.
				|     Resource qualifiers are optional in general, but are required for some types
				|     of images. If they are required, but you don't need ones, use empty string.
			""".stripMargin)
		}
	}
}