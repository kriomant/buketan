package net.kriomant.android_svg_res

import java.io.{File, FileOutputStream}
import org.slf4j.LoggerFactory
import java.awt.image.{BufferedImage, ByteLookupTable, LookupOp}
import org.w3c.dom.svg.SVGDocument
import org.apache.batik.dom.svg.SVGOMDocument

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

	type Renderer = (SVGOMDocument, Int, Int) => BufferedImage
	case class ImageVariant(renderer: Renderer, qualifiers: ResourceQualifiers, sizes: Seq[(Int, Int)])

	/** Get image sizes for given image kind for ldpi, mdpi, hdpi, xhdpi densities.
	  */
	def getImageVariants(kind: ImageKind.Value): Seq[ImageVariant] = kind match {
		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_action_bar.html#size11
		case ImageKind.ActionBar => Seq(ImageVariant(
			svg.render,
			ResourceQualifiers.empty,
			Seq((18,18), (24,24), (36,36), (48,48))
		))

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_status_bar.html
		case ImageKind.Notification => Seq(
			ImageVariant(
				renderNotificationV11,
				ResourceQualifiers(platformVersion=Some(11)),
				Seq((18,18), (24,24), (36,36), (48,48))
			),
			ImageVariant(
				svg.render,
				ResourceQualifiers(platformVersion=Some(9)),
				Seq((12,19), (16,25), (24,38), (32,50))
			),
			ImageVariant(
				svg.render,
				ResourceQualifiers.empty,
				Seq((19,19), (25,25), (38,38), (50,50))
			)
		)

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_launcher.html#size
		case ImageKind.Launcher => Seq(ImageVariant(
			svg.render, ResourceQualifiers.empty, Seq((36,36), (48,48), (72,72), (96,96))
		))

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_list.html
		case ImageKind.ListView => Seq(ImageVariant(
			svg.render, ResourceQualifiers.empty, Seq((24,24), (32,32), (48,48), (64,64))
		))

		case ImageKind.Tab => Seq(ImageVariant(
			svg.render, ResourceQualifiers.empty, Seq((24,24), (32,32), (48,48), (64,64))
		))
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
			val variants = getImageVariants(kind)
			if (logger.isDebugEnabled)
				logger.debug("Variants: {}", variants.map(v => "%s: %s" format (v.qualifiers, v.sizes)).mkString(", "))

			val resourcesDirectory = new File(resourcesDirectoryPath)
			for (
				variant <- variants;
				(density, (width, height)) <- DENSITIES zip variant.sizes
			) {
				val qualifiers = baseQualifiers.update(variant.qualifiers).copy(screenPixelDensity = Some(density))

				val drawableDirectory = new File(resourcesDirectory, "drawable-%s" format qualifiers)
				if (!drawableDirectory.exists) {
					logger.debug("Create '{}' resources directory", drawableDirectory.getName)
					drawableDirectory.mkdir()
				}

				val targetFile = new File(drawableDirectory, "%s.png" format baseName)

				// Load SVG document.
				val svgDocument = svg.load(sourceFile)

				logger.info("Render {} to {} with size {}x{}", array(sourceFilePath, targetFile, width, height))
				val image = svg.render(svgDocument, width, height)
				invertImageInPlace(image)
				val output = new FileOutputStream(targetFile)

				svg.savePng(image, output)
			}

		} else {
			val kinds = ImageKind.values.mkString(", ")
			println(s"""Usage:
				|sbt "run <file.svg> <resource-type> <path/to/resources> [<resource-qualifiers>]"
				|where
				|  <resource-type> is one of: $kinds;
				|  <path/to/directory> is path to resources directory containing 'drawable-*' directories.
				|  <resource-qualifiers> are qualifiers added to name of 'drawable-*' directory.
				|     Resource qualifiers are optional in general, but are required for some types
				|     of images. If they are required, but you don't need ones, use empty string.
			""".stripMargin)
		}
	}

	def renderNotificationV11(doc: SVGOMDocument, width: Int, height: Int): BufferedImage = {
		val image = svg.render(doc, width, height)
		invertImageInPlace(image)
		image
	}

	def invertImageInPlace(image: BufferedImage) {
		val noopTable = (0 to 255).map(_.toByte).toArray
		val invertionTable = noopTable.reverse
		val op = new LookupOp(
			// I don't understand why alpha channel is third (not first or last), but it works this way only.
			new ByteLookupTable(0, Array(invertionTable, invertionTable, noopTable, invertionTable)),
			null
		)
		op.filter(image, image)
	}
}