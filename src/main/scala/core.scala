package net.kriomant.android_svg_res

import org.apache.batik.dom.svg.SVGOMDocument
import java.awt.image.BufferedImage
import java.io.{FileOutputStream, File}
import org.slf4j.LoggerFactory
import org.apache.batik.gvt.GraphicsNode
import java.awt.geom.{AffineTransform, Rectangle2D}
import java.awt.{GradientPaint, AlphaComposite, Color}
import com.jhlabs.image.GaussianFilter

object core {

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

	type Renderer = SVGOMDocument => BufferedImage
	case class ImageVariant(
		renderer: Renderer,
		qualifiers: ResourceQualifiers = ResourceQualifiers.empty,
		nameSuffix: Option[String] = None
	)

	/** Creates [[net.kriomant.android_svg_res.core.ImageVariant]] for each screen density value.
	  *
	  * Feeds `values` one by one to `f` function and modify `qualifiers` field
	  * in returned `ImageVariant` to have corresponding `screenPixelDensity`.
	  *
		* @param values Arbitrary values for ldpi, mdpi, hdpi and xhdpi densities
	  *               (in that order) for passing to creation function `f`
	  * @param f Function which received one value from `values` and returns `ImageVariant`
	  * @tparam T Type of data passed to `f`
	  * @return Sequence of created `ImageVariant`s
	  */
	def mapDensities[T](values: (T, T, T, T))(f: T => ImageVariant): Seq[ImageVariant] = {
		(values.productIterator.toSeq zip DENSITIES) map { case (value, density) =>
			val variant = f(value.asInstanceOf[T])
			variant.copy(qualifiers = variant.qualifiers.copy(screenPixelDensity=Some(density)))
		}
	}

	/** Creates [[net.kriomant.android_svg_res.core.ImageVariant]] for each screen density value.
	  *
	  * Feeds `values` one by one to `f` function and modify `qualifiers` field
	  * in returned `ImageVariant`s to have corresponding `screenPixelDensity`.
	  *
	  * @param values Arbitrary values for ldpi, mdpi, hdpi and xhdpi densities
	  *               (in that order) for passing to creation function `f`
	  * @param f Function which received one value from `values` and returns `ImageVariant` sequence
	  * @tparam T Type of data passed to `f`
	  * @return Sequence of created `ImageVariant`s
	  */
	def flatMapDensities[T](values: (T, T, T, T))(f: T => Seq[ImageVariant]): Seq[ImageVariant] = {
		(values.productIterator.toSeq zip DENSITIES) flatMap { case (value, density) =>
			val variants = f(value.asInstanceOf[T])
			variants map (v => v.copy(qualifiers = v.qualifiers.copy(screenPixelDensity=Some(density))))
		}
	}

	/** Get image sizes for given image kind for ldpi, mdpi, hdpi, xhdpi densities.
	  */
	def getImageVariants(kind: ImageKind.Value): Seq[ImageVariant] = kind match {
		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_action_bar.html#size11
		case ImageKind.ActionBar => mapDensities((18, 24, 36, 48)) { size =>
			ImageVariant(simpleRender(size, size))
		}

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_status_bar.html
		case ImageKind.Notification =>
			mapDensities((18, 24, 36, 48)) { size =>
				ImageVariant(renderNotificationV11(size), ResourceQualifiers(platformVersion=Some(11)))
			} ++
			mapDensities(((12,19), (16,25), (24,38), (32,50))) { case (width, height) =>
				ImageVariant(renderNotificationV9(width, height), ResourceQualifiers(platformVersion=Some(9)))
			} ++
			mapDensities(((19,1), (25,2), (38,3), (50,4))) { case (size, frame) =>
				ImageVariant(renderNotificationPreV9(size, frame))
			}

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_launcher.html#size
		case ImageKind.Launcher => mapDensities((36, 48, 72, 96)) { size =>
			ImageVariant(simpleRender(size, size))
		}

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_list.html
		case ImageKind.ListView => mapDensities((24, 32, 48, 64)) { size =>
			ImageVariant(simpleRender(size, size))
		}

		case ImageKind.Tab => flatMapDensities(((24,1), (32,2), (48,3), (64,4))) { case (size, padding) => Seq(
			ImageVariant(renderTabV5Unselected(size, padding), ResourceQualifiers(platformVersion=Some(5)), nameSuffix=Some("unselected")),
			ImageVariant(renderTabV5Selected(size, padding), ResourceQualifiers(platformVersion=Some(5)), nameSuffix=Some("selected")),
			ImageVariant(simpleRender(size, size))
		)}

		case ImageKind.NinePatch => mapDensities((24, 32, 48, 64)) { size =>
			ImageVariant(renderNinePatch(size, size))
		}
	}

	def convert(kind: ImageKind.Value, sourceFile: File, resourcesDirectory: File, baseQualifiers: ResourceQualifiers) {
		val fileName = sourceFile.getName
		if (! (fileName endsWith ".svg"))
			throw new IllegalArgumentException("Source file must have .SVG extension")
		val baseName = fileName.dropRight(4)

		val variants = getImageVariants(kind)
		if (logger.isDebugEnabled)
			logger.debug("Variants: {}", variants)

		// Load SVG document.
		val svgDocument = svg.load(sourceFile)

		for (variant <- variants) {
			val drawableDirectory = new File(resourcesDirectory, "drawable-%s" format variant.qualifiers)
			if (!drawableDirectory.exists) {
				logger.debug("Create '{}' resources directory", drawableDirectory.getName)
				drawableDirectory.mkdir()
			}

			val suffix = variant.nameSuffix.map("_" + _).getOrElse("")
			val targetFile = new File(drawableDirectory, "%s%s.png" format (baseName, suffix))

			logger.info("Render {} to {}", sourceFile.getPath, targetFile)
			val image = variant.renderer(svgDocument)
			val output = new FileOutputStream(targetFile)

			svg.savePng(image, output)
		}
	}

	def simpleRender(width: Int, height: Int)(doc: SVGOMDocument): BufferedImage = {
		svg.render(doc, width, height)
	}

	/** Render 9-patch image.
	  *
	  * 9-patch image format is described at
	  * http://developer.android.com/guide/topics/graphics/2d-graphics.html#nine-patch
	  */
	def renderNinePatch(width: Int, height: Int)(doc: SVGOMDocument): BufferedImage = {
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
		val (ctx, gvtRoot) = svg.prepareRendering(doc, createGvtMapping = true)
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

	def renderTabV5Unselected(size: Int, padding: Int)(doc: SVGOMDocument): BufferedImage = {
		val iconSize = size - 2*padding

		val asset = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
		val gc = asset.createGraphics()
		gc.setPaint(new Color(0x80,0x80,0x80))
		gc.fillRect(padding, padding, iconSize, iconSize)

		val icon = svg.render(doc, iconSize, iconSize)
		gc.setComposite(AlphaComposite.DstIn)
		gc.drawImage(icon, null, padding, padding)
		gc.dispose()

		asset
	}

	def renderTabV5Selected(size: Int, padding: Int)(doc: SVGOMDocument): BufferedImage = {
		val iconSize = size - 2*padding

		val icon = svg.render(doc, iconSize, iconSize)

		val preGlow = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
		val ggc = preGlow.createGraphics()
		ggc.setPaint(new Color(0, 0, 0, 64))
		ggc.fillRect(padding, padding, iconSize, iconSize)
		ggc.setComposite(AlphaComposite.DstIn)
		ggc.drawImage(icon, null, padding, padding)
		ggc.dispose()

		val glow = new GaussianFilter(size / 8.0f).filter(preGlow, null)

		val white = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB)
		val wgc = white.createGraphics()
		wgc.setPaint(Color.white)
		wgc.fillRect(0, 0, iconSize, iconSize)
		wgc.setComposite(AlphaComposite.DstIn)
		wgc.drawImage(icon, null, 0, 0)
		wgc.dispose()

		val asset = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
		val gc = asset.createGraphics()
		gc.drawImage(glow, null, 0, 0)
		gc.drawImage(white, null, padding, padding)
		gc.dispose()

		asset
	}

	// http://developer.android.com/guide/practices/ui_guidelines/icon_design_status_bar.html#icon1
	def renderNotificationPreV9(size: Int, safeframeWidth: Int)(doc: SVGOMDocument): BufferedImage = {
		val cornerRadius = safeframeWidth
		val padding = safeframeWidth
		val backgroundSize = size - 2*safeframeWidth
		val iconSize = backgroundSize - 2*padding

		val asset = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
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
	def renderNotificationV9(width: Int, height: Int)(doc: SVGOMDocument): BufferedImage = {
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

	def renderNotificationV11(size: Int)(doc: SVGOMDocument): BufferedImage = {
		// Create image filled with white.
		val asset = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
		val background = Color.white
		val gc = asset.createGraphics()
		gc.setPaint(background)
		gc.fillRect(0, 0, size, size)

		val image = svg.render(doc, size, size)
		gc.setComposite(AlphaComposite.DstIn)
		gc.drawImage(image, null, 0, 0)

		asset
	}
}