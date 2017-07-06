package com.kodekutters.neo4j

import java.io.InputStream
import java.util.UUID

import com.kodekutters.stix._
import com.kodekutters.stix.Bundle
import org.neo4j.graphdb.Label.label
import org.neo4j.graphdb.{Node, RelationshipType}
import play.api.libs.json.Json

import scala.io.Source
import scala.language.implicitConversions
import scala.language.postfixOps

/**
  * embedded nodes and relations creation support
  */
object MakerSupport {

  import DbService._

  // convenience implicit transformation from a string to a RelationshipType
  implicit def string2relationshipType(x: String): RelationshipType = RelationshipType.withName(x)

  /**
    * convenience method for converting a CustomMap option of custom properties into a json string
    */
  def asJsonString(cust: Option[CustomProps]) = {
    cust match {
      case Some(x) => Json.stringify(Json.toJson[CustomProps](x))
      case None => ""
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
    Option(Json.parse(jsondoc)) match {
      case None => println("\n-----> could not parse JSON"); None
      case Some(js) =>
        // create a bundle object from it
        Json.fromJson[Bundle](js).asOpt match {
          case None => println("-----> ERROR invalid bundle JSON in zip file: \n"); None
          case Some(bundle) => Option(bundle)
        }
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
    val markObjNodeOpt = transaction {
      val node = DbService.graphDB.createNode(label("marking_object_refs"))
      node.setProperty("marking_id", definition_id)
      node.setProperty("marking", mark)
      node
    }
    markObjNodeOpt match {
      case Some(markObjNode) =>
        transaction {
          sourceNode.createRelationshipTo(markObjNode, "HAS_MARKING_OBJECT")
        }.getOrElse(println("---> could not process marking_object_refs relation: " + definition_id))

      case None => println("---> could not create marking_object_refs definition_id: " + definition_id)
    }
  }

  // create the kill_chain_phases nodes and relationships
  def createKillPhases(sourceNode: Node, kill_chain_phasesOpt: Option[List[KillChainPhase]], ids: Array[String]) = {
    kill_chain_phasesOpt.foreach(kill_chain_phases => {
      for ((kp, i) <- kill_chain_phases.zipWithIndex) {
        val stixNodeOpt = transaction {
          val node = DbService.graphDB.createNode(label(asCleanLabel(kp.`type`)))
          node.setProperty("kill_chain_phase_id", ids(i))
          node.setProperty("kill_chain_name", kp.kill_chain_name)
          node.setProperty("phase_name", kp.phase_name)
          node
        }
        stixNodeOpt match {
          case Some(stixNode) =>
            transaction {
              sourceNode.createRelationshipTo(stixNode, "HAS_KILL_CHAIN_PHASE")
            }.getOrElse(println("---> could not process relation: HAS_KILL_CHAIN_PHASE"))

          case None => println("---> could not create node kill_chain_phase: " + kp.toString())
        }
      }
    })
  }

  // create the external_references nodes and relationships
  def createExternRefs(idString: String, external_referencesOpt: Option[List[ExternalReference]], ids: Array[String]): Unit = {
    val sourceNodeOpt = transaction {
      DbService.idIndex.get("id", idString).getSingle
    }
    sourceNodeOpt match {
      case Some(sourceNode) => createExternRefs(sourceNode, external_referencesOpt, ids)
      case None => println("---> could not create node external_reference for: " + idString)
    }
  }

  // create the external_references nodes and relationships
  def createExternRefs(sourceNode: Node, external_referencesOpt: Option[List[ExternalReference]], ids: Array[String]): Unit = {
    external_referencesOpt.foreach(external_references => {
      for ((extRef, i) <- external_references.zipWithIndex) {
        val stixNodeOpt = transaction {
          val node = DbService.graphDB.createNode(label(asCleanLabel(extRef.`type`)))
          node.setProperty("external_reference_id", ids(i))
          node.setProperty("source_name", extRef.source_name)
          node.setProperty("description", extRef.description.getOrElse(""))
          node.setProperty("url", extRef.url.getOrElse(""))
          node.setProperty("external_id", extRef.external_id.getOrElse(""))
          node
        }
        stixNodeOpt match {
          case Some(stixNode) =>
            transaction {
              sourceNode.createRelationshipTo(stixNode, "HAS_EXTERNAL_REF")
            }.getOrElse(println("---> could not process relation: HAS_EXTERNAL_REF"))

          case None => println("---> could not create node external_reference: " + extRef.toString())
        }
      }
    })
  }

  // create the granular_markings nodes and relationships
  def createGranulars(idString: String, granular_markingsOpt: Option[List[GranularMarking]], ids: Array[String]): Unit = {
    val sourceNodeOpt = transaction {
      DbService.idIndex.get("id", idString).getSingle
    }
    sourceNodeOpt match {
      case Some(sourceNode) => createGranulars(sourceNode, granular_markingsOpt, ids)
      case None => println("---> could not create node granular_markings for: " + idString)
    }
  }

  // create the granular_markings nodes and relationships
  def createGranulars(sourceNode: Node, granular_markingsOpt: Option[List[GranularMarking]], ids: Array[String]): Unit = {
    granular_markingsOpt.foreach(granular_markings => {
      for ((gra, i) <- granular_markings.zipWithIndex) {
        val stixNodeOpt = transaction {
          val node = DbService.graphDB.createNode(label(asCleanLabel(gra.`type`)))
          node.setProperty("granular_marking_id", ids(i))
          node.setProperty("selectors", gra.selectors.toArray)
          node.setProperty("marking_ref", gra.marking_ref.getOrElse(""))
          node.setProperty("lang", gra.lang.getOrElse(""))
          node
        }
        stixNodeOpt match {
          case Some(stixNode) =>
            transaction {
              sourceNode.createRelationshipTo(stixNode, "HAS_GRANULAR_MARKING")
            }.getOrElse(println("---> could not process relation: HAS_GRANULAR_MARKING"))

          case None => println("---> could not create node granular_marking: " + gra.toString())
        }
      }
    })
  }

  // create relations between the idString and the list of object_refs SDO id
  def createRelToObjRef(idString: String, object_refs: Option[List[Identifier]], relName: String) = {
    for (s <- object_refs.getOrElse(List.empty)) {
      transaction {
        val sourceNode = DbService.idIndex.get("id", idString).getSingle
        val targetNode = DbService.idIndex.get("id", s.toString()).getSingle
        sourceNode.createRelationshipTo(targetNode, relName)
      }.getOrElse(println("---> could not process " + relName + " relation from: " + idString + " to: " + s.toString()))
    }
  }

  def createdByRel(sourceId: String, tgtOpt: Option[Identifier]) = {
    tgtOpt.map(tgt => {
      transaction {
        val sourceNode = DbService.idIndex.get("id", sourceId).getSingle
        val targetNode = DbService.idIndex.get("id", tgt.toString()).getSingle
        sourceNode.createRelationshipTo(targetNode, "CREATED_BY")
      }.getOrElse(println("---> could not process CREATED_BY relation from: " + sourceId + " to: " + tgt.toString()))
    })
  }

  def createLangContents(sourceNode: Node, contents: Map[String, Map[String, String]], ids: Map[String, String]) = {
    for ((k, obs) <- contents) {
      val obs_contents_ids: Map[String, String] = (for (s <- obs.keySet) yield s -> UUID.randomUUID().toString).toMap
      val tgtNodeOpt = transaction {
        val node = DbService.graphDB.createNode(label("contents"))
        node.setProperty("contents_id", ids(k))
        node.setProperty(k, obs_contents_ids.values.toArray)
        node
      }
      tgtNodeOpt match {
        case Some(tgtNode) =>
          createTranslations(tgtNode, obs, obs_contents_ids)
          transaction {
            sourceNode.createRelationshipTo(tgtNode, "HAS_CONTENTS")
          }.getOrElse(println("---> could not process language HAS_CONTENTS relation"))

        case None => println("---> could not create node language contents")
      }
    }
  }

  private def createTranslations(sourceNode: Node, translations: Map[String, String], ids: Map[String, String])

  = {
    for ((k, obs) <- translations) {
      val tgtNodeOpt = transaction {
        val node = DbService.graphDB.createNode(label("translations"))
        node.setProperty("translations_id", ids(k))
        node.setProperty(k, obs)
        node
      }
      tgtNodeOpt match {
        case Some(tgtNode) =>
          transaction {
            sourceNode.createRelationshipTo(tgtNode, "HAS_TRANSLATION")
          }.getOrElse(println("---> could not process language HAS_TRANSLATION relation"))

        case None => println("---> could not create node language translations")
      }
    }
  }

  // create the hashes objects and their relationship to the theNode
  def createHashes(theNode: Node, hashesOpt: Option[Map[String, String]], ids: Map[String, String]) = {
    hashesOpt.foreach(hashes =>
      for ((k, obs) <- hashes) {
        val hashNodeOpt = transaction {
          val node = DbService.graphDB.createNode(label("hashes"))
          node.setProperty("hash_id", ids(k))
          node.setProperty(k, obs)
          node
        }
        hashNodeOpt match {
          case Some(hashNode) =>
            transaction {
              theNode.createRelationshipTo(hashNode, "HAS_HASHES")
            }.getOrElse(println("---> could not process language HAS_HASHES relation"))

          case None => println("---> could not create node hashes")
        }
      }
    )
  }

}
