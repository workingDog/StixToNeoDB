
organization := "com.github.workingDog"

name := "stixtoneodb"

version := (version in ThisBuild).value

scalaVersion := "2.13.3"

libraryDependencies ++= Seq("com.github.workingDog" %% "stixtoneolib" % "0.6")

test in assembly := {}

assemblyMergeStrategy in assembly := {
  case "module-info.class" => MergeStrategy.discard
  case PathList(xs @_*) if xs.last.toLowerCase endsWith ".rsa" => MergeStrategy.discard
  case PathList(xs@_*) if xs.last.toLowerCase endsWith ".dsa" => MergeStrategy.discard
  case PathList(xs@_*) if xs.last.toLowerCase endsWith ".sf" => MergeStrategy.discard
  case PathList(xs@_*) if xs.last.toLowerCase endsWith ".des" => MergeStrategy.discard
  case PathList(xs@_*) if xs.last endsWith "LICENSES.txt" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

homepage := Some(url("https://github.com/workingDog/StixToNeoDB"))

licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

mainClass in(Compile, run) := Some("com.kodekutters.StixToNeoDB")

mainClass in assembly := Some("com.kodekutters.StixToNeoDB")

assemblyJarName in assembly := "stixtoneodb-" + version.value + ".jar"
