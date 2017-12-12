package com.kodekutters

import com.kodekutters.neo4j.Neo4jLoader

import scala.language.implicitConversions
import scala.language.postfixOps

/**
  * loads a file containing STIX-2.0 (json) objects, or
  * a zip file containing one or more entry files,
  * into a neo4j graph database
  *
  * @author R. Wathelet June 2017
  *
  */
object StixToNeoDB {

  val usage =
    """Usage:
       java -jar stixtoneodb-1.0.jar --json stix_file.json db_dir
        or
       java -jar stixtoneodb-1.0.jar --zip stix_file.zip db_dir

       the options --jsonx and --zipx can also be used for large files""".stripMargin

  /**
    * loads a file containing STIX-2.x objects, or
    * a zip file containing one or more of such entry files,
    * into a neo4j graph database
    */
  def main(args: Array[String]) {
    if (args.isEmpty)
      println(usage)
    else {
      // the db location path
      val dbf = if (args.length == 3) args(2).trim else ""
      // if nothing default is current location path plus stixdb
      val dbFile = if (dbf.isEmpty) new java.io.File(".").getCanonicalPath + "/stixdb" else dbf
      println("database location: " + dbFile)
      val neoLoader = new Neo4jLoader(args(1), dbFile)
      args(0) match {
        case "--json" => neoLoader.processBundleFile()
        case "--zip" => neoLoader.processBundleZipFile()
        case "--jsonx" => neoLoader.processStixFile()
        case "--zipx" => neoLoader.processStixZipFile()
        case x => println("unknown option: " + x + "\n"); println(usage)
      }
    }
  }

}


