package com.kodekutters.neo4j

import java.io.File

import org.neo4j.graphdb.{GraphDatabaseService, Node}
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.index.Index

import scala.util.Try

/**
  * the GraphDatabaseService support and associated index
  */
object DbService {

  var graphDB: GraphDatabaseService = _

  var idIndex: Index[Node] = _

  /**
    * initialise this singleton
    *
    * @param dbDir dbDir the directory of the database
    */
  def init(dbDir: String): Unit = {
    // will create a new database or open the existing one
    graphDB = new GraphDatabaseFactory().newEmbeddedDatabase(new File(dbDir))
    transaction {
      idIndex = graphDB.index.forNodes("id")
    }.getOrElse(println("---> could not process indexing in DbService.init()"))
  }

  // general transaction support
  // see snippet: http://sandrasi-sw.blogspot.jp/2012/02/neo4j-transactions-in-scala.html
  private def plainTransaction[A <: Any](db: GraphDatabaseService)(dbOp: => A): A = {
    val tx = synchronized {
      db.beginTx
    }
    try {
      val result = dbOp
      tx.success()
      result
    } finally {
      tx.close()
    }
  }

  /**
    * do a transaction that evaluate correctly to Some(result) or to a failure as None
    *
    * returns an Option
    */
  def transaction[A <: Any](dbOp: => A): Option[A] = Try(plainTransaction(DbService.graphDB)(dbOp)).toOption

  def closeAll() = {
    graphDB.shutdown()
  }

}
