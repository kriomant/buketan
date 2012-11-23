package net.kriomant.android_svg_res

import org.apache.batik.dom.svg.{SAXSVGDocumentFactory, SVGDOMImplementation, SVGOMDocument}
import java.io.{OutputStream, FileOutputStream, FileInputStream, File}
import java.awt.{RenderingHints, Graphics2D}
import org.apache.batik.transcoder.{TranscoderOutput, TranscoderInput}
import org.apache.batik.util.{XMLResourceDescriptor, SVGConstants}
import org.apache.batik.dom.util.DocumentFactory
import java.awt.image.BufferedImage
import org.apache.batik.ext.awt.image.codec.png.{PNGImageEncoder, PNGEncodeParam}
import org.apache.batik.bridge.{ViewBox, BridgeContext, GVTBuilder, UserAgentAdapter}
import org.apache.batik.gvt.GraphicsNode
import org.apache.batik.bridge.svg12.SVG12BridgeContext
import org.w3c.dom.svg.SVGSVGElement
import java.awt.geom.AffineTransform

package object svg {

	def load(file: File): SVGOMDocument = {
		val stream = new FileInputStream(file)
		val namespaceURI: String = SVGConstants.SVG_NAMESPACE_URI
		val documentElement: String = SVGConstants.SVG_SVG_TAG
		val parserClassname = XMLResourceDescriptor.getXMLParserClassName
		val f: DocumentFactory = new SAXSVGDocumentFactory(parserClassname)
		f.setValidating(false)
		f.createDocument(namespaceURI, documentElement, null, stream).asInstanceOf[SVGOMDocument]
	}

	def render(svgDocument: SVGOMDocument, targetFile: File, width: Int, height: Int): BufferedImage = {
		val userAgent = new UserAgentAdapter
		val ctx = if (svgDocument.isSVG12) new SVG12BridgeContext(userAgent) else new BridgeContext(userAgent)
		val builder = new GVTBuilder
		var gvtRoot: GraphicsNode = builder.build(ctx, svgDocument)
		val root: SVGSVGElement = svgDocument.getRootElement

		// Get document area to render and create corresponding transformation.
		// If viewBox is provided in SVG document, use it, otherwise render whole document.
		val transformation = root.getAttributeNS(null, SVGConstants.SVG_VIEW_BOX_ATTRIBUTE) match {
			case null | "" =>
				val docWidth: Float = ctx.getDocumentSize.getWidth.asInstanceOf[Float]
				val docHeight: Float = ctx.getDocumentSize.getHeight.asInstanceOf[Float]
				val scale: Float = Math.min(width / docWidth, height / docHeight)
				AffineTransform.getScaleInstance(scale, scale)

			case viewBoxAttr =>
				val aspectRatioAttr = root.getAttributeNS(null, SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE)
				ViewBox.getPreserveAspectRatioTransform(root, viewBoxAttr, aspectRatioAttr, width, height, ctx)
		}

		val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		val gr = image.createGraphics()
		gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
		gr.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
		gr.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
		gr.setTransform(transformation)
		gvtRoot.paint(gr)
		gr.dispose()

		ctx.dispose

		image
	}

	def savePng(image: BufferedImage, stream: OutputStream) {
		val params = PNGEncodeParam.getDefaultEncodeParam(image)
		val pngEncoder = new PNGImageEncoder(stream, params)
		pngEncoder.encode(image)
	}
}
