package net.kriomant.android_svg_res

import org.scalatest.FunSpec
import net.kriomant.android_svg_res.core.{ResourceIntent, mapDensities, flatMapDensities}

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
}
