package com.kodekutters.neo4j

import java.io.InputStream
import java.util.UUID

import com.kodekutters.stix._
import com.kodekutters.stix.Bundle
import io.circe.generic.auto._
import io.circe.parser.decode
import org.neo4j.graphdb.Label.label
import org.neo4j.graphdb.{Node, RelationshipType}

import scala.io.Source
import scala.language.implicitConversions
import scala.language.postfixOps

/**
  * embedded nodes and relations creation support
  */
object MakerSupport {

  import DbService._

  val objectRefs = "object_refs"
  val observedDataRefs = "observed_data_refs"
  val whereSightedRefs = "where_sighted_refs"
  val markingObjRefs = "marking_object_refs"
  val createdByRefs = "created_by"
  val observable_id = "observable_id"
  val hash_id = "hash_id"

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

  // clean the string, i.e. escape special char
  //  def clean(s: String) = s.replace("\\", """\\""").replace("'", """\'""").
  //    replace("\"", """\""").replace("\n", """\n""").replace("\r", """\r""").
  //    replace("\b", """\b""").replace("\f", """\f""").replace("\t", """\t""")

  // the Neo4j :LABEL and :TYPE cannot deal with "-", so clean and replace with "_"
  def asCleanLabel(s: String) = s.replace(",", " ").replace(":", " ").replace("\'", " ").
    replace(";", " ").replace("\"", "").replace("\\", "").
    replace("\n", "").replace("\r", "").replace("-", "_")

  // create the marking definition node and its relationship
  def createMarkingDef(idString: String, definition: MarkingObject, definition_id: String) = {
    val mark: String = definition match {
      case s: StatementMarking => s.statement
      case s: TPLMarking => s.tlp.value
      case _ => ""
    }
    var markObjNode: Node = null
    transaction(DbService.graphDB) {
      markObjNode = DbService.graphDB.createNode(label(MakerSupport.markingObjRefs))
      markObjNode.setProperty("marking_id", definition_id)
      markObjNode.setProperty("marking", mark)
      DbService.marking_idIndex.add(markObjNode, "marking_id", markObjNode.getProperty("marking_id"))
    }
    transaction(DbService.graphDB) {
      val sourceNode = DbService.idIndex.get("id", idString).getSingle
      sourceNode.createRelationshipTo(markObjNode, RelationshipType.withName("HAS_MARKING_OBJECT"))
    }
  }

  // create the kill_chain_phases nodes and relationships
  def createKillPhases(idString: String, kill_chain_phasesOpt: Option[List[KillChainPhase]], ids: Array[String]) = {
    kill_chain_phasesOpt.foreach(kill_chain_phases => {
      for ((kp, i) <- kill_chain_phases.zipWithIndex) {
        var stixNode: Node = null
        transaction(DbService.graphDB) {
          stixNode = DbService.graphDB.createNode(label(asCleanLabel(kp.`type`)))
          stixNode.setProperty("kill_chain_phase_id", ids(i))
          stixNode.setProperty("kill_chain_name", kp.kill_chain_name)
          stixNode.setProperty("phase_name", kp.phase_name)
          DbService.kill_chain_phase_idIndex.add(stixNode, "kill_chain_phase_id", stixNode.getProperty("kill_chain_phase_id"))
        }
        transaction(DbService.graphDB) {
          val sourceNode = DbService.idIndex.get("id", idString).getSingle
          sourceNode.createRelationshipTo(stixNode, RelationshipType.withName("HAS_KILL_CHAIN_PHASE"))
        }
      }
    })
  }

  // create the external_references nodes and relationships
  def createExternRefs(idString: String, external_referencesOpt: Option[List[ExternalReference]], ids: Array[String]) = {
    external_referencesOpt.foreach(external_references => {
      for ((extRef, i) <- external_references.zipWithIndex) {
        var stixNode: Node = null
        transaction(DbService.graphDB) {
          stixNode = DbService.graphDB.createNode(label(asCleanLabel(extRef.`type`)))
          stixNode.setProperty("external_reference_id", ids(i))
          stixNode.setProperty("source_name", extRef.source_name)
          stixNode.setProperty("description", extRef.description.getOrElse(""))
          stixNode.setProperty("url", extRef.url.getOrElse(""))
          stixNode.setProperty("external_id", extRef.external_id.getOrElse(""))
          DbService.external_reference_idIndex.add(stixNode, "external_reference_id", stixNode.getProperty("external_reference_id"))
        }
        transaction(DbService.graphDB) {
          val sourceNode = DbService.idIndex.get("id", idString).getSingle
          sourceNode.createRelationshipTo(stixNode, RelationshipType.withName("HAS_EXTERNAL_REF"))
        }
      }
    })
  }

  // create the granular_markings nodes and relationships
  def createGranulars(idString: String, granular_markingsOpt: Option[List[GranularMarking]], ids: Array[String]) = {
    granular_markingsOpt.foreach(granular_markings => {
      for ((gra, i) <- granular_markings.zipWithIndex) {
        var stixNode: Node = null
        transaction(DbService.graphDB) {
          stixNode = DbService.graphDB.createNode(label(asCleanLabel(gra.`type`)))
          stixNode.setProperty("granular_marking_id", ids(i))
          stixNode.setProperty("selectors", gra.selectors.toArray)
          stixNode.setProperty("marking_ref", gra.marking_ref.getOrElse(""))
          stixNode.setProperty("lang", gra.lang.getOrElse(""))
          DbService.granular_marking_idIndex.add(stixNode, "granular_marking_id", stixNode.getProperty("granular_marking_id"))
        }
        transaction(DbService.graphDB) {
          val sourceNode = DbService.idIndex.get("id", idString).getSingle
          sourceNode.createRelationshipTo(stixNode, RelationshipType.withName("HAS_EXTERNAL_REF"))
        }
      }
    })
  }

  // create relations between the idString and the list of object_refs SDO id
  def createRelToObjRef(idString: String, object_refs: Option[List[Identifier]], relName: String) = {
    for (s <- object_refs.getOrElse(List.empty)) {
      transaction(DbService.graphDB) {
        val sourceNode = DbService.idIndex.get("id", idString).getSingle
        val targetNode = DbService.idIndex.get("id", s.toString()).getSingle
        sourceNode.createRelationshipTo(targetNode, RelationshipType.withName(relName))
      }
    }
  }

  def createdByRel(sourceId: String, tgtOpt: Option[Identifier]) = {
    tgtOpt.map(tgt =>
      transaction(DbService.graphDB) {
        val sourceNode = DbService.idIndex.get("id", sourceId).getSingle
        val targetNode = DbService.idIndex.get("id", tgt.toString()).getSingle
        sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("CREATED_BY"))
      })
  }

}
