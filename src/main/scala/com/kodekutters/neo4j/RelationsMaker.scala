package com.kodekutters.neo4j

import com.kodekutters.stix._
import org.neo4j.graphdb.RelationshipType


/**
  * create Neo4j relations from a Stix object
  *
  */
object RelationsMaker {

  def apply(dbService: DbService) = new RelationsMaker(dbService)

}

/**
  * create Neo4j relations from a Stix object
  *
  * @param dbService
  */
class RelationsMaker(dbService: DbService) {

  import NodesMaker._

  // create relations from the stix object
  def createRelations(obj: StixObj) = {
    obj match {
      case stix if stix.isInstanceOf[SDO] => createSDORel(stix.asInstanceOf[SDO])
      case stix if stix.isInstanceOf[SRO] => createSRORel(stix.asInstanceOf[SRO])
      case stix if stix.isInstanceOf[StixObj] => createStixObjRel(stix.asInstanceOf[StixObj])
      case _ => // do nothing for now
    }
  }

  // create relations (to other SDO, Marking etc...) for the input SDO
  def createSDORel(x: SDO) = {
    // the object marking relations
    createRelToObjRef(x.id.toString(), x.object_marking_refs, "HAS_MARKING")
    // the created_by relation
    createdByRel(x.id.toString(), x.created_by_ref)

    x.`type` match {

      case Report.`type` =>
        val y = x.asInstanceOf[Report]
        // create relations between the Report id and the list of object_refs SDO id
        createRelToObjRef(y.id.toString(), y.object_refs, "REFERS_TO")

      // todo  objects: Map[String, Observable],
      //  case ObservedData.`type` =>
      //    val y = x.asInstanceOf[ObservedData]

      case _ => // do nothing more
    }
  }

  // create the Relationship and Sighting
  def createSRORel(x: SRO) = {
    def baseRel(sourceId: String, targetId: String, name: String): org.neo4j.graphdb.Relationship = {
      var rel: org.neo4j.graphdb.Relationship = null
      transaction(dbService.graphDB) {
        val sourceNode = dbService.idIndex.get("id", sourceId).getSingle
        val targetNode = dbService.idIndex.get("id", targetId).getSingle
        rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName(name))
        rel.setProperty("id", x.id.toString())
        rel.setProperty("type", x.`type`)
        rel.setProperty("created", x.created.time)
        rel.setProperty("modified", x.modified.time)
        rel.setProperty("revoked", x.revoked.getOrElse(false))
        rel.setProperty("labels", toStringArray(x.labels))
        rel.setProperty("confidence", x.confidence.getOrElse(0))
        rel.setProperty("external_references", toIdArray(x.external_references))
        rel.setProperty("lang", clean(x.lang.getOrElse("")))
        rel.setProperty("object_marking_refs", toIdStringArray(x.object_marking_refs))
        rel.setProperty("granular_markings", toIdArray(x.granular_markings))
        rel.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
      }
      // the object marking relations
      createRelToObjRef(x.id.toString(), x.object_marking_refs, "HAS_MARKING")
      // the created_by relation
      createdByRel(x.id.toString(), x.created_by_ref)
      // the relation
      rel
    }

    // a Relationsip
    if (x.isInstanceOf[Relationship]) {
      val y = x.asInstanceOf[Relationship]
      val rel = baseRel(y.source_ref.toString(), y.target_ref.toString(), asCleanLabel(y.relationship_type))
      transaction(dbService.graphDB) {
        rel.setProperty("source_ref", y.source_ref.toString())
        rel.setProperty("target_ref", y.target_ref.toString())
        rel.setProperty("relationship_type", asCleanLabel(y.relationship_type))
        rel.setProperty("description", clean(y.description.getOrElse("")))
      }
    }
    else { // a Sighting
      val y = x.asInstanceOf[Sighting]
      val rel = baseRel(y.id.toString(), y.sighting_of_ref.toString(), asCleanLabel(Sighting.`type`))
      transaction(dbService.graphDB) {
        rel.setProperty("sighting_of_ref", y.sighting_of_ref.toString())
        rel.setProperty("first_seen", y.first_seen.getOrElse("").toString)
        rel.setProperty("last_seen", y.last_seen.getOrElse("").toString)
        rel.setProperty("count", y.count.getOrElse(0))
        rel.setProperty("summary", y.summary.getOrElse(""))
        rel.setProperty("observed_data_id", toIdStringArray(y.observed_data_refs))
        rel.setProperty("where_sighted_refs_id", toIdStringArray(y.where_sighted_refs))
        rel.setProperty("description", clean(y.description.getOrElse("")))
      }
      // create relations between the sighting (SightingNode id) and the list of observed_data_refs ObservedData SDO id
      createRelToObjRef(y.id.toString(), y.observed_data_refs, "SIGHTED_OBSERVED_DATA")
      // create relations between the sighting (SightingNode id) and the list of where_sighted SDO id
      createRelToObjRef(y.id.toString(), y.where_sighted_refs, "WHERE_SIGHTED")
    }
  }

  // MarkingDefinition and LanguageContent relations
  def createStixObjRel(stixObj: StixObj) = {
    stixObj match {
      case x: MarkingDefinition =>
        // the object marking relations
        createRelToObjRef(x.id.toString(), x.object_marking_refs, "HAS_MARKING")
        // the created_by relation
        createdByRel(x.id.toString(), x.created_by_ref)

      // todo <----- contents: Map[String, Map[String, String]]
      case x: LanguageContent =>
        // the object marking relations
        createRelToObjRef(x.id.toString(), x.object_marking_refs, "HAS_MARKING")
        // the created_by relation
        createdByRel(x.id.toString(), x.created_by_ref)
    }
  }

  //--------------------------------------------------------------------------------------------

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
