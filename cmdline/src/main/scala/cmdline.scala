package net.kriomant.buketan

import java.io.File
import org.slf4j.LoggerFactory
import net.elehack.argparse4s.{ExecutionContext, Subcommand, MasterCommand}
import net.sourceforge.argparse4j.inf.ArgumentParserException
import org.apache.batik.dom.svg.SVGOMDocument
import net.kriomant.buketan.core.ResourceIntent

object cmdline {
	val logger = LoggerFactory.getLogger(getClass)

	trait GenerateResourcesSubcommand extends Subcommand {
		val file = argument[File]("svg-file")
		val resourcesDirectory = argument[File]("resources-directory").metavar("PATH")

		val qualifiers = option[String]('q', "resource-qualifiers")
			.default("")
			.metavar("RESOURCE-QUALIFIERS")
			.help("additional resource qualifiers")

		def generator: SVGOMDocument => Seq[ResourceIntent]

		def run()(implicit exc: ExecutionContext) {
			core.convert(generator, file.get, resourcesDirectory.get, ResourceQualifiers.parse(qualifiers.get))
		}
	}

	class FixedSizeResourceSubcommand(val name: String, val kind: core.ImageKind.Value) extends GenerateResourcesSubcommand {
		def generator = core.generateFixedSizedResources(kind)
	}

	object ninePatchSubcommand extends GenerateResourcesSubcommand {
		val name = "9-patch"
		def generator = core.generateNinePatchResources
	}

	object batchSubcommand extends Subcommand {
		val name = "batch"

		val sourceDirectory = argument[File]("source-directory")
		val resourcesDirectory = argument[File]("resources-directory").metavar("PATH")

		val qualifiers = option[String]('q', "resource-qualifiers")
			.default("")
			.metavar("RESOURCE-QUALIFIERS")
			.help("additional resource qualifiers")

		def run()(implicit exc: ExecutionContext) {
			core.batch(sourceDirectory.get, resourcesDirectory.get, ResourceQualifiers.parse(qualifiers.get))
		}
	}

	object Command extends MasterCommand {
		val name: String = "buketan"

		def run()(implicit exc: ExecutionContext) {
			subcommand match {
				case None => println("Usage")
				case Some(subcmd) => subcmd.run()
			}
		}

		def subcommands: Seq[Subcommand] =
			core.ImageKind.values.toSeq.map { kind => new FixedSizeResourceSubcommand(kind.toString, kind) } ++
			Seq(
				ninePatchSubcommand,
				batchSubcommand
			)
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