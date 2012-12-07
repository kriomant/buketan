package net.kriomant.buketan

import org.apache.batik.dom.svg.{SAXSVGDocumentFactory, SVGOMDocument}
import java.io._
import java.awt.RenderingHints
import org.apache.batik.util.{XMLResourceDescriptor, SVGConstants}
import org.apache.batik.dom.util.DocumentFactory
import java.awt.image.BufferedImage
import org.apache.batik.ext.awt.image.codec.png.{PNGImageEncoder, PNGEncodeParam}
import org.apache.batik.bridge.{ViewBox, BridgeContext, GVTBuilder, UserAgentAdapter}
import org.apache.batik.gvt.GraphicsNode
import org.apache.batik.bridge.svg12.SVG12BridgeContext
import java.awt.geom.{Rectangle2D, AffineTransform}
import org.apache.batik.ext.awt.image.GraphicsUtil
import org.slf4j.LoggerFactory
import utils.array
import org.apache.batik.parser.{PreserveAspectRatioHandler, PreserveAspectRatioParser}

package object svg {
	val logger = LoggerFactory.getLogger(getClass)

	def load(file: File): SVGOMDocument = {
		val reader = new FileReader(file)
		load(reader)
	}

	def loadFromString(s: String): SVGOMDocument = {
		val reader = new StringReader(s)
		load(reader)
	}

	def load(reader: Reader): SVGOMDocument = {
		val namespaceURI: String = SVGConstants.SVG_NAMESPACE_URI
		val documentElement: String = SVGConstants.SVG_SVG_TAG
		val parserClassname = XMLResourceDescriptor.getXMLParserClassName
		val f: DocumentFactory = new SAXSVGDocumentFactory(parserClassname)
		f.setValidating(false)
		f.createDocument(namespaceURI, documentElement, null, reader).asInstanceOf[SVGOMDocument]
	}

	def prepareRendering(doc: SVGOMDocument, createGvtMapping: Boolean): (BridgeContext, GraphicsNode) = {
		val userAgent = new UserAgentAdapter
		val ctx = if (doc.isSVG12) new SVG12BridgeContext(userAgent) else new BridgeContext(userAgent)
		ctx.setDynamic(createGvtMapping)
		val builder = new GVTBuilder
		var gvtRoot: GraphicsNode = builder.build(ctx, doc)
		(ctx, gvtRoot)
	}

	def render(svgDocument: SVGOMDocument, width: Int, height: Int): BufferedImage = {
		val (ctx, gvtRoot) = prepareRendering(svgDocument, createGvtMapping = false)
		val image = render(ctx, gvtRoot, width, height)
		ctx.dispose()
		image
	}

	def getAreaOfInterest(ctx: BridgeContext): Rectangle2D.Float = {
		val doc = ctx.getDocument.asInstanceOf[SVGOMDocument]
		val root = doc.getRootElement
		root.getAttributeNS(null, SVGConstants.SVG_VIEW_BOX_ATTRIBUTE) match {
			case null | "" =>
				val docWidth: Float = ctx.getDocumentSize.getWidth.asInstanceOf[Float]
				val docHeight: Float = ctx.getDocumentSize.getHeight.asInstanceOf[Float]
				logger.debug("Use document dimensions: {}x{}", docWidth, docHeight)
				new Rectangle2D.Float(0, 0, docWidth, docHeight)

			case viewBoxAttr =>
				val aspectRatioAttr = root.getAttributeNS(null, SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE)
				logger.info("Use viewBox '{}' and aspectRationAttr '{}'", viewBoxAttr, aspectRatioAttr)
				val p = ViewBox.parseViewBoxAttribute(root, viewBoxAttr, ctx)
				new Rectangle2D.Float(p(0), p(1), p(2), p(3))
		}
	}

	def getTransformation(ctx: BridgeContext, width: Int, height: Int): AffineTransform = {
		// Get document area to render and create corresponding transformation.
		// If viewBox is provided in SVG document, use it, otherwise render whole document.
		val doc = ctx.getDocument.asInstanceOf[SVGOMDocument]
		val root = doc.getRootElement
		root.getAttributeNS(null, SVGConstants.SVG_VIEW_BOX_ATTRIBUTE) match {
			case null | "" =>
				val docWidth: Float = ctx.getDocumentSize.getWidth.asInstanceOf[Float]
				val docHeight: Float = ctx.getDocumentSize.getHeight.asInstanceOf[Float]
				val scale: Float = Math.min(width / docWidth, height / docHeight)
				logger.info("Use document dimensions: {}x{}, scale: {}", array(docWidth, docHeight, scale))
				AffineTransform.getScaleInstance(scale, scale)

			case viewBoxAttr =>
				val aspectRatioAttr = root.getAttributeNS(null, SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE)
				logger.info("Use viewBox '{}' and aspectRationAttr '{}'", viewBoxAttr, aspectRatioAttr)
				ViewBox.getPreserveAspectRatioTransform(root, viewBoxAttr, aspectRatioAttr, width, height, ctx)
		}
	}

	def render(ctx: BridgeContext, gvtRoot: GraphicsNode, width: Int, height: Int): BufferedImage = {
		val transformation = getTransformation(ctx, width, height)

		val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

		// Use Batik's utility method for creating Graphics2D which properly initializes it
		// otherwise Batik may print "Graphics2D from BufferedImage lacks BUFFERED_IMAGE hint" warning
		// for some images.
		val gr = GraphicsUtil.createGraphics(image)
		gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
		gr.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
		gr.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
		gr.setTransform(transformation)
		gvtRoot.paint(gr)
		gr.dispose()

		image
	}

	def savePng(image: BufferedImage, stream: OutputStream) {
		val params = PNGEncodeParam.getDefaultEncodeParam(image)
		val pngEncoder = new PNGImageEncoder(stream, params)
		pngEncoder.encode(image)
	}

	/** Get affine transformation which maps `from` rectangle to `to` rectangle.
	  */
	def rectToRectTransformation(from: Rectangle2D, to: Rectangle2D): AffineTransform = {
		val tr = new AffineTransform

		tr.preConcatenate(AffineTransform.getTranslateInstance(-from.getX, -from.getY))
		tr.preConcatenate(AffineTransform.getScaleInstance(to.getWidth/from.getWidth, to.getHeight/from.getHeight))
		tr.preConcatenate(AffineTransform.getTranslateInstance(to.getX, to.getY))

		tr
	}

}
