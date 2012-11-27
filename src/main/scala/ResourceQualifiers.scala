package net.kriomant.android_svg_res

import util.parsing.combinator.RegexParsers

// All fields are detected, but fields I am not interested in for now are
// prefixed with underscore and are represented as plain strings.
// Fields interesting to me are parsed into integers, enumerations, etc.
case class ResourceQualifiers(
	_mobileCountryAndNetworkCodes: Option[String] = None,
	_languageAndRegion: Option[String] = None,
	_layoutDirection: Option[String] = None,
	_smallestWidth: Option[String] = None,
	_availableWidth: Option[String] = None,
	_availableHeight: Option[String] = None,
	_screenSize: Option[String] = None,
	_screenAspect: Option[String] = None,
	_screenOrientation: Option[String] = None,
	_uiMode: Option[String] = None,
	_nightMode: Option[String] = None,
	screenPixelDensity: Option[String] = None,
	_touchscreenType: Option[String] = None,
	_keyboardAvailability: Option[String] = None,
	_primaryTextInputMethod: Option[String] = None,
	_navigationKeyAvailability: Option[String] = None,
	_primaryNonTouchNavigationMethod: Option[String] = None,
	platformVersion: Option[Int] = None
) {
	override def toString = Seq(
		_mobileCountryAndNetworkCodes,
		_languageAndRegion,
		_layoutDirection,
		_smallestWidth,
		_availableWidth,
		_availableHeight,
		_screenSize,
		_screenAspect,
		_screenOrientation,
		_uiMode,
		_nightMode,
		screenPixelDensity,
		_touchscreenType,
		_keyboardAvailability,
		_primaryTextInputMethod,
		_navigationKeyAvailability,
		_primaryNonTouchNavigationMethod,
		platformVersion map {"v" + _}
	).flatten.mkString("-")
	
	def update(q: ResourceQualifiers): ResourceQualifiers = {
		ResourceQualifiers(
			q._mobileCountryAndNetworkCodes orElse _mobileCountryAndNetworkCodes,
			q._languageAndRegion orElse _languageAndRegion,
			q._layoutDirection orElse _layoutDirection,
			q._smallestWidth orElse _smallestWidth,
			q._availableWidth orElse _availableWidth,
			q._availableHeight orElse _availableHeight,
			q._screenSize orElse _screenSize,
			q._screenAspect orElse _screenAspect,
			q._screenOrientation orElse _screenOrientation,
			q._uiMode orElse _uiMode,
			q._nightMode orElse _nightMode,
			q.screenPixelDensity orElse screenPixelDensity,
			q._touchscreenType orElse _touchscreenType,
			q._keyboardAvailability orElse _keyboardAvailability,
			q._primaryTextInputMethod orElse _primaryTextInputMethod,
			q._navigationKeyAvailability orElse _navigationKeyAvailability,
			q._primaryNonTouchNavigationMethod orElse _primaryNonTouchNavigationMethod,
			q.platformVersion orElse platformVersion
		)
	}
}

object ResourceQualifiers {
	object P extends RegexParsers {
		// Helpers.
		def digits = "\\d+".r
		def natural = digits ^^ (_.toInt)
		def eof = """\z""".r

		// Qualifiers.
		def _mobileCountryAndNetworkCodes = """(?i)mcc\d+(-mnc\d+)?""".r
		def _languageAndRegion = """(?i)[a-z]{2}(-r[a-z]{2})?""".r
		def _layoutDirection = """(?i)ld(rtl|ltr)""".r
		def _smallestWidth = """(?i)sw\d+dp""".r
		def _availableWidth = """(?i)w\d+dp""".r
		def _availableHeight = """(?i)h\d+dp""".r
		def _screenSize = """(?i)small|normal|large|xlarge""".r
		def _screenAspect = """(?i)long|notlong""".r
		def _screenOrientation = """(?i)port|land""".r
		def _uiMode = """(?i)car|desk|television|appliance""".r
		def _nightMode = """(?i)night|nothight""".r
		def screenPixelDensity = """(?i)ldpi|mdpi|hdpi|xhdpi|nodpi|tvdpi""".r
		def _touchscreenType = """(?i)notouch|finger""".r
		def _keyboardAvailability = """(?i)keysexposed|keyshidden|keyssoft""".r
		def _primaryTextInputMethod = """(?i)nokeys|qwerty|12key""".r
		def _navigationKeyAvailability = """(?i)navexposed|navhidden""".r
		def _primaryNonTouchNavigationMethod = """(?i)nonav|dpad|trackball|wheel""".r
		def platformVersion = "v" ~> natural

		def dashed[T](p: Parser[T]): Parser[Option[T]] = opt(p <~ ("-" | eof))

		def qualifiers: Parser[ResourceQualifiers] = (
			dashed(_mobileCountryAndNetworkCodes) ~
			dashed(_languageAndRegion) ~
			dashed(_layoutDirection) ~
			dashed(_smallestWidth) ~
			dashed(_availableWidth) ~
			dashed(_availableHeight) ~
			dashed(_screenSize) ~
			dashed(_screenAspect) ~
			dashed(_screenOrientation) ~
			dashed(_uiMode) ~
			dashed(_nightMode) ~
			dashed(screenPixelDensity) ~
			dashed(_touchscreenType) ~
			dashed(_keyboardAvailability) ~
			dashed(_primaryTextInputMethod) ~
			dashed(_navigationKeyAvailability) ~
			dashed(_primaryNonTouchNavigationMethod) ~
			dashed(platformVersion)
		) ^^ {
			case country~lang~dir~sw~aw~ah~size~aspect~orient~mode~night~density~touch~keyb~input~nav~nontouch~version =>
				ResourceQualifiers(country, lang, dir, sw, aw, ah, size, aspect, orient, mode, night, density, touch, keyb, input, nav, nontouch, version)
		}

		def parse(s: String): ResourceQualifiers = {
			parseAll(qualifiers, s) match {
				case Success(q, _) => q
				case err: NoSuccess => throw new Exception(s"Invalid resource qualifiers: ${err.msg}")
			}
		}
	}

	// We need to parse resource qualifiers in order to get target platform version
	// and inject screen density qualifier.
	// http://developer.android.com/guide/topics/resources/providing-resources.html#table2
	def parse(s: String) = P.parse(s)

	val empty = ResourceQualifiers()
}
