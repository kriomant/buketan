package net.kriomant.android_svg_res

import java.io.File
import org.slf4j.LoggerFactory
import net.elehack.argparse4s.{ExecutionContext, Subcommand, MasterCommand}
import net.sourceforge.argparse4j.inf.ArgumentParserException

object cmdline {
	val logger = LoggerFactory.getLogger(getClass)

	class FixedSizeResourceSubcommand(val name: String, val kind: core.ImageKind.Value) extends Subcommand {
		val file = argument[File]("svg-file")
		val resourcesDirectory = argument[File]("resources-directory").metavar("PATH")

		val qualifiers = option[String]('q', "resource-qualifiers")
			.default("")
			.metavar("RESOURCE-QUALIFIERS")
			.help("additional resource qualifiers")

		def run()(implicit exc: ExecutionContext) {
			core.convert(kind, file.get, resourcesDirectory.get, ResourceQualifiers.parse(qualifiers.get))
		}
	}

	object Command extends MasterCommand {
		val name: String = "android-svg-res"

		def run()(implicit exc: ExecutionContext) {
			subcommand match {
				case None => println("Usage")
				case Some(subcmd) => subcmd.run()
			}
		}

		def subcommands: Seq[Subcommand] = core.ImageKind.values.toSeq.map { kind =>
			new FixedSizeResourceSubcommand(kind.toString, kind)
		}
	}

	def main(args: Array[String]) {
		val exitCode = try {
			Command.run(args)
			0
		} catch {
			case e: ArgumentParserException =>
				System.err.println("%s\nUse '--help' for help." format e.getMessage)
				1
		}
		System.exit(exitCode)
	}

}