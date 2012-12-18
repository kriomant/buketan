package net.kriomant.buketan.ant_plugin

import scala.collection.mutable
import java.io.File
import org.apache.tools.ant.{Task, BuildException}
import org.apache.tools.ant.types.FileSet

class BuketanTask extends Task {
	def setTargetDirectory(dir: File) { targetDirectory = dir }

	def addFileset(fileset: FileSet) { filesets += fileset }

	@throws(classOf[BuildException])
	override def execute() {
		if (filesets.isEmpty)
			throw new BuildException("filesets not specified")
		if (targetDirectory == null)
			throw new BuildException("target-directory not specified")

		for (
			fileset <- filesets;
			scanner = fileset.getDirectoryScanner(getProject);
			path <- scanner.getIncludedFiles
		) {
			net.kriomant.buketan.core.autoConvert(new File(scanner.getBasedir, path), targetDirectory)
		}
	}

	private[this] val filesets = mutable.Buffer[FileSet]()
	private[this] var targetDirectory: File = _
}
