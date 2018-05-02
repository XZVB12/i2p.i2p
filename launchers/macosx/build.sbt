import sbtassembly.AssemblyPlugin.defaultShellScript
import sbt._
import Keys._
import sbt.io.IO
import java.io.File

lazy val i2pVersion = "0.9.34"

lazy val cleanAllTask = taskKey[Unit]("Clean up and remove the OSX bundle")
lazy val buildAppBundleTask = taskKey[Unit](s"Build an Mac OS X bundle for I2P ${i2pVersion}.")
lazy val bundleBuildPath = file("./output")

lazy val staticFiles = List(
  "blocklist.txt",
  "clients.config",
  "continents.txt",
  "countries.txt",
  "hosts.txt",
  "geoip.txt",
  "router.config",
  "webapps.config"
)

lazy val resDir = new File("./../installer/resources")
lazy val i2pBuildDir = new File("./../pkg-temp")
lazy val warsForCopy = new File(i2pBuildDir, "webapps").list.filter { f => f.endsWith(".war") }
lazy val jarsForCopy = new File(i2pBuildDir, "lib").list.filter { f => f.endsWith(".jar") }


def defaultOSXLauncherShellScript(javaOpts: Seq[String] = Seq.empty): Seq[String] = {
  val javaOptsString = javaOpts.map(_ + " ").mkString
  Seq(
    "#!/usr/bin/env sh",
    s"""
       |echo "Yo"
       |export I2P=$$HOME/Library/I2P
       |for jar in `ls $${I2P}/lib/*.jar`; do
       |  if [ ! -z $$CP ]; then
       |      CP=$${CP}:$${jar};
       |  else
       |      CP=$${jar}
       |  fi
       |done
       |export CLASSPATH=$$CP
       |exec java -jar $javaOptsString$$JAVA_OPTS "$$0" "$$@"""".stripMargin,
    "")
}

// Pointing the resources directory to the "installer" directory
resourceDirectory in Compile := baseDirectory.value / ".." / ".." / "installer" / "resources"

// Unmanaged base will be included in a fat jar
unmanagedBase in Compile := baseDirectory.value / ".." / ".." / "pkg-temp" / "lib"

// Unmanaged classpath will be available at compile time
unmanagedClasspath in Compile ++= Seq(
  baseDirectory.value / ".." / ".." / "pkg-temp" / "lib" / "*.jar"
)

assemblyOption in assembly := (assemblyOption in assembly).value.copy(
  prependShellScript = Some(defaultOSXLauncherShellScript(
    Seq(
      "-Xmx512M",
      "-Xms128m",
      "-Dwrapper.logfile=/tmp/router.log",
      "-Dwrapper.logfile.loglevel=DEBUG",
      "-Dwrapper.java.pidfile=/tmp/routerjvm.pid",
      "-Dwrapper.console.loglevel=DEBUG",
      "-Djava.awt.headless=true",
      "-Di2p.dir.base=$I2P",
      "-Djava.library.path=$I2P"
    )))
)


assemblyJarName in assembly := s"OSXLauncher"

assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp filter { c => jarsForCopy.toList.contains(c.data.getName) }
}

// TODO: MEEH: Add assemblyExcludedJars and load the router from own jar files, to handle upgrades better.
// In fact, most likely the bundle never would need an update except for the router jars/wars.

convertToICNSTask := {
  println("TODO")
}

cleanAllTask := {
  clean.value
  IO.delete(bundleBuildPath)
}

buildAppBundleTask := {
  println(s"Building Mac OS X bundle for I2P version ${i2pVersion}.")
  bundleBuildPath.mkdir()
  val paths = Map[String,File](
    "execBundlePath" -> new File(bundleBuildPath, "I2P.app/Contents/MacOS"),
    "resBundlePath" -> new File(bundleBuildPath, "I2P.app/Contents/Resources"),
    "i2pbaseBunldePath" -> new File(bundleBuildPath, "I2P.app/Contents/Resources/i2pbase"),
    "i2pJarsBunldePath" -> new File(bundleBuildPath, "I2P.app/Contents/Resources/i2pbase/lib"),
    "webappsBunldePath" -> new File(bundleBuildPath, "I2P.app/Contents/Resources/i2pbase/webapps")
  )
  paths.map { case (s,p) => p.mkdirs() }
  val dirsToCopy = List("certificates","locale","man")

  val launcherBinary = Some(assembly.value)
  launcherBinary.map { l => IO.copyFile( new File(l.toString), new File(paths.get("execBundlePath").get, "I2P") ) }


  /**
    *
    * First of, if "map" is unknown for you - shame on you :p
    *
    * It's a loop basically where it loops through a list/array
    * with the current indexed item as subject.
    *
    * The code bellow takes the different lists and
    * copy all the directories or files from the i2p.i2p build dir,
    * and into the bundle so the launcher will know where to find i2p.
    *
    */
  dirsToCopy.map  { d => IO.copyDirectory( new File(resDir, d), new File(paths.get("i2pbaseBunldePath").get, d) ) }
  warsForCopy.map { w => IO.copyFile( new File(new File(i2pBuildDir, "webapps"), w), new File(paths.get("webappsBunldePath").get, w) ) }
  jarsForCopy.map { j => IO.copyFile( new File(new File(i2pBuildDir, "lib"), j), new File(paths.get("i2pJarsBunldePath").get, j) ) }
}
