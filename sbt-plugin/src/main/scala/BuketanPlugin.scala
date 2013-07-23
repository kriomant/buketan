import sbt._
import Keys._
import AndroidKeys._

import Project.{richInitialize,richInitializeTask}

object BuketanPlugin extends Plugin {
	val svgResourceDirectory = SettingKey[File](
		"svg-resource-directory",
		"Default directory with SVG files to render to resources"
	)

	val svgResourceDirectories = SettingKey[Seq[File]](
		"svg-resource-directories",
		"Directories with SVG files to render to resources"
	)

	val svgResources = TaskKey[Seq[File]](
		"svg-resources",
		"SVG files to render to resources"
	)

	val svgResourceTarget = TaskKey[File](
		"svg-resource-target",
		"Directory for rendered SVG resources"
	)

	val renderSvgResources = TaskKey[Seq[File]](
		"render-svg-resources",
		"Render SVG images to PNG resources"
	)

	val svgResourcesLibraryProject = TaskKey[LibraryProject](
		"svg-resources-library-project",
		"Library project with rendered SVG resources"
	)

	val createRenderedSvgResourcesDirectory = TaskKey[File](
		"create-rendered-svg-resources-directory",
		"Create rendered SVG resources directory"
	)

	val buketanSettings = inConfig(Android)(Seq(
		// SVG files should be put into "resources-svg" directory in project root.
		svgResourceDirectory <<= sourceDirectory { _ / "res-svg" },

		svgResourceDirectories := Seq(),
		svgResourceDirectories <+= svgResourceDirectory,

		includeFilter in svgResources := "*.svg",

		// List of SVG files to render.
		svgResources <<= Defaults.collectFiles(
			svgResourceDirectories,
			includeFilter in svgResources,
			excludeFilter in svgResources
		),

		createRenderedSvgResourcesDirectory <<= (target in renderSvgResources) map { dir =>
			IO.createDirectory(dir)
			dir
		},

		renderSvgResources <<= (svgResources, createRenderedSvgResourcesDirectory) map { (svgs, target) =>
			svgs.flatMap { svg =>
			  net.kriomant.buketan.core.autoConvert(svg, target)
			}
		},

		// `aaptGenerateTask` doesn't directly supports multiple resource directories, but
		// it collects resource directories from all library projects, so just create fake
		// library project.
		svgResourcesLibraryProject <<= createRenderedSvgResourcesDirectory map { dir =>
		  LibraryProject(
		    pkgName = "resources.svg",
		    manifest = dir / "AndroidManifest.xml",
		    sources = Set(),
		    resDir = Some(dir),
		    assetsDir = None
			)
	  },
		svgResourcesLibraryProject <<= svgResourcesLibraryProject.dependsOn(renderSvgResources),

		extractApkLibDependencies <+= svgResourcesLibraryProject,

		target in renderSvgResources <<= target { target => target / "resources.svg" }
	))
}
