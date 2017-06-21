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
  def createMarkingDef(sourceNode: Node, definition: MarkingObject, definition_id: String) = {
    val mark: String = definition match {
      case s: StatementMarking => s.statement
      case s: TPLMarking => s.tlp.value
      case _ => ""
    }
    val markObjNode: Node =
      transaction(DbService.graphDB) {
        val node = DbService.graphDB.createNode(label(MakerSupport.markingObjRefs))
        node.setProperty("marking_id", definition_id)
        node.setProperty("marking", mark)
        DbService.marking_idIndex.add(node, "marking_id", node.getProperty("marking_id"))
        node
      }
    transaction(DbService.graphDB) {
      sourceNode.createRelationshipTo(markObjNode, RelationshipType.withName("HAS_MARKING_OBJECT"))
    }
  }

  // create the kill_chain_phases nodes and relationships
  def createKillPhases(sourceNode: Node, kill_chain_phasesOpt: Option[List[KillChainPhase]], ids: Array[String]) = {
    kill_chain_phasesOpt.foreach(kill_chain_phases => {
      for ((kp, i) <- kill_chain_phases.zipWithIndex) {
        val stixNode: Node =
          transaction(DbService.graphDB) {
            val node = DbService.graphDB.createNode(label(asCleanLabel(kp.`type`)))
            node.setProperty("kill_chain_phase_id", ids(i))
            node.setProperty("kill_chain_name", kp.kill_chain_name)
            node.setProperty("phase_name", kp.phase_name)
            DbService.kill_chain_phase_idIndex.add(node, "kill_chain_phase_id", node.getProperty("kill_chain_phase_id"))
            node
          }
        transaction(DbService.graphDB) {
          sourceNode.createRelationshipTo(stixNode, RelationshipType.withName("HAS_KILL_CHAIN_PHASE"))
        }
      }
    })
  }

  // create the external_references nodes and relationships
  def createExternRefs(idString: String, external_referencesOpt: Option[List[ExternalReference]], ids: Array[String]): Unit = {
    val sourceNode: Node =
      transaction(DbService.graphDB) {
        DbService.idIndex.get("id", idString).getSingle
      }
    createExternRefs(sourceNode, external_referencesOpt, ids)
  }

  // create the external_references nodes and relationships
  def createExternRefs(sourceNode: Node, external_referencesOpt: Option[List[ExternalReference]], ids: Array[String]): Unit = {
    external_referencesOpt.foreach(external_references => {
      for ((extRef, i) <- external_references.zipWithIndex) {
        val stixNode: Node =
          transaction(DbService.graphDB) {
            val node = DbService.graphDB.createNode(label(asCleanLabel(extRef.`type`)))
            node.setProperty("external_reference_id", ids(i))
            node.setProperty("source_name", extRef.source_name)
            node.setProperty("description", extRef.description.getOrElse(""))
            node.setProperty("url", extRef.url.getOrElse(""))
            node.setProperty("external_id", extRef.external_id.getOrElse(""))
            DbService.external_reference_idIndex.add(node, "external_reference_id", node.getProperty("external_reference_id"))
            node
          }
        transaction(DbService.graphDB) {
          sourceNode.createRelationshipTo(stixNode, RelationshipType.withName("HAS_EXTERNAL_REF"))
        }
      }
    })
  }

  // create the granular_markings nodes and relationships
  def createGranulars(idString: String, granular_markingsOpt: Option[List[GranularMarking]], ids: Array[String]): Unit = {
    val sourceNode: Node =
      transaction(DbService.graphDB) {
        DbService.idIndex.get("id", idString).getSingle
      }
    createGranulars(sourceNode, granular_markingsOpt, ids)
  }

  // create the granular_markings nodes and relationships
  def createGranulars(sourceNode: Node, granular_markingsOpt: Option[List[GranularMarking]], ids: Array[String]): Unit = {
    granular_markingsOpt.foreach(granular_markings => {
      for ((gra, i) <- granular_markings.zipWithIndex) {
        val stixNode: Node =
          transaction(DbService.graphDB) {
            val node = DbService.graphDB.createNode(label(asCleanLabel(gra.`type`)))
            node.setProperty("granular_marking_id", ids(i))
            node.setProperty("selectors", gra.selectors.toArray)
            node.setProperty("marking_ref", gra.marking_ref.getOrElse(""))
            node.setProperty("lang", gra.lang.getOrElse(""))
            DbService.granular_marking_idIndex.add(node, "granular_marking_id", node.getProperty("granular_marking_id"))
            node
          }
        transaction(DbService.graphDB) {
          sourceNode.createRelationshipTo(stixNode, RelationshipType.withName("HAS_GRANULAR_MARKING"))
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

  def createLangContents(sourceNode: Node, contents: Map[String, Map[String, String]], ids: Map[String, String]) = {
    for ((k, obs) <- contents) {
      val obs_contents_ids: Map[String, String] = (for (s <- obs.keySet) yield s -> UUID.randomUUID().toString).toMap
      val tgtNode: Node =
        transaction(DbService.graphDB) {
          val node = DbService.graphDB.createNode(label("contents"))
          node.setProperty("contents_id", ids(k))
          node.setProperty(k, obs_contents_ids.values.toArray)
          DbService.contents_idIndex.add(node, "contents_id", node.getProperty("contents_id"))
          node
        }
      createTranslations(tgtNode, obs, obs_contents_ids)
      transaction(DbService.graphDB) {
        sourceNode.createRelationshipTo(tgtNode, RelationshipType.withName("HAS_CONTENTS"))
      }
    }
  }

  private def createTranslations(sourceNode: Node, translations: Map[String, String], ids: Map[String, String]) = {
    for ((k, obs) <- translations) {
      val tgtNode: Node =
        transaction(DbService.graphDB) {
          val node = DbService.graphDB.createNode(label("translations"))
          node.setProperty("translations_id", ids(k))
          node.setProperty(k, obs)
          DbService.translations_idIndex.add(node, "translations_id", node.getProperty("translations_id"))
          node
        }
      transaction(DbService.graphDB) {
        sourceNode.createRelationshipTo(tgtNode, RelationshipType.withName("HAS_TRANSLATION"))
      }
    }
  }

  // create the hashes objects and their relationship to the theNode
  def createHashes(theNode: Node, hashesOpt: Option[Map[String, String]], ids: Map[String, String]) = {
    hashesOpt.foreach(hashes =>
      for ((k, obs) <- hashes) {
        val hashNode: Node =
          transaction(DbService.graphDB) {
            val node = DbService.graphDB.createNode(label("hashes"))
            node.setProperty("hash_id", ids(k))
            node.setProperty(k, obs)
            DbService.hash_idIndex.add(node, "hash_id", node.getProperty("hash_id"))
            node
          }
        transaction(DbService.graphDB) {
          theNode.createRelationshipTo(hashNode, RelationshipType.withName("HAS_HASHES"))
        }
      }
    )
  }

}
