
organization := "com.github.workingDog"

name := "stixtoneodb"

version := (version in ThisBuild).value

scalaVersion := "2.11.11"

crossScalaVersions := Seq("2.11.11")

//libraryDependencies ++= Seq(
//  "com.typesafe" % "config" % "1.3.1",
//  "org.neo4j.driver" % "neo4j-java-driver" % "1.4.0-rc1",
//  "org.neo4j" % "neo4j-kernel" % "3.2.1",
//  "org.neo4j" % "neo4j-lucene-index" % "3.2.1",
//  "org.neo4j" % "neo4j-logging" % "3.2.1" % "test",
//  "org.neo4j" % "neo4j-common" % "3.2.1" % "test",
//  "org.neo4j" % "neo4j-cypher" % "3.2.1",
//  "org.neo4j" % "neo4j-io" % "3.2.1" % "test",
//  "org.neo4j" % "neo4j-graph-algo" % "3.2.1",
//  "org.neo4j" % "neo4j-management" % "3.2.1" % "provided",
//  "org.neo4j" % "neo4j" % "3.2.1"
//)
//
//assemblyMergeStrategy in assembly := {
//  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
//  case x => MergeStrategy.first
//}

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.1",
  "org.neo4j" % "neo4j-lucene-index" % "3.2.1",
  "org.neo4j" % "neo4j-cypher" % "3.2.1",
  "org.neo4j" % "neo4j" % "3.2.1"
)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case x => val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

homepage := Some(url("https://github.com/workingDog/StixToNeoDB"))

licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

mainClass in(Compile, run) := Some("com.kodekutters.StixToNeoDB")

mainClass in assembly := Some("com.kodekutters.StixToNeoDB")

assemblyJarName in assembly := "stixtoneodb-1.0.jar"
