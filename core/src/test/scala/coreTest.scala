package net.kriomant.buketan

import org.scalatest.FunSpec
import net.kriomant.buketan.core.{ResourceIntent, mapDensities, flatMapDensities}
import org.apache.batik.svggen.SVGGraphics2D
import java.awt.{Color, Dimension}
import org.apache.batik.dom.svg.{SVGDOMImplementation, SVGOMDocument}
import java.awt.geom.Rectangle2D

class coreTest extends FunSpec {

	val noopRenderer: core.ResourceRenderer = core.PngRenderer(f => ???)

	describe("mapDensities") {
		it("must set `screenPixelDensity` qualifier") {
			val variants = mapDensities((0, 1, 2, 3)) { i =>
				ResourceIntent(noopRenderer, ResourceQualifiers(platformVersion = Some(i)))
			}

			assert(variants === Seq("ldpi", "mdpi", "hdpi", "xhdpi").zipWithIndex.map { case (d, i) =>
				ResourceIntent(noopRenderer, ResourceQualifiers(screenPixelDensity = Some(d), platformVersion = Some(i)))
			})
		}
	}

	describe("flatMapDensities") {
		it("must set `screenPixelDensity` qualifier") {
			val variants = flatMapDensities((0, 1, 2, 3)) { i =>
				Seq(ResourceIntent(noopRenderer, ResourceQualifiers(platformVersion = Some(i))))
			}

			assert(variants === Seq("ldpi", "mdpi", "hdpi", "xhdpi").zipWithIndex.map { case (d, i) =>
				ResourceIntent(noopRenderer, ResourceQualifiers(screenPixelDensity = Some(d), platformVersion = Some(i)))
			})
		}
	}

	describe("renderNinePatch") {
		// Prepare SVG document 32x32.
		val svgDoc = {
			val domImpl = SVGDOMImplementation.getDOMImplementation
			val doc = domImpl.createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, "svg", null).asInstanceOf[SVGOMDocument]
			val creator = new SVGGraphics2D(doc)
			creator.setSVGCanvasSize(new Dimension(32, 32))
			doc
		}

		it("must render markers of correct size") {
			// Render it.
			val (ctx, gvt) = svg.prepareRendering(svgDoc, createGvtMapping = false)
			val image = core.renderNinePatch(
				32, 32, ctx, gvt,
				new Rectangle2D.Float(0, 0, 32, 32),
				new Rectangle2D.Float(4, 6, 24, 20), Some(new Rectangle2D.Float(2, 2, 25, 25))
			)(svgDoc)

			// Now check markers.
			assert(image.getWidth === 34)
			assert(image.getHeight === 34)

			val transparent = new Color(0, 0, 0, 0).getRGB
			val black = new Color(0, 0, 0, 255).getRGB

			val firstLine = image.getData.getDataElements(0, 0, 34, 1, null).asInstanceOf[Array[Int]]
			assert(firstLine === Array.fill(5)(transparent) ++ Array.fill(24)(black) ++ Array.fill(5)(transparent))

			val firstColumn = image.getData.getDataElements(0, 0, 1, 34, null).asInstanceOf[Array[Int]]
			assert(firstColumn === Array.fill(7)(transparent) ++ Array.fill(20)(black) ++ Array.fill(7)(transparent))

			val lastLine = image.getData.getDataElements(0, 33, 34, 1, null).asInstanceOf[Array[Int]]
			assert(lastLine === Array.fill(3)(transparent) ++ Array.fill(25)(black) ++ Array.fill(6)(transparent))

			val lastColumn = image.getData.getDataElements(33, 0, 1, 34, null).asInstanceOf[Array[Int]]
			assert(lastColumn === Array.fill(3)(transparent) ++ Array.fill(25)(black) ++ Array.fill(6)(transparent))
		}

		it("must correctly scale markers") {
			// Render it.
			val (ctx, gvt) = svg.prepareRendering(svgDoc, createGvtMapping = false)
			val image = core.renderNinePatch(
				64, 64, ctx, gvt,
				new Rectangle2D.Float(0, 0, 32, 32),
				new Rectangle2D.Float(4, 6, 24, 20), Some(new Rectangle2D.Float(2, 2, 25, 25))
			)(svgDoc)

			// Now check markers.
			assert(image.getWidth === 66)
			assert(image.getHeight === 66)

			val transparent = new Color(0, 0, 0, 0).getRGB
			val black = new Color(0, 0, 0, 255).getRGB

			val firstLine = image.getData.getDataElements(0, 0, 66, 1, null).asInstanceOf[Array[Int]]
			assert(firstLine === Array.fill(9)(transparent) ++ Array.fill(48)(black) ++ Array.fill(9)(transparent))

			val firstColumn = image.getData.getDataElements(0, 0, 1, 66, null).asInstanceOf[Array[Int]]
			assert(firstColumn === Array.fill(13)(transparent) ++ Array.fill(40)(black) ++ Array.fill(13)(transparent))

			val lastLine = image.getData.getDataElements(0, 65, 66, 1, null).asInstanceOf[Array[Int]]
			assert(lastLine === Array.fill(5)(transparent) ++ Array.fill(50)(black) ++ Array.fill(11)(transparent))

			val lastColumn = image.getData.getDataElements(65, 0, 1, 66, null).asInstanceOf[Array[Int]]
			assert(lastColumn === Array.fill(5)(transparent) ++ Array.fill(50)(black) ++ Array.fill(11)(transparent))
		}
	}
}
