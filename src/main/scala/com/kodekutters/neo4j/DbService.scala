package com.kodekutters.neo4j

import java.io.File

import org.neo4j.graphdb.{GraphDatabaseService, Node}
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.index.Index

/**
  * the GraphDatabaseService support and associated index
  */
object DbService {

  var graphDB: GraphDatabaseService = _

  var idIndex: Index[Node] = _
  var marking_idIndex: Index[Node] = _
  var kill_chain_phase_idIndex: Index[Node] = _
  var external_reference_idIndex: Index[Node] = _
  var granular_marking_idIndex: Index[Node] = _
  var object_ref_idIndex: Index[Node] = _
  var observable_idIndex: Index[Node] = _
  var extension_idIndex: Index[Node] = _
  var hash_idIndex: Index[Node] = _
  var altStream_idIndex: Index[Node] = _

  // general transaction support
  // see snippet: http://sandrasi-sw.blogspot.jp/2012/02/neo4j-transactions-in-scala.html
  def transaction[A <: Any](db: GraphDatabaseService)(dbOp: => A): A = {
    val tx = db.beginTx()
    try {
      val result = dbOp
      tx.success()
      result
    } finally {
      tx.close()
    }
  }

  def closeAll() = {
    graphDB.shutdown()
  }

  /**
    * initialise this singleton
    * @param dbDir dbDir the directory of the database
    */
  def init(dbDir: String): Unit = {
    // will create a new database or open the existing one
    graphDB = new GraphDatabaseFactory().newEmbeddedDatabase(new File(dbDir))
    transaction(graphDB) {
      idIndex = graphDB.index.forNodes("id")
      marking_idIndex = graphDB.index.forNodes("marking_id")
      kill_chain_phase_idIndex = graphDB.index.forNodes("kill_chain_phase_id")
      external_reference_idIndex = graphDB.index.forNodes("external_reference_id")
      granular_marking_idIndex = graphDB.index.forNodes("granular_marking_id")
      object_ref_idIndex = graphDB.index.forNodes("object_ref_id")
      observable_idIndex = graphDB.index.forNodes("observable_id")
      extension_idIndex = graphDB.index.forNodes("extension_id")
      hash_idIndex = graphDB.index.forNodes("hash_id")
      altStream_idIndex = graphDB.index.forNodes("alternate_data_stream_id")
    }
  }

}
