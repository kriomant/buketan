package net.kriomant.android_svg_res

import org.scalatest.FunSpec
import java.awt.geom.{Rectangle2D, AffineTransform}

class svgTest extends FunSpec {
//	def getTransformation(ctx: BridgeContext, width: Int, height: Int): AffineTransform = {
//		// Get document area to render and create corresponding transformation.
//		// If viewBox is provided in SVG document, use it, otherwise render whole document.
//		val doc = ctx.getDocument.asInstanceOf[SVGOMDocument]
//		val root = doc.getRootElement
//		root.getAttributeNS(null, SVGConstants.SVG_VIEW_BOX_ATTRIBUTE) match {
//			case null | "" =>
//				val docWidth: Float = ctx.getDocumentSize.getWidth.asInstanceOf[Float]
//				val docHeight: Float = ctx.getDocumentSize.getHeight.asInstanceOf[Float]
//				val scale: Float = Math.min(width / docWidth, height / docHeight)
//				logger.info("Use document dimensions: {}x{}, scale: {}", array(docWidth, docHeight, scale))
//				AffineTransform.getScaleInstance(scale, scale)
//
//			case viewBoxAttr =>
//				val aspectRatioAttr = root.getAttributeNS(null, SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE)
//				logger.info("Use viewBox '{}' and aspectRationAttr '{}'", viewBoxAttr, aspectRatioAttr)
//				ViewBox.getPreserveAspectRatioTransform(root, viewBoxAttr, aspectRatioAttr, width, height, ctx)
//		}
//	}

	describe("svg.getTransformation") {
		/** Get affine transformation which maps `from` rectangle to `to` rectangle.
		  */
		def rectToRectTransformation(from: Rectangle2D, to: Rectangle2D): AffineTransform = {
			val tr = new AffineTransform

			tr.preConcatenate(AffineTransform.getTranslateInstance(-from.getX, -from.getY))
			tr.preConcatenate(AffineTransform.getScaleInstance(to.getWidth/from.getWidth, to.getHeight/from.getHeight))
			tr.preConcatenate(AffineTransform.getTranslateInstance(to.getX, to.getY))

			tr
		}

		def getTransformation(xml: String, width: Int, height: Int): AffineTransform = {
			val doc = svg.loadFromString(xml)
			val (ctx, _) = svg.prepareRendering(doc, createGvtMapping = false)
			svg.getTransformation(ctx, width, height)
		}

		it("should use 'viewBox' document attribute") {
			val xml = """<svg
			            | xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" version="1.1"
			            | x="0px" y="0px" width="100px" height="100px" viewBox="50 0 50 50"
			            |/>
			          """.stripMargin
			assert(
				getTransformation(xml, 100, 100)
				=== rectToRectTransformation(new Rectangle2D.Float(50,0,50,50), new Rectangle2D.Float(0,0,100,100))
			)
		}

		it("should use document size if 'viewBox' if absent") {
			val xml = """<svg
			            | xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" version="1.1"
			            | x="0px" y="0px" width="50px" height="50px"
			            |/>
			          """.stripMargin
			assert(
				getTransformation(xml, 100, 100)
				=== rectToRectTransformation(new Rectangle2D.Float(0,0,50,50), new Rectangle2D.Float(0,0,100,100))
			)
		}
	}
}
