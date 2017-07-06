
organization := "com.github.workingDog"

name := "stixtoneodb"

version := (version in ThisBuild).value

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "org.neo4j" % "neo4j" % "3.2.1",
  "com.github.workingDog" %% "scalastix" % "0.3"
)

assemblyMergeStrategy in assembly := {
  case PathList(xs @_*) if xs.last.toLowerCase endsWith ".dsa" => MergeStrategy.discard
  case PathList(xs @_*) if xs.last.toLowerCase endsWith ".sf" => MergeStrategy.discard
  case PathList(xs @_*) if xs.last.toLowerCase endsWith ".des" => MergeStrategy.discard
  case PathList(xs @_*) if xs.last endsWith "LICENSES.txt"=> MergeStrategy.discard
  case x => val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

homepage := Some(url("https://github.com/workingDog/StixToNeoDB"))

licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

mainClass in(Compile, run) := Some("com.kodekutters.StixToNeoDB")

mainClass in assembly := Some("com.kodekutters.StixToNeoDB")

assemblyJarName in assembly := "stixtoneodb-1.0.jar"
