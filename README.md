# Buketan

Buketan is a tool for generation of Android image resources from SVG images.

## Purpose

Want to quickly prototype your new Android application? Just draw or find SVG file (I like http://thenounproject.com), put it into special directory and Buketan will create PNG resources and required XML files for different screen densities and Android platform versions according (well, almost) to guidelines.

Of course, creating all icons by hands is always better, but it is not always possible or needed.

## Supported resources

* action bar icons
* status bar icons (variants for pre-9, 9-10 and 11 platform versions)
* launcher icons
* list icons
* tab icons
* 9-patch resources (additional SVG markup needed)

## Installation

Unfortunately, buketan isn't uploaded to any artifactory for now, so you have to checkout source and execute `sbt publish-local` to get it working.

## Usage

Buketan currently has three frontends, see below for details:
* command line interface
* SBT plugin
* Ant task

Each of these frontends has batch processing when all SVG files from specified directory are converted into resources. You must name SVG file so that Buketan will know how to convert it.

Example name: `favorite.action-bar.svg`

All image types:
* action-bar
* notification
* launcher
* list-view
* tab
* 9 (for 9-patch images)

If you omit action then buketan will render image with intrinsic size for *mdpi* screen density and scale image appropriately for another densities.

### 9-patch images

9-patch images require additional markup: create rectangle with ID `stretch-area` and (optionally) rectangle with ID `content-area`. These rectangles may have any color or style as they will be hidden during rendering.
9-patch images are scaled like simple images without action.

## Frontends

### Command line interface

TODO

### SBT plugin

SBT plugins depends on [SBT Android plugin by jberkel](https://github.com/jberkel/android-plugin).

Just add `addSbtPlugin("net.kriomant.buketan" % "buketan-sbt" % "0.1-SNAPSHOT)` to your `project/plugins.sbt`, include `BuketanPlugin.buketanSettings` to project settings and put SVG files to `res-svg` directory next to your `res`.

See `test-sbt-project` for reference.

### Ant plugin

See `test-ant-project/build.xml` for details on integration with Android Ant build process. Basically you just need to import one file before platform's `build.xml` and another after.


