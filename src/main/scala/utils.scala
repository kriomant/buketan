package net.kriomant.android_svg_res

object utils {

	def array(items: Any*): Array[AnyRef] = items.map(_.asInstanceOf[AnyRef]).toArray

}