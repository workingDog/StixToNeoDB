package com.kodekutters.neo4j

import java.util.UUID

import com.kodekutters.stix._
import org.neo4j.graphdb.Label.label
import org.neo4j.graphdb.Node


/**
  * create Neo4j nodes and internal relations from a Stix object
  *
  */
class NodesMaker() {

  import MakerSupport._
  import DbService._

  // create nodes and embedded relations from a Stix object
  def createNodes(obj: StixObj) = {
    obj match {
      case stix if stix.isInstanceOf[SDO] => createSDONode(stix.asInstanceOf[SDO])
      case stix if stix.isInstanceOf[SRO] => createSRONode(stix.asInstanceOf[SRO])
      case stix if stix.isInstanceOf[StixObj] => createStixObjNode(stix.asInstanceOf[StixObj])
      case _ => // do nothing for now
    }
  }

  // create nodes and embedded relations from a SDO
  def createSDONode(x: SDO) = {
    // common elements
    val granular_markings_ids = toIdArray(x.granular_markings)
    val external_references_ids = toIdArray(x.external_references)
    // create a base node and internal relations common to all SDO
    val sdoNode: Node =
    transaction(DbService.graphDB) {
      val node = DbService.graphDB.createNode(label(asCleanLabel(x.`type`)))
      node.addLabel(label("SDO"))
      node.setProperty("id", x.id.toString())
      node.setProperty("type", x.`type`)
      node.setProperty("created", x.created.time)
      node.setProperty("modified", x.modified.time)
      node.setProperty("revoked", x.revoked.getOrElse(false))
      node.setProperty("labels", x.labels.getOrElse(List.empty).toArray)
      node.setProperty("confidence", x.confidence.getOrElse(0))
      node.setProperty("external_references", external_references_ids)
      node.setProperty("lang", x.lang.getOrElse(""))
      node.setProperty("object_marking_refs", toIdStringArray(x.object_marking_refs))
      node.setProperty("granular_markings", granular_markings_ids)
      node.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
      DbService.idIndex.add(node, "id", node.getProperty("id"))
      node
    }
    // the external_references nodes and relations
    createExternRefs(sdoNode, x.external_references, external_references_ids)
    // the granular_markings nodes and relations
    createGranulars(sdoNode, x.granular_markings, granular_markings_ids)

    x.`type` match {

      case AttackPattern.`type` =>
        val y = x.asInstanceOf[AttackPattern]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name)
          sdoNode.setProperty("description", y.description.getOrElse(""))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
        }
        createKillPhases(sdoNode, y.kill_chain_phases, kill_chain_phases_ids)

      case Identity.`type` =>
        val y = x.asInstanceOf[Identity]
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name)
          sdoNode.setProperty("identity_class", y.identity_class)
          sdoNode.setProperty("sectors", y.sectors.getOrElse(List.empty).toArray)
          sdoNode.setProperty("contact_information", y.contact_information.getOrElse(""))
          sdoNode.setProperty("description", y.description.getOrElse(""))
        }

      case Campaign.`type` =>
        val y = x.asInstanceOf[Campaign]
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name)
          sdoNode.setProperty("objective", y.objective.getOrElse(""))
          sdoNode.setProperty("aliases", y.aliases.getOrElse(List.empty).toArray)
          sdoNode.setProperty("first_seen", y.first_seen.getOrElse("").toString)
          sdoNode.setProperty("last_seen", y.last_seen.getOrElse("").toString)
          sdoNode.setProperty("description", y.description.getOrElse(""))
        }

      case CourseOfAction.`type` =>
        val y = x.asInstanceOf[CourseOfAction]
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name)
          sdoNode.setProperty("description", y.description.getOrElse(""))
        }

      case IntrusionSet.`type` =>
        val y = x.asInstanceOf[IntrusionSet]
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name)
          sdoNode.setProperty("description", y.description.getOrElse(""))
          sdoNode.setProperty("aliases", y.aliases.getOrElse(List.empty).toArray)
          sdoNode.setProperty("first_seen", y.first_seen.getOrElse("").toString)
          sdoNode.setProperty("last_seen", y.last_seen.getOrElse("").toString)
          sdoNode.setProperty("goals", y.goals.getOrElse(List.empty).toArray)
          sdoNode.setProperty("resource_level", y.resource_level.getOrElse(""))
          sdoNode.setProperty("primary_motivation", y.primary_motivation.getOrElse(""))
          sdoNode.setProperty("secondary_motivations", y.secondary_motivations.getOrElse(List.empty).toArray)
        }

      case Malware.`type` =>
        val y = x.asInstanceOf[Malware]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name)
          sdoNode.setProperty("description", y.description.getOrElse(""))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
        }
        createKillPhases(sdoNode, y.kill_chain_phases, kill_chain_phases_ids)

      case Report.`type` =>
        val y = x.asInstanceOf[Report]
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name)
          sdoNode.setProperty("published", y.published.time)
          sdoNode.setProperty("object_refs_ids", toIdStringArray(y.object_refs))
          sdoNode.setProperty("description", y.description.getOrElse(""))
        }

      case ThreatActor.`type` =>
        val y = x.asInstanceOf[ThreatActor]
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name)
          sdoNode.setProperty("description", y.description.getOrElse(""))
          sdoNode.setProperty("aliases", y.aliases.getOrElse(List.empty).toArray)
          sdoNode.setProperty("roles", y.roles.getOrElse(List.empty).toArray)
          sdoNode.setProperty("goals", y.goals.getOrElse(List.empty).toArray)
          sdoNode.setProperty("sophistication", y.sophistication.getOrElse(""))
          sdoNode.setProperty("resource_level", y.resource_level.getOrElse(""))
          sdoNode.setProperty("primary_motivation", y.primary_motivation.getOrElse(""))
          sdoNode.setProperty("secondary_motivations", y.secondary_motivations.getOrElse(List.empty).toArray)
          sdoNode.setProperty("personal_motivations", y.personal_motivations.getOrElse(List.empty).toArray)
        }

      case Tool.`type` =>
        val y = x.asInstanceOf[Tool]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name)
          sdoNode.setProperty("description", y.description.getOrElse(""))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
          sdoNode.setProperty("tool_version", y.tool_version.getOrElse(""))
        }
        createKillPhases(sdoNode, y.kill_chain_phases, kill_chain_phases_ids)

      case Vulnerability.`type` =>
        val y = x.asInstanceOf[Vulnerability]
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name)
          sdoNode.setProperty("description", y.description.getOrElse(""))
        }

      case Indicator.`type` =>
        val y = x.asInstanceOf[Indicator]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        transaction(DbService.graphDB) {
          sdoNode.setProperty("name", y.name.getOrElse(""))
          sdoNode.setProperty("description", y.description.getOrElse(""))
          sdoNode.setProperty("pattern", y.pattern)
          sdoNode.setProperty("valid_from", y.valid_from.toString())
          sdoNode.setProperty("valid_until", y.valid_until.getOrElse("").toString)
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
        }
        createKillPhases(sdoNode, y.kill_chain_phases, kill_chain_phases_ids)

      case ObservedData.`type` =>
        val y = x.asInstanceOf[ObservedData]
        val obs_ids: Map[String, String] = for (s <- y.objects) yield s._1 -> UUID.randomUUID().toString
        transaction(DbService.graphDB) {
          sdoNode.setProperty("first_observed", y.first_observed.toString())
          sdoNode.setProperty("last_observed", y.last_observed.toString())
          sdoNode.setProperty("number_observed", y.number_observed)
          sdoNode.setProperty("objects", obs_ids.values.toArray)
          sdoNode.setProperty("description", y.description.getOrElse(""))
        }
        // create the Observable objects nodes and relations for this ObservedData object
        ObservablesMaker.create(sdoNode, y.objects, obs_ids)

      case _ => // do nothing for now
    }
  }

  // create an artificial Relationship or a Sighting node so that the embedded relations can refer to it
  def createSRONode(x: SRO) = {
    transaction(DbService.graphDB) {
      val node = DbService.graphDB.createNode(label("SRO"))
      node.addLabel(label(asCleanLabel(x.`type` + "_node")))
      node.setProperty("id", x.id.toString())
      node.setProperty("type", x.`type`) // todo should this be here
      DbService.idIndex.add(node, "id", node.getProperty("id"))
    }
  }

  // create a MarkingDefinition or LanguageContent node
  def createStixObjNode(stixObj: StixObj) = {

    stixObj match {

      case x: MarkingDefinition =>
        val definition_id = UUID.randomUUID().toString
        val granular_markings_ids = toIdArray(x.granular_markings)
        val external_references_ids = toIdArray(x.external_references)
        val stixNode: Node =
        transaction(DbService.graphDB) {
          val node = DbService.graphDB.createNode(label(asCleanLabel(x.`type`)))
          node.addLabel(label("StixObj"))
          node.setProperty("id", x.id.toString())
          node.setProperty("type", x.`type`)
          node.setProperty("created", x.created.time)
          node.setProperty("definition_type", x.definition_type)
          node.setProperty("definition_id", definition_id)
          node.setProperty("external_references", external_references_ids)
          node.setProperty("object_marking_refs", toIdStringArray(x.object_marking_refs))
          node.setProperty("granular_markings", granular_markings_ids)
          node.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
          DbService.idIndex.add(node, "id", node.getProperty("id"))
          node
        }
        // the external_references
        createExternRefs(stixNode, x.external_references, external_references_ids)
        // the granular_markings
        createGranulars(stixNode, x.granular_markings, granular_markings_ids)
        // the marking object definition
        createMarkingDef(stixNode, x.definition, definition_id)

      case x: LanguageContent =>
        val granular_markings_ids = toIdArray(x.granular_markings)
        val external_references_ids = toIdArray(x.external_references)
        val lang_contents_ids: Map[String, String] = (for (s <- x.contents.keySet) yield s -> UUID.randomUUID().toString).toMap
        val stixNode: Node =
        transaction(DbService.graphDB) {
          val node = DbService.graphDB.createNode(label(asCleanLabel(x.`type`)))
          node.addLabel(label("StixObj"))
          node.setProperty("id", x.id.toString())
          node.setProperty("type", x.`type`)
          node.setProperty("created", x.created.time)
          node.setProperty("modified", x.modified.time)
          node.setProperty("object_modified", x.object_modified.time)
          node.setProperty("object_ref", x.object_ref.toString())
          node.setProperty("labels", x.labels.getOrElse(List.empty).toArray)
          node.setProperty("revoked", x.revoked.getOrElse(false))
          node.setProperty("external_references", external_references_ids)
          node.setProperty("object_marking_refs", toIdStringArray(x.object_marking_refs))
          node.setProperty("granular_markings", granular_markings_ids)
          node.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
          node.setProperty("contents", lang_contents_ids.values.toArray)
          DbService.idIndex.add(node, "id", node.getProperty("id"))
          node
        }
        // the external_references
        createExternRefs(stixNode, x.external_references, external_references_ids)
        // the granular_markings
        createGranulars(stixNode, x.granular_markings, granular_markings_ids)
        // the language contents
        createLangContents(stixNode, x.contents, lang_contents_ids)
    }
  }

}
