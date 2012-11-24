package net.kriomant.android_svg_res

import java.io.{File, FileOutputStream}
import org.slf4j.LoggerFactory
import java.awt.image.{BufferedImage, ByteLookupTable, LookupOp}
import org.w3c.dom.svg.SVGDocument
import org.apache.batik.dom.svg.SVGOMDocument
import java.awt.{AlphaComposite, Color, GradientPaint, Rectangle}

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
				renderNotificationV9,
				ResourceQualifiers(platformVersion=Some(9)),
				Seq((12,19), (16,25), (24,38), (32,50))
			),
			ImageVariant(
				renderNotificationPreV9,
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
				val image = variant.renderer(svgDocument, width, height)
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

	// http://developer.android.com/guide/practices/ui_guidelines/icon_design_status_bar.html#icon1
	def renderNotificationPreV9(doc: SVGOMDocument, width: Int, height: Int): BufferedImage = {
		assert(width == height)

		val safeframeWidth = (width.toFloat / 12.5).round.toInt
		val cornerRadius = safeframeWidth
		val padding = safeframeWidth
		val backgroundSize = width - 2*safeframeWidth
		val iconSize = backgroundSize - 2*padding

		val asset = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		val gradient = new GradientPaint(
			0, safeframeWidth, new Color(105,105,105),
			0, safeframeWidth+backgroundSize, new Color(10,10,10)
		)
		val gc = asset.createGraphics()
		gc.setPaint(gradient)
		gc.fillRoundRect(safeframeWidth, safeframeWidth, backgroundSize, backgroundSize, cornerRadius, cornerRadius)

		val foreground = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB)
		val fgc = foreground.createGraphics()
		fgc.setPaint(Color.white)
		fgc.fillRect(0, 0, iconSize, iconSize)

		val icon = svg.render(doc, iconSize, iconSize)
		fgc.setComposite(AlphaComposite.DstIn)
		fgc.drawImage(icon, null, 0, 0)
		fgc.dispose()

		gc.drawImage(foreground, null, safeframeWidth+padding, safeframeWidth+padding)
		gc.dispose()

		asset
	}

	// http://developer.android.com/guide/practices/ui_guidelines/icon_design_status_bar.html#style9
	def renderNotificationV9(doc: SVGOMDocument, width: Int, height: Int): BufferedImage = {
		// Full asset is rectangular, but icon must be drawn in centered square are.
		assert(height > width)
		val yOffset = (height - width) / 2

		// Icon is filled with gradient.
		val asset = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		val gradient = new GradientPaint(0, yOffset, new Color(0x919191), 0, yOffset + width, new Color(0x828282))
		val gc = asset.createGraphics()
		gc.setPaint(gradient)
		gc.fillRect(0, yOffset, width, width)

		val icon = svg.render(doc, width, width)
		gc.setComposite(AlphaComposite.DstIn)
		gc.drawImage(icon, null, 0, yOffset)

		asset
	}

	def renderNotificationV11(doc: SVGOMDocument, width: Int, height: Int): BufferedImage = {//
		// Create image filled with white.
		val asset = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		val background = Color.white
		val gc = asset.createGraphics()
		gc.setPaint(background)
		gc.fillRect(0, 0, width, height)

		val image = svg.render(doc, width, height)
		gc.setComposite(AlphaComposite.DstIn)
		gc.drawImage(image, null, 0, 0)

		asset
	}
}