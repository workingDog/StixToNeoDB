package com.kodekutters

import com.kodekutters.neo4j.Neo4jFileLoader
import java.io.File

import scala.language.implicitConversions
import scala.language.postfixOps

/**
  * loads a file containing STIX-2 objects (in json format), or
  * a zip file of such, into a Neo4j graph database
  *
  * @author R. Wathelet June 2017, revised December 2017
  *
  */
object StixToNeoDB {

  val usage =
    """Usage:
       java -jar stixtoneodb-2.0.jar --json stix_file.json db_dir
        or
       java -jar stixtoneodb-2.0.jar --zip stix_file.zip db_dir

       the options --jsonx and --zipx can also be used for large files""".stripMargin

  /**
    * loads a file containing STIX-2 objects, or
    * a zip file containing one or more such entry files,
    * into a neo4j graph database
    */
  def main(args: Array[String]) {
    if (args.length < 2)
      println(usage)
    else {
      // the input file
      val infile = new File(args(1))
      // the Neo4j db directory
      val dbDir = if (args.length == 3) args(2).trim else ""
      // if nothing default is to create a new db in
      // the current directory with name stixdb
      val dbFile = if (dbDir.isEmpty) new java.io.File(".").getCanonicalPath + "/stixdb" else dbDir
      val neoLoader = new Neo4jFileLoader(dbFile)
      args(0) match {
        case "--json" => neoLoader.loadBundleFile(infile)
        case "--zip" => neoLoader.loadBundleZipFile(infile)
        case "--jsonx" => neoLoader.loadLargeTextFile(infile)
        case "--zipx" => neoLoader.loadLargeZipTextFile(infile)
        case x => println("unknown option: " + x + "\n" + usage)
      }
    }
  }

}
