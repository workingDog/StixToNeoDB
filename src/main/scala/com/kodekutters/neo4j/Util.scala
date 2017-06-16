package com.kodekutters.neo4j

import java.io.InputStream
import java.util.UUID

import com.kodekutters.stix._
import com.kodekutters.stix.Bundle
import io.circe.generic.auto._
import io.circe.parser.decode
import org.neo4j.graphdb.Label.label
import org.neo4j.graphdb.{GraphDatabaseService, Node, RelationshipType}

import scala.io.Source
import scala.language.implicitConversions
import scala.language.postfixOps


object Util {

  val objectRefs = "object_refs"
  val observedDataRefs = "observed_data_refs"
  val whereSightedRefs = "where_sighted_refs"
  val markingObjRefs = "marking_object_refs"
  val createdByRefs = "created_by"

  def apply(dbService: DbService) = new Util(dbService)

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

  /**
    * read a Bundle from the input source
    *
    * @param source the input InputStream
    * @return a Bundle option
    */
  def loadBundle(source: InputStream): Option[Bundle] = {
    // read a STIX bundle from the InputStream
    val jsondoc = Source.fromInputStream(source).mkString
    // create a bundle object from it
    decode[Bundle](jsondoc) match {
      case Left(failure) => println("-----> ERROR invalid bundle JSON in zip file: \n"); None
      case Right(bundle) => Option(bundle)
    }
  }

  // make an array of unique random id values from the input list
  def toIdArray(dataList: Option[List[Any]]): Array[String] = {
    (for (s <- dataList.getOrElse(List.empty)) yield UUID.randomUUID().toString).toArray
  }

  // make an array of id strings from the list of Identifier
  def toIdStringArray(dataList: Option[List[Identifier]]): Array[String] = {
    (for (s <- dataList.getOrElse(List.empty)) yield s.toString()).toArray
  }

  // clean the string, i.e. replace unwanted char, but not "-"
  def clean(s: String) = s.replace(",", " ").replace(":", " ").replace("\'", " ").replace(";", " ").replace("\"", "").replace("\\", "").replace("\n", "").replace("\r", "")

  // the Neo4j :LABEL and :TYPE cannot deal with "-", so clean and replace with "_"
  def asCleanLabel(s: String) = clean(s).replace("-", "_")

}

class Util(dbService: DbService) {

  import Util._

  // create the marking definition object and its relationship
  def createMarkingDef(idString: String, definition: MarkingObject, definition_id: String) = {
    val mark: String = definition match {
      case s: StatementMarking => s.statement
      case s: TPLMarking => s.tlp.value
      case _ => ""
    }
    var markObjNode: Node = null
    transaction(dbService.graphDB) {
      markObjNode = dbService.graphDB.createNode(label(Util.markingObjRefs))
      markObjNode.setProperty("marking_id", definition_id)
      markObjNode.setProperty("marking", mark)
      dbService.marking_idIndex.add(markObjNode, "marking_id", markObjNode.getProperty("marking_id"))
    }
    transaction(dbService.graphDB) {
      val sourceNode = dbService.idIndex.get("id", idString).getSingle
      sourceNode.createRelationshipTo(markObjNode, RelationshipType.withName("HAS_MARKING_OBJECT"))
    }
  }

  // create the kill_chain_phases nodes and relationships
  def createKillPhases(idString: String, kill_chain_phases: Option[List[KillChainPhase]], kill_chain_phases_ids: Array[String]) = {
    val killphases = for (s <- kill_chain_phases.getOrElse(List.empty))
      yield (s.kill_chain_name, s.phase_name, asCleanLabel(s.`type`))
    if (killphases.nonEmpty) {
      val kp = (kill_chain_phases_ids zip killphases).foreach({ case (a, (b, c, d)) =>
        transaction(dbService.graphDB) {
          val stixNode = dbService.graphDB.createNode(label(d))
          stixNode.setProperty("kill_chain_phase_id", a)
          stixNode.setProperty("kill_chain_name", b)
          stixNode.setProperty("phase_name", c)
          dbService.kill_chain_phase_idIndex.add(stixNode, "kill_chain_phase_id", stixNode.getProperty("kill_chain_phase_id"))
        }
      })
      for (k <- kill_chain_phases_ids) {
        transaction(dbService.graphDB) {
          val sourceNode = dbService.idIndex.get("id", idString).getSingle
          val targetNode = dbService.kill_chain_phase_idIndex.get("kill_chain_phase_id", k).getSingle
          sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("HAS_KILL_CHAIN_PHASE"))
        }
      }
    }
  }

  // create the external_references nodes and relationships
  def createExternRefs(idString: String, external_references: Option[List[ExternalReference]], external_references_ids: Array[String]) = {
    val externRefs = for (s <- external_references.getOrElse(List.empty))
      yield (s.source_name, s.description.getOrElse(""),
        s.url.getOrElse(""), s.external_id.getOrElse(""), asCleanLabel(s.`type`))
    if (externRefs.nonEmpty) {
      val kp = (external_references_ids zip externRefs).foreach(
        { case (a, (b, c, d, e, f)) =>
          transaction(dbService.graphDB) {
            val stixNode = dbService.graphDB.createNode(label(f))
            stixNode.setProperty("external_reference_id", a)
            stixNode.setProperty("source_name", b)
            stixNode.setProperty("description", c)
            stixNode.setProperty("url", d)
            stixNode.setProperty("external_id", e)
            dbService.external_reference_idIndex.add(stixNode, "external_reference_id", stixNode.getProperty("external_reference_id"))
          }
        }
      )
      // the external_reference relationships with the given ids
      for (k <- external_references_ids) {
        transaction(dbService.graphDB) {
          val sourceNode = dbService.idIndex.get("id", idString).getSingle
          val targetNode = dbService.external_reference_idIndex.get("external_reference_id", k).getSingle
          sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("HAS_EXTERNAL_REF"))
        }
      }
    }
  }

  // create the granular_markings nodes and relationships
  def createGranulars(idString: String, granular_markings: Option[List[GranularMarking]], granular_markings_ids: Array[String]) = {
    val granulars = for (s <- granular_markings.getOrElse(List.empty))
      yield (s.selectors.toArray, s.marking_ref.getOrElse(""), s.lang.getOrElse(""), asCleanLabel(s.`type`))
    if (granulars.nonEmpty) {
      val kp = (granular_markings_ids zip granulars).foreach(
        { case (a, (b, c, d, e)) =>
          transaction(dbService.graphDB) {
            val stixNode = dbService.graphDB.createNode(label(e))
            stixNode.setProperty("granular_marking_id", a)
            stixNode.setProperty("selectors", b)
            stixNode.setProperty("marking_ref", c)
            stixNode.setProperty("lang", d)
            dbService.granular_marking_idIndex.add(stixNode, "granular_marking_id", stixNode.getProperty("granular_marking_id"))
          }
        }
      )
      // the granular_markings relationships with the given ids
      for (k <- granular_markings_ids) {
        transaction(dbService.graphDB) {
          val sourceNode = dbService.idIndex.get("id", idString).getSingle
          val targetNode = dbService.granular_marking_idIndex.get("granular_marking_id", k).getSingle
          sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("HAS_GRANULAR_MARKING"))
        }
      }
    }
  }

  // create relations between the idString and the list of object_refs SDO id
  def createRelToObjRef(idString: String, object_refs: Option[List[Identifier]], relName: String) = {
    for (s <- object_refs.getOrElse(List.empty)) {
      transaction(dbService.graphDB) {
        val sourceNode = dbService.idIndex.get("id", idString).getSingle
        val targetNode = dbService.idIndex.get("id", s.toString()).getSingle
        sourceNode.createRelationshipTo(targetNode, RelationshipType.withName(relName))
      }
    }
  }

  def createdByRel(sourceId: String, tgtOpt: Option[Identifier]) = {
    tgtOpt.map(tgt =>
      transaction(dbService.graphDB) {
        val sourceNode = dbService.idIndex.get("id", sourceId).getSingle
        val targetNode = dbService.idIndex.get("id", tgt.toString()).getSingle
        sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("CREATED_BY"))
      })
  }

}
