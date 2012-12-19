package net.kriomant.buketan

import org.apache.batik.dom.svg.SVGOMDocument
import java.awt.image.BufferedImage
import java.io.{FilenameFilter, FileOutputStream, File}
import org.slf4j.LoggerFactory
import org.apache.batik.gvt.GraphicsNode
import java.awt.geom.{AffineTransform, Rectangle2D}
import java.awt.{GradientPaint, AlphaComposite, Color}
import org.apache.batik.bridge.BridgeContext
import com.jhlabs.image.GaussianFilter
import util.parsing.combinator.RegexParsers

object core {

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

	sealed trait ResourceRenderer
	case class PngRenderer(apply: SVGOMDocument => BufferedImage) extends ResourceRenderer
	case class XmlRenderer(apply: String => scala.xml.Elem) extends ResourceRenderer

	case class ResourceIntent(
		renderer: ResourceRenderer,
		qualifiers: ResourceQualifiers = ResourceQualifiers.empty,
		nameSuffix: Option[String] = None
	)

	/** Creates [[net.kriomant.buketan.core.ResourceIntent]] for each screen density value.
	  *
	  * Feeds `values` one by one to `f` function and modify `qualifiers` field
	  * in returned `ResourceIntent` to have corresponding `screenPixelDensity`.
	  *
		* @param values Arbitrary values for ldpi, mdpi, hdpi and xhdpi densities
	  *               (in that order) for passing to creation function `f`
	  * @param f Function which received one value from `values` and returns `ResourceIntent`
	  * @tparam T Type of data passed to `f`
	  * @return Sequence of created `ResourceIntent`s
	  */
	def mapDensities[T](values: (T, T, T, T))(f: T => ResourceIntent): Seq[ResourceIntent] = {
		(values.productIterator.toList zip DENSITIES) map { case (value, density) =>
			val variant = f(value.asInstanceOf[T])
			variant.copy(qualifiers = variant.qualifiers.copy(screenPixelDensity=Some(density)))
		}
	}

	/** Creates [[net.kriomant.buketan.core.ResourceIntent]] for each screen density value.
	  *
	  * Feeds `values` one by one to `f` function and modify `qualifiers` field
	  * in returned `ResourceIntent`s to have corresponding `screenPixelDensity`.
	  *
	  * @param values Arbitrary values for ldpi, mdpi, hdpi and xhdpi densities
	  *               (in that order) for passing to creation function `f`
	  * @param f Function which received one value from `values` and returns `ResourceIntent` sequence
	  * @tparam T Type of data passed to `f`
	  * @return Sequence of created `ResourceIntent`s
	  */
	def flatMapDensities[T](values: (T, T, T, T))(f: T => Seq[ResourceIntent]): Seq[ResourceIntent] = {
		(values.productIterator.toList zip DENSITIES) flatMap { case (value, density) =>
			val variants = f(value.asInstanceOf[T])
			variants map (v => v.copy(qualifiers = v.qualifiers.copy(screenPixelDensity=Some(density))))
		}
	}

	def generateFixedSizedResources(kind: ImageKind.Value)(doc: SVGOMDocument): Seq[ResourceIntent] = kind match {
		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_action_bar.html#size11
		case ImageKind.ActionBar => mapDensities((18, 24, 36, 48)) { size =>
			ResourceIntent(PngRenderer(simpleRender(size, size)))
		}

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_status_bar.html
		case ImageKind.Notification =>
			mapDensities((18, 24, 36, 48)) { size =>
				ResourceIntent(PngRenderer(renderNotificationV11(size)), ResourceQualifiers(platformVersion=Some(11)))
			} ++
			mapDensities(((12,19), (16,25), (24,38), (32,50))) { case (width, height) =>
				ResourceIntent(PngRenderer(renderNotificationV9(width, height)), ResourceQualifiers(platformVersion=Some(9)))
			} ++
			mapDensities(((19,1), (25,2), (38,3), (50,4))) { case (size, frame) =>
				ResourceIntent(PngRenderer(renderNotificationPreV9(size, frame)))
			}

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_launcher.html#size
		case ImageKind.Launcher => mapDensities((36, 48, 72, 96)) { size =>
			ResourceIntent(PngRenderer(simpleRender(size, size)))
		}

		// http://developer.android.com/guide/practices/ui_guidelines/icon_design_list.html
		case ImageKind.ListView => mapDensities((24, 32, 48, 64)) { size =>
			ResourceIntent(PngRenderer(simpleRender(size, size)))
		}

		case ImageKind.Tab => flatMapDensities(((24,1), (32,2), (48,3), (64,4))) { case (size, padding) => Seq(
			ResourceIntent(PngRenderer(renderTabV5Unselected(size, padding)), ResourceQualifiers(platformVersion=Some(5)), nameSuffix=Some("unselected")),
			ResourceIntent(PngRenderer(renderTabV5Selected(size, padding)), ResourceQualifiers(platformVersion=Some(5)), nameSuffix=Some("selected"))
		)} :+ ResourceIntent(XmlRenderer(renderTabSelectorResource), ResourceQualifiers(platformVersion=Some(5)))
	}

	def generateSimpleResources(doc: SVGOMDocument): Seq[ResourceIntent] = {
		val (ctx, gvt) = svg.prepareRendering(doc, createGvtMapping = true)

		val intrinsicWidth = ctx.getDocumentSize.getWidth
		val intrinsicHeight = ctx.getDocumentSize.getHeight
		logger.debug("Image size: {}x{}", intrinsicWidth, intrinsicHeight)

		// Use intrinsic document size for 'mdpi' density, scale image for
		// other densities accordingly.
		mapDensities((0.75, 1.0, 1.5, 2.0)) { scale =>
			val width = (intrinsicWidth * scale).toInt
			val height = (intrinsicHeight * scale).toInt
			ResourceIntent(PngRenderer(
				simpleRender(width, height)
			))
		}
	}

	def generateNinePatchResources(doc: SVGOMDocument): Seq[ResourceIntent] = {
		val (ctx, gvt) = svg.prepareRendering(doc, createGvtMapping = true)

		val aoi = svg.getAreaOfInterest(ctx)
		logger.debug("Area of interest: {}", aoi)

		val CONTENT_AREA_ELEMENT_ID = "content-area"
		val STRETCH_AREA_ELEMENT_ID = "stretch-area"

		val contentAreaElement = Option(doc.getElementById(CONTENT_AREA_ELEMENT_ID))
		contentAreaElement.foreach { e =>
			if (e.getTagName != "rect")
				throw new Exception("Content area (element with ID '%s') must be rectangle" format CONTENT_AREA_ELEMENT_ID)
		}

		val stretchAreaElement = doc.getElementById(STRETCH_AREA_ELEMENT_ID)
		if (stretchAreaElement == null)
			throw new Exception("9-patch SVG document must contain rectangle with ID '%s'" format STRETCH_AREA_ELEMENT_ID)
		if (stretchAreaElement.getTagName != "rect")
			throw new Exception("Stretch area (element with ID '%s') must be rectangle" format STRETCH_AREA_ELEMENT_ID)

		// Get graphics node bounds in user coordinate system.
		def getBounds(gn: GraphicsNode): Rectangle2D = {
			// This is bounds in coordinate system of graphics node.
			val bounds = gn.getBounds
			gn.getGlobalTransform.createTransformedShape(bounds).getBounds2D
		}

		val stretchGraphicsNode = ctx.getGraphicsNode(stretchAreaElement)
		val stretchArea = getBounds(stretchGraphicsNode)
		logger.debug("Stretch area: {}", stretchArea)

		val contentGraphicsNode = contentAreaElement.map(ctx.getGraphicsNode(_))
		val contentArea = contentGraphicsNode.map(getBounds)
		logger.debug("Content area: {}", contentArea)

		// Hide *-area elements.
		contentGraphicsNode.foreach { n => n.setVisible(false) }
		stretchGraphicsNode.setVisible(false)

		// Use intrinsic document size for 'mdpi' density, scale image for
		// other densities accordingly.
		mapDensities((0.75, 1.0, 1.5, 2.0)) { scale =>
			val width = (aoi.width * scale).toInt
			val height = (aoi.height * scale).toInt
			ResourceIntent(PngRenderer(
				renderNinePatch(width, height, ctx, gvt, aoi, stretchArea, contentArea)
			))
		}
	}

	def convert(generator: SVGOMDocument => Seq[ResourceIntent], sourceFile: File, resourcesDirectory: File, baseQualifiers: ResourceQualifiers) {
		val fileName = sourceFile.getName
		if (! (fileName endsWith ".svg"))
			throw new IllegalArgumentException("Source file must have .SVG extension")
		val baseName = fileName.dropRight(4)

		// Load SVG document.
		val svgDocument = svg.load(sourceFile)

		convert(generator, svgDocument, baseName, resourcesDirectory, baseQualifiers)
	}

	def convert(generator: SVGOMDocument => Seq[ResourceIntent], svgDocument: SVGOMDocument, baseName: String, resourcesDirectory: File, baseQualifiers: ResourceQualifiers): Seq[File] = {
		val variants = generator(svgDocument)
		if (logger.isDebugEnabled)
			logger.debug("Variants: {}", variants)

		variants.map { variant =>
			val drawableDirectory = new File(resourcesDirectory, "drawable-%s" format variant.qualifiers)
			if (!drawableDirectory.exists) {
				logger.debug("Create '{}' resources directory", drawableDirectory.getName)
				drawableDirectory.mkdir()
			}

			val suffix = variant.nameSuffix.map("_" + _).getOrElse("")
			val resourceName = "%s%s" format (baseName, suffix)

			variant.renderer match {
				case PngRenderer(render) =>
					val image = render(svgDocument)
					val targetFile = new File(drawableDirectory, "%s.png" format resourceName)
					logger.info("Render {} to {}", baseName, targetFile)
					val output = new FileOutputStream(targetFile)
					try {
						svg.savePng(image, output)
					} finally {
						output.close()
					}
					targetFile

				case XmlRenderer(render) =>
					val xml = render(resourceName)
					val targetFile = new File(drawableDirectory, "%s.xml" format resourceName)
					logger.info("Render {} to {}", baseName, targetFile)
					scala.xml.XML.save(targetFile.getAbsolutePath, xml)
					targetFile
			}
		}
	}

	case class BatchItem(baseName: String, action: String, qualifiers: Option[ResourceQualifiers])

	object fileNameParser extends RegexParsers {
		val baseName = """(?i)\w+""".r
		val action = """[\w\d-]+""".r
		val qualifiers = """(?i)[\w\d\-]+""".r ^^ ResourceQualifiers.parse
		val directives = opt("." ~> action) ~ opt("," ~> qualifiers)
		val fileName = (baseName ~ directives) ^^ { case n~(a~q) => BatchItem(n, a getOrElse "", q) }

		def parse(s: String): BatchItem = {
			parseAll(fileName, s) match {
				case Success(res, _) => res
				case err: NoSuccess => throw new Exception("""Invalid file name "%s": %s""" format (s, err.msg))
			}
		}
	}

	def batch(sourceDirectory: File, resourcesDirectory: File, baseQualifiers: ResourceQualifiers) {
		object svgFileFilter extends FilenameFilter {
			def accept(dir: File, filename: String): Boolean = filename.endsWith(".svg")
		}
		for (sourceFile <- sourceDirectory.listFiles(svgFileFilter)) {
			try {
			autoConvert(sourceFile, resourcesDirectory, baseQualifiers)
			} catch {
				case UnknownActionError(action) => logger.error("Unknown action '{}' for file '{}'", action, sourceFile.getName)
			}
		}
	}

	case class UnknownActionError(action: String) extends Exception("Unknown action '%s'" format action)

	def autoConvert(sourceFile: File, resourcesDirectory: File, baseQualifiers: ResourceQualifiers = ResourceQualifiers.empty): Seq[File] = {
		val batchItem = fileNameParser.parse(sourceFile.getName.stripSuffix(".svg"))
		val (generator, baseName) = batchItem.action match {
			case "" => (Some(generateSimpleResources _), batchItem.baseName)
			case "9" => (Some(generateNinePatchResources _), batchItem.baseName + ".9")
			case action => (ImageKind.values.find(_.toString == action).map(generateFixedSizedResources), batchItem.baseName)
		}

		generator match {
			case Some(gen) =>
				val doc = svg.load(sourceFile)
				val qualifiers = batchItem.qualifiers.map(baseQualifiers.update(_)).getOrElse(baseQualifiers)
				convert(gen, doc, baseName, resourcesDirectory, qualifiers)

			case None => throw UnknownActionError(batchItem.action)
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
	def renderNinePatch(
		width: Int, height: Int,
		ctx: BridgeContext, gvtRoot: GraphicsNode,
		aoi: Rectangle2D.Float, stretchArea: Rectangle2D, contentArea: Option[Rectangle2D]
	)(doc: SVGOMDocument): BufferedImage = {
		val transformation = svg.rectToRectTransformation(aoi, new Rectangle2D.Float(0, 0, width, height))

		val icon = svg.render(ctx, gvtRoot, width, height)

		val image = new BufferedImage(width+2, height+2, BufferedImage.TYPE_INT_ARGB)
		val gc = image.createGraphics()
		gc.drawImage(icon, null, 1, 1)

		gc.setPaint(Color.black)

		// Drawing rectangle of width 3 will produce 4 pixels, so it
		// it needed to decrease width and height by 1 during rendering.

		// Transformed stretch area.
		val tsa = transformation.createTransformedShape(stretchArea).getBounds
		logger.trace("Stretch area: {}", tsa)
		gc.drawLine(tsa.x+1, 0, tsa.x+tsa.width, 0)
		gc.drawLine(0, tsa.y+1, 0, tsa.y+tsa.height)

		// Transformed content area.
		contentArea.foreach { area =>
			val tca = transformation.createTransformedShape(area).getBounds
			logger.trace("Content area: {}", tca)
			gc.drawLine(tca.x+1, height+1, tca.x+tca.width, height+1)
			gc.drawLine(width+1, tca.y+1, width+1, tca.y+tca.height)
		}

		gc.dispose()

		image
	}

	def renderTabSelectorResource(baseName: String): scala.xml.Elem = {
		<selector xmlns:android="http://schemas.android.com/apk/res/android">
			<item android:drawable={"@drawable/%s_selected" format baseName}
			      android:state_selected="true"
			      android:state_pressed="false" />
			<item android:drawable={"@drawable/%s_unselected" format baseName} />
		</selector>
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