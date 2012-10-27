package net.kriomant.android_svg_res

import org.apache.batik.transcoder.image.PNGTranscoder
import org.apache.batik.transcoder.{TranscoderOutput, TranscoderInput}
import java.io.{FileOutputStream, FileInputStream}

object Main {
	def main(args: Array[String]) {
		val transcoder = new PNGTranscoder
		val input = new TranscoderInput(new FileInputStream(args(0)))
		val output = new TranscoderOutput(new FileOutputStream(args(1)))
		transcoder.transcode(input, output)
	}
}