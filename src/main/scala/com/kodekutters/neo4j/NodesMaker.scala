package com.kodekutters.neo4j

import java.util.UUID

import com.kodekutters.stix._
import org.neo4j.graphdb.Label.label
import org.neo4j.graphdb.{GraphDatabaseService, Node, RelationshipType}

/**
  * create Neo4j nodes and internal relations from a Stix object
  *
  */
object NodesMaker {

  def apply(dbService: DbService) = new NodesMaker(dbService)

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

  // clean the string, i.e. replace unwanted char, but not "-"
  def clean(s: String) = s.replace(",", " ").replace(":", " ").replace("\'", " ").replace(";", " ").replace("\"", "").replace("\\", "").replace("\n", "").replace("\r", "")

  // make an array ([..]) of unique random id values from the input list
  def toIdArray(dataList: Option[List[Any]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'${UUID.randomUUID().toString}'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // make an array ([..]) of cleaned string values from the input list
  def toStringArray(dataList: Option[List[String]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'${clean(s)}'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // make an array ([..]) of id strings from the list of Identifier --> no cleaning done here
  def toIdStringArray(dataList: Option[List[Identifier]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'${s.toString()}'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // the Neo4j :LABEL and :TYPE cannot deal with "-", so clean and replace with "_"
  def asCleanLabel(s: String) = clean(s).replace("-", "_")

}

/**
  * create Neo4j nodes and internal relations from a Stix object
  *
  */
class NodesMaker(dbService: DbService) {

  import NodesMaker._

  // create nodes and internal relations from a Stix object
  def createNodes(obj: StixObj) = {
    obj match {
      case stix if stix.isInstanceOf[SDO] => createSDONode(stix.asInstanceOf[SDO])
      case stix if stix.isInstanceOf[SRO] => createSRONode(stix.asInstanceOf[SRO])
      case stix if stix.isInstanceOf[StixObj] => createStixObjNode(stix.asInstanceOf[StixObj])
      case _ => // do nothing for now
    }
  }

  // create nodes and internal relations from a SDO
  def createSDONode(x: SDO) = {
    // common elements
    val granular_markings_ids = toIdArray(x.granular_markings)
    val external_references_ids = toIdArray(x.external_references)
    val nodeLabel = asCleanLabel(x.`type`)

    // create a base node and internal relations common to all SDO
    def baseNode(labl: String): Node = {
      var sdoNode: Node = null
      transaction(dbService.graphDB) {
        sdoNode = dbService.graphDB.createNode(label(labl))
        sdoNode.addLabel(label("SDO"))
        sdoNode.setProperty("id", x.id.toString())
        sdoNode.setProperty("type", x.`type`)
        sdoNode.setProperty("created", x.created.time)
        sdoNode.setProperty("modified", x.modified.time)
        sdoNode.setProperty("revoked", x.revoked.getOrElse("false"))
        sdoNode.setProperty("labels", toStringArray(x.labels))
        sdoNode.setProperty("confidence", x.confidence.getOrElse(0))
        sdoNode.setProperty("external_references", external_references_ids)
        sdoNode.setProperty("lang", clean(x.lang.getOrElse("")))
        sdoNode.setProperty("object_marking_refs", toIdStringArray(x.object_marking_refs))
        sdoNode.setProperty("granular_markings", granular_markings_ids)
        sdoNode.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
        dbService.idIndex.add(sdoNode, "id", sdoNode.getProperty("id"))
      }
      // the external_references nodes and relations
      createExternRefs(x.id.toString(), x.external_references, external_references_ids)
      // the granular_markings nodes and relations
      createGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)
      // return the node
      sdoNode
    }

    x.`type` match {

      case AttackPattern.`type` =>
        val y = x.asInstanceOf[AttackPattern]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
        }
        createKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Identity.`type` =>
        val y = x.asInstanceOf[Identity]
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("identity_class", clean(y.identity_class))
          sdoNode.setProperty("sectors", toStringArray(y.sectors))
          sdoNode.setProperty("contact_information", clean(y.contact_information.getOrElse("")))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }

      case Campaign.`type` =>
        val y = x.asInstanceOf[Campaign]
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("objective", clean(y.objective.getOrElse("")))
          sdoNode.setProperty("aliases", toStringArray(y.aliases))
          sdoNode.setProperty("first_seen", clean(y.first_seen.getOrElse("").toString))
          sdoNode.setProperty("last_seen", clean(y.last_seen.getOrElse("").toString))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }

      case CourseOfAction.`type` =>
        val y = x.asInstanceOf[CourseOfAction]
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }

      case IntrusionSet.`type` =>
        val y = x.asInstanceOf[IntrusionSet]
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
          sdoNode.setProperty("aliases", toStringArray(y.aliases))
          sdoNode.setProperty("first_seen", clean(y.first_seen.getOrElse("").toString))
          sdoNode.setProperty("last_seen", clean(y.last_seen.getOrElse("").toString))
          sdoNode.setProperty("goals", toStringArray(y.goals))
          sdoNode.setProperty("resource_level", clean(y.resource_level.getOrElse("")))
          sdoNode.setProperty("primary_motivation", clean(y.primary_motivation.getOrElse("")))
          sdoNode.setProperty("secondary_motivations", toStringArray(y.secondary_motivations))
        }

      case Malware.`type` =>
        val y = x.asInstanceOf[Malware]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
        }
        createKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Report.`type` =>
        val y = x.asInstanceOf[Report]
        val object_refs_ids = toIdStringArray(y.object_refs)
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("published", y.published.time)
          sdoNode.setProperty("object_refs_ids", object_refs_ids)
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }

      case ThreatActor.`type` =>
        val y = x.asInstanceOf[ThreatActor]
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
          sdoNode.setProperty("aliases", toStringArray(y.aliases))
          sdoNode.setProperty("roles", toStringArray(y.roles))
          sdoNode.setProperty("goals", toStringArray(y.goals))
          sdoNode.setProperty("sophistication", clean(y.sophistication.getOrElse("")))
          sdoNode.setProperty("resource_level", clean(y.resource_level.getOrElse("")))
          sdoNode.setProperty("primary_motivation", clean(y.primary_motivation.getOrElse("")))
          sdoNode.setProperty("secondary_motivations", toStringArray(y.secondary_motivations))
          sdoNode.setProperty("personal_motivations", toStringArray(y.personal_motivations))
        }

      case Tool.`type` =>
        val y = x.asInstanceOf[Tool]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
          sdoNode.setProperty("tool_version", clean(y.tool_version.getOrElse("")))
        }
        createKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Vulnerability.`type` =>
        val y = x.asInstanceOf[Vulnerability]
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }

      case Indicator.`type` =>
        val y = x.asInstanceOf[Indicator]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("name", clean(y.name.getOrElse("")))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
          sdoNode.setProperty("pattern", clean(y.pattern))
          sdoNode.setProperty("valid_from", y.valid_from.toString())
          sdoNode.setProperty("valid_until", clean(y.valid_until.getOrElse("").toString))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
        }
        createKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      // todo  objects: Map[String, Observable],
      case ObservedData.`type` =>
        val y = x.asInstanceOf[ObservedData]
        val sdoNode = baseNode(nodeLabel)
        transaction(dbService.graphDB) {
          sdoNode.setProperty("first_observed", y.first_observed.toString())
          sdoNode.setProperty("last_observed", y.last_observed.toString())
          sdoNode.setProperty("number_observed", y.number_observed)
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }

      case _ => // do nothing for now
    }
  }

  // the Relationship and Sighting
  def createSRONode(x: SRO) = {
    // the external_references nodes and relations
    createExternRefs(x.id.toString(), x.external_references, toIdArray(x.external_references))
    // the granular_markings nodes and relations
    createGranulars(x.id.toString(), x.granular_markings, toIdArray(x.granular_markings))

    // a Relationship
    if (x.isInstanceOf[Relationship]) {
      val y = x.asInstanceOf[Relationship]
      // create a relationshipNode to be the source node in the object marking relations
      transaction(dbService.graphDB) {
        val relNode = dbService.graphDB.createNode(label("RelationshipNode"))
        relNode.setProperty("id", y.id.toString())
        relNode.setProperty("type", Relationship.`type`)
        dbService.idIndex.add(relNode, "id", relNode.getProperty("id"))
      }
    }
    else { // a Sighting
      val y = x.asInstanceOf[Sighting]
      // create a SightingNode to be the source node in the sighting relationship
      transaction(dbService.graphDB) {
        val sightingNode = dbService.graphDB.createNode(label("SightingNode"))
        sightingNode.setProperty("id", y.id.toString())
        sightingNode.setProperty("type", Sighting.`type`)
        dbService.idIndex.add(sightingNode, "id", sightingNode.getProperty("id"))
      }
    }
  }

  // convert MarkingDefinition and LanguageContent
  def createStixObjNode(stixObj: StixObj) = {

    stixObj match {

      case x: MarkingDefinition =>
        val definition_id = UUID.randomUUID().toString
        val granular_markings_ids = toIdArray(x.granular_markings)
        val external_references_ids = toIdArray(x.external_references)
        transaction(dbService.graphDB) {
          val stixNode = dbService.graphDB.createNode(label(asCleanLabel(x.`type`)))
          stixNode.addLabel(label("StixObj"))
          stixNode.setProperty("id", x.id.toString())
          stixNode.setProperty("type", x.`type`)
          stixNode.setProperty("created", x.created.time)
          stixNode.setProperty("definition_type", clean(x.definition_type))
          stixNode.setProperty("definition_id", definition_id)
          stixNode.setProperty("external_references", external_references_ids)
          stixNode.setProperty("object_marking_refs", toIdStringArray(x.object_marking_refs))
          stixNode.setProperty("granular_markings", granular_markings_ids)
          stixNode.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
          dbService.idIndex.add(stixNode, "id", stixNode.getProperty("id"))
        }
        // the external_references
        createExternRefs(x.id.toString(), x.external_references, external_references_ids)
        // the granular_markings
        createGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)
        // the marking object definition
        createMarkingDef(x.id.toString(), x.definition, definition_id)

      // todo <----- contents: Map[String, Map[String, String]]
      case x: LanguageContent =>
        val granular_markings_ids = toIdArray(x.granular_markings)
        val external_references_ids = toIdArray(x.external_references)
        transaction(dbService.graphDB) {
          val stixNode = dbService.graphDB.createNode(label(asCleanLabel(x.`type`)))
          stixNode.addLabel(label("StixObj"))
          stixNode.setProperty("id", x.id.toString())
          stixNode.setProperty("type", x.`type`)
          stixNode.setProperty("created", x.created.time)
          stixNode.setProperty("modified", x.modified.time)
          stixNode.setProperty("object_modified", x.object_modified.time)
          stixNode.setProperty("object_ref", x.object_ref.toString())
          stixNode.setProperty("labels", toStringArray(x.labels))
          stixNode.setProperty("revoked", x.revoked.getOrElse("false"))
          stixNode.setProperty("external_references", external_references_ids)
          stixNode.setProperty("object_marking_refs", toIdStringArray(x.object_marking_refs))
          stixNode.setProperty("granular_markings", granular_markings_ids)
          stixNode.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
          dbService.idIndex.add(stixNode, "id", stixNode.getProperty("id"))
        }
        // the external_references
        createExternRefs(x.id.toString(), x.external_references, external_references_ids)
        // the granular_markings
        createGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)
    }
  }

  //--------------------------------------------------------------------------------------------

  // create the marking definition object and its relationship
  def createMarkingDef(idString: String, definition: MarkingObject, definition_id: String) = {
    val mark: String = definition match {
      case s: StatementMarking => clean(s.statement) + ",statement"
      case s: TPLMarking => clean(s.tlp.value) + ",tlp"
      case _ => ""
    }
    var stixNode: Node = null
    transaction(dbService.graphDB) {
      stixNode = dbService.graphDB.createNode(label("marking_object_refs"))
      stixNode.setProperty("marking_id", definition_id)
      stixNode.setProperty("marking", mark)
      dbService.marking_idIndex.add(stixNode, "marking_id", stixNode.getProperty("marking_id"))
    }
    transaction(dbService.graphDB) {
      val sourceNode = dbService.idIndex.get("id", idString).getSingle
      val rel = sourceNode.createRelationshipTo(stixNode, RelationshipType.withName("HAS_MARKING_OBJECT"))
    }
  }

  // create the kill_chain_phases nodes and relationships
  def createKillPhases(idString: String, kill_chain_phases: Option[List[KillChainPhase]], kill_chain_phases_ids: String) = {
    val killphases = for (s <- kill_chain_phases.getOrElse(List.empty))
      yield (clean(s.kill_chain_name), clean(s.phase_name), asCleanLabel(s.`type`))
    if (killphases.nonEmpty) {
      val temp = kill_chain_phases_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip killphases).foreach({ case (a, (b, c, d)) =>
        transaction(dbService.graphDB) {
          val stixNode = dbService.graphDB.createNode(label(d))
          stixNode.setProperty("kill_chain_phase_id", a)
          stixNode.setProperty("kill_chain_name", b)
          stixNode.setProperty("phase_name", c)
          dbService.kill_chain_phase_idIndex.add(stixNode, "kill_chain_phase_id", stixNode.getProperty("kill_chain_phase_id"))
        }
      })
      for (k <- temp.split(",")) {
        transaction(dbService.graphDB) {
          val sourceNode = dbService.idIndex.get("id", idString).getSingle
          val targetNode = dbService.kill_chain_phase_idIndex.get("kill_chain_phase_id", k).getSingle
          val rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("HAS_KILL_CHAIN_PHASE"))
        }
      }
    }
  }

  // create the external_references nodes and relationships
  def createExternRefs(idString: String, external_references: Option[List[ExternalReference]], external_references_ids: String) = {
    val externRefs = for (s <- external_references.getOrElse(List.empty))
      yield (clean(s.source_name), clean(s.description.getOrElse("")),
        clean(s.url.getOrElse("")), clean(s.external_id.getOrElse("")), asCleanLabel(s.`type`))
    if (externRefs.nonEmpty) {
      val temp = external_references_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip externRefs).foreach(
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
      for (k <- temp.split(",")) {
        transaction(dbService.graphDB) {
          val sourceNode = dbService.idIndex.get("id", idString).getSingle
          val targetNode = dbService.external_reference_idIndex.get("external_reference_id", k).getSingle
          val rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("HAS_EXTERNAL_REF"))
        }
      }
    }
  }

  // create the granular_markings nodes and relationships
  def createGranulars(idString: String, granular_markings: Option[List[GranularMarking]], granular_markings_ids: String) = {
    val granulars = for (s <- granular_markings.getOrElse(List.empty))
      yield (toStringArray(Option(s.selectors)), clean(s.marking_ref.getOrElse("")),
        clean(s.lang.getOrElse("")), asCleanLabel(s.`type`))
    if (granulars.nonEmpty) {
      val temp = granular_markings_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip granulars).foreach(
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
      for (k <- temp.split(",")) {
        transaction(dbService.graphDB) {
          val sourceNode = dbService.idIndex.get("id", idString).getSingle
          val targetNode = dbService.granular_marking_idIndex.get("granular_marking_id", k).getSingle
          val rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("HAS_GRANULAR_MARKING"))
        }
      }
    }
  }

}
