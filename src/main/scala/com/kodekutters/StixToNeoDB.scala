package com.kodekutters

import com.kodekutters.neo4j.Neo4jFileLoader
import java.io.File

import com.typesafe.scalalogging.Logger
import org.slf4j.helpers.NOPLogger

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
       java -jar stixtoneodb-6.0.jar -f hostAddress stix_file db_dir
       or
       java -jar stixtoneodb-6.0.jar -x hostAddress stix_file db_dir""".stripMargin

  /**
    * loads a file containing STIX-2 objects, or
    * a zip file containing one or more such entry files,
    * into a neo4j graph database
    */
  def main(args: Array[String]) {
    if (args.length < 3)
      println(usage)
    else {
      // to log the progress, comment this line if you don't want any logging at all
      implicit val logger = Logger("StixToNeoDB")
      // the Neo4j hostname
      val hostAddress = args(1).trim
      // the input file
      val infile = new File(args(2))
      // the Neo4j db directory
      val dbDir = if (args.length == 4) args(3).trim else ""
      // if nothing default is to create a new db in
      // the current directory with name stixdb
      val dbFile = if (dbDir.isEmpty) new java.io.File(".").getCanonicalPath + "/stixdb" else dbDir
      // Neo4jLoader and Neo4jFileLoader can take an implicit logger, default NOPLogger
      val neoLoader = new Neo4jFileLoader(dbFile, hostAddress)
      args(0) match {
        case "-f" =>
          if (infile.getName.endsWith(".zip")) neoLoader.loadBundleZipFile(infile)
          else neoLoader.loadBundleFile(infile)
        case "-x" =>
          if (infile.getName.endsWith(".zip")) neoLoader.loadLargeZipTextFile(infile)
          else neoLoader.loadLargeTextFile(infile)
        case x => println("unknown option: " + x + "\n" + usage)
      }
    }
  }

}
