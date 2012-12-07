package net.kriomant.buketan

import org.scalatest.FunSpec

class ResourceQualifiersTest extends FunSpec {
	describe("ResourceQualifiers parser") {
		it("should parse empty string") {
			assert(ResourceQualifiers.parse("") === ResourceQualifiers())
		}

		it("should parse valid resource qualifiers string") {
			assert(
				ResourceQualifiers.parse("en-rUS-v7")
				=== ResourceQualifiers(_languageAndRegion = Some("en-rUS"), platformVersion = Some(7))
			)
		}

		it("should reject invalid resource qualifiers string") {
			intercept[Exception] {
				ResourceQualifiers.parse("unknown-v7")
			}
		}

		it("should not confuse country name with other qualifier prefix") {
			assert(
				ResourceQualifiers.parse("sw800dp")
				=== ResourceQualifiers(_smallestWidth = Some("sw800dp"))
			)
		}
	}

	describe("ResourceQualifiers") {
		it("should produce valid qualifiers string") {
			assert(
				ResourceQualifiers(_smallestWidth = Some("sw800dp"), screenPixelDensity = Some("mdpi")).toString
				=== "sw800dp-mdpi"
			)
		}
	}
}
