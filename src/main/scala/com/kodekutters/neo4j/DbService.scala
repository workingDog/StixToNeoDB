package com.kodekutters.neo4j

import java.io.File

import org.neo4j.graphdb.{GraphDatabaseService, Node}
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.index.Index

/**
  * the GraphDatabaseService support and associated index
  * @param dbDir the directory of the database
  */
class DbService(dbDir: String) {

  import NodesMaker._

  var idIndex: Index[Node] = _
  var marking_idIndex: Index[Node] = _
  var kill_chain_phase_idIndex: Index[Node] = _
  var external_reference_idIndex: Index[Node] = _
  var granular_marking_idIndex: Index[Node] = _
  var object_ref_idIndex: Index[Node] = _

  // will create a new database or open the existing one
  val graphDB: GraphDatabaseService = new GraphDatabaseFactory().newEmbeddedDatabase(new File(dbDir))
  transaction(graphDB) {
    idIndex = graphDB.index.forNodes("id")
    marking_idIndex = graphDB.index.forNodes("marking_id")
    kill_chain_phase_idIndex = graphDB.index.forNodes("kill_chain_phase_id")
    external_reference_idIndex = graphDB.index.forNodes("external_reference_id")
    granular_marking_idIndex = graphDB.index.forNodes("granular_marking_id")
    object_ref_idIndex = graphDB.index.forNodes("object_ref_id")
  }

  def closeAll() = {
    graphDB.shutdown()
  }

}
