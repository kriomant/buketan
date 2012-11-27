package net.kriomant.android_svg_res

import java.io.{File, FileOutputStream}
import org.slf4j.LoggerFactory
import java.awt.image.{BufferedImage}
import org.apache.batik.dom.svg.{SVGOMElement, SVGOMAElement, SVGOMDocument}
import java.awt.{Rectangle, AlphaComposite, Color, GradientPaint}
import java.awt.geom.{AffineTransform, Rectangle2D}
import org.w3c.dom.Element
import org.apache.batik.gvt.GraphicsNode

object Main {
	val logger = LoggerFactory.getLogger(getClass)

	def array(values: Any*): Array[AnyRef] = values.map(_.asInstanceOf[AnyRef]).toArray

	object ImageKind extends Enumeration {
		val ActionBar = Value("action-bar")
		val Notification = Value("notification")
		val Launcher = Value("launcher")
		val ListView = Value("list-view")
		val Tab = Value("tab")
		val NinePatch = Value("9-patch")
	}

	val DENSITIES = Seq("ldpi", "mdpi", "hdpi", "xhdpi")

	type Renderer = (SVGOMDocument, Int, Int) => BufferedImage
	case class ImageVariant(
		renderer: Renderer,
		qualifiers: ResourceQualifiers,
		sizes: Seq[(Int, Int)],
		nameSuffix: Option[String] = None
	)

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

		case ImageKind.Tab => Seq(
			ImageVariant(
				renderTabV5Unselected, ResourceQualifiers(platformVersion=Some(5)), Seq((24,24), (32,32), (48,48), (64,64)),
				nameSuffix=Some("unselected")
			),
			ImageVariant(
				renderTabV5Selected, ResourceQualifiers(platformVersion=Some(5)), Seq((24,24), (32,32), (48,48), (64,64)),
				nameSuffix=Some("selected")
			),
			ImageVariant(
				svg.render, ResourceQualifiers.empty, Seq((24,24), (32,32), (48,48), (64,64))
			)
		)

		case ImageKind.NinePatch => Seq(ImageVariant(
			renderNinePatch, ResourceQualifiers.empty, Seq((24,24), (32,32), (48,48), (64,64))
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

			// Load SVG document.
			val svgDocument = svg.load(sourceFile)

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

				val suffix = variant.nameSuffix.map("_" + _).getOrElse("")
				val targetFile = new File(drawableDirectory, "%s%s.png" format (baseName, suffix))

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

	/** Render 9-patch image.
	  *
	  * 9-patch image format is described at
	  * http://developer.android.com/guide/topics/graphics/2d-graphics.html#nine-patch
	  */
	def renderNinePatch(doc: SVGOMDocument, width: Int, height: Int): BufferedImage = {
		val CONTENT_AREA_ELEMENT_ID = "content-area"
		val STRETCH_AREA_ELEMENT_ID = "stretch-area"

		val contentAreaElement = Option(doc.getElementById(CONTENT_AREA_ELEMENT_ID))
		if (! contentAreaElement.map(_.getTagName == "rect").getOrElse(true))
			throw new Exception("Content area (element with ID '%s') must be rectangle" format CONTENT_AREA_ELEMENT_ID)

		val stretchAreaElement = doc.getElementById(STRETCH_AREA_ELEMENT_ID)
		if (stretchAreaElement == null)
			throw new Exception("9-patch SVG document must contain rectangle with ID '%s'" format STRETCH_AREA_ELEMENT_ID)
		if (stretchAreaElement.getTagName != "rect")
			throw new Exception("Stretch area (element with ID '%s') must be rectangle" format STRETCH_AREA_ELEMENT_ID)

		// Create SVG content and GVT element. Tell context to maintain mapping between
		// source XML nodes and graphic elements.
		val (ctx, gvtRoot) = svg.prepareRendering(doc, true)
		val transformation = svg.getTransformation(ctx, width, height)

		def getBounds(gn: GraphicsNode): Rectangle2D = {
			val bounds = gn.getBounds
			val tr = new AffineTransform(gn.getGlobalTransform)
			tr.preConcatenate(transformation)
			tr.createTransformedShape(bounds).getBounds2D
		}

		val contentGraphicsNode = contentAreaElement.map(ctx.getGraphicsNode(_))
		val contentArea = contentGraphicsNode.map(getBounds)

		val stretchGraphicsNode = ctx.getGraphicsNode(stretchAreaElement)
		val stretchArea = getBounds(stretchGraphicsNode)

		// Hide *-area elements.
		contentGraphicsNode.foreach { n => n.setVisible(false) }
		stretchGraphicsNode.setVisible(false)

		val icon = svg.render(ctx, gvtRoot, width, height)

		val image = new BufferedImage(width+2, height+2, BufferedImage.TYPE_INT_ARGB)
		val gc = image.createGraphics()
		gc.drawImage(icon, null, 1, 1)

		gc.setPaint(Color.black)

		// Stretch area.
		logger.trace("Stretch area: {}", stretchArea)
		gc.drawLine(stretchArea.getMinX.toInt+1, 0, stretchArea.getMaxX.toInt+1, 0)
		gc.drawLine(0, stretchArea.getMinY.toInt+1, 0, stretchArea.getMaxY.toInt+1)

		// Content area.
		contentArea.foreach { area =>
			logger.trace("Content area: {}", area)
			gc.drawLine(area.getMinX.toInt+1, height+1, area.getMaxX.toInt+1, height+1)
			gc.drawLine(width+1, area.getMinY.toInt+1, width+1, area.getMaxY.toInt+1)
		}

		gc.dispose()

		image
	}

	def renderTabV5Unselected(doc: SVGOMDocument, width: Int, height: Int): BufferedImage = {
		val paddings = Map(24 -> 1, 32 -> 2, 48 -> 3, 64 -> 4)
		val padding = paddings(width)
		val iconSize = width - 2*padding

		val asset = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		val gc = asset.createGraphics()
		gc.setPaint(new Color(0x80,0x80,0x80))
		gc.fillRect(padding, padding, iconSize, iconSize)

		val icon = svg.render(doc, iconSize, iconSize)
		gc.setComposite(AlphaComposite.DstIn)
		gc.drawImage(icon, null, padding, padding)
		gc.dispose()

		asset
	}

	def renderTabV5Selected(doc: SVGOMDocument, width: Int, height: Int): BufferedImage = {
		val paddings = Map(24 -> 1, 32 -> 2, 48 -> 3, 64 -> 4)
		val padding = paddings(width)
		val iconSize = width - 2*padding

		val icon = svg.render(doc, iconSize, iconSize)

		val preGlow = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		val ggc = preGlow.createGraphics()
		ggc.setPaint(new Color(0, 0, 0, 64))
		ggc.fillRect(padding, padding, iconSize, iconSize)
		ggc.setComposite(AlphaComposite.DstIn)
		ggc.drawImage(icon, null, padding, padding)
		ggc.dispose()

		val glow = new com.jhlabs.image.GaussianFilter(width / 8.0f).filter(preGlow, null)

		val white = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB)
		val wgc = white.createGraphics()
		wgc.setPaint(Color.white)
		wgc.fillRect(0, 0, iconSize, iconSize)
		wgc.setComposite(AlphaComposite.DstIn)
		wgc.drawImage(icon, null, 0, 0)
		wgc.dispose()

		val asset = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		val gc = asset.createGraphics()
		gc.drawImage(glow, null, 0, 0)
		gc.drawImage(white, null, padding, padding)
		gc.dispose()

		asset
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

	def renderNotificationV11(doc: SVGOMDocument, width: Int, height: Int): BufferedImage = {
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