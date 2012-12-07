package net.kriomant.buketan

object utils {

	def array(items: Any*): Array[AnyRef] = items.map(_.asInstanceOf[AnyRef]).toArray

}