package com.kodekutters.neo4j

import com.kodekutters.stix._

/**
  * create Neo4j relations from a Stix object
  */
class RelationsMaker() {

  import MakerSupport._
  import DbService._

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

      case _ => // do nothing more
    }
  }

  // create the Relationship and Sighting
  def createSRORel(x: SRO) = {

    // create a possible relation
    def baseRel(sourceId: String, targetId: String, name: String): Option[org.neo4j.graphdb.Relationship] = {
      val externRefIds = toIdArray(x.external_references)
      val granularIds = toIdArray(x.granular_markings)
      val relation = transactionOpt(DbService.graphDB) {
        val sourceNode = DbService.idIndex.get("id", sourceId).getSingle
        val targetNode = DbService.idIndex.get("id", targetId).getSingle
        val rel = sourceNode.createRelationshipTo(targetNode, name)
        rel.setProperty("id", x.id.toString())
        rel.setProperty("type", x.`type`)
        rel.setProperty("created", x.created.time)
        rel.setProperty("modified", x.modified.time)
        rel.setProperty("revoked", x.revoked.getOrElse(false))
        rel.setProperty("labels", x.labels.getOrElse(List.empty).toArray)
        rel.setProperty("confidence", x.confidence.getOrElse(0))
        rel.setProperty("external_references", externRefIds)
        rel.setProperty("lang", x.lang.getOrElse(""))
        rel.setProperty("object_marking_refs", toIdStringArray(x.object_marking_refs))
        rel.setProperty("granular_markings", granularIds)
        rel.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
        rel
      }
      // catch errors
      relation match {
        case Some(relship) =>
          // the object marking relations
          createRelToObjRef(x.id.toString(), x.object_marking_refs, "HAS_MARKING")
          // the created_by relation
          createdByRel(x.id.toString(), x.created_by_ref)
          // the external_references nodes and relations
          createExternRefs(x.id.toString(), x.external_references, externRefIds)
          // the granular_markings nodes and relations
          createGranulars(x.id.toString(), x.granular_markings, granularIds)

        case None => println("---> could not process relation: " + x.id.toString() + " from: " + sourceId + " to: " + targetId)
      }
      // the relation option
      relation
    }

    // a Relationsip
    if (x.isInstanceOf[Relationship]) {
      val y = x.asInstanceOf[Relationship]
      val relOpt = baseRel(y.source_ref.toString(), y.target_ref.toString(), asCleanLabel(y.relationship_type))
      relOpt.foreach(rel => {
        transactionOpt(DbService.graphDB) {
          rel.setProperty("source_ref", y.source_ref.toString())
          rel.setProperty("target_ref", y.target_ref.toString())
          rel.setProperty("relationship_type", y.relationship_type)
          rel.setProperty("description", y.description.getOrElse(""))
        }
      })
    }
    else { // a Sighting
      val y = x.asInstanceOf[Sighting]
      val relOpt = baseRel(y.sighting_of_ref.toString(), y.sighting_of_ref.toString(), asCleanLabel("sighting_of"))
      relOpt.foreach(rel => {
        transactionOpt(DbService.graphDB) {
          rel.setProperty("sighting_of_ref", y.sighting_of_ref.toString())
          rel.setProperty("first_seen", y.first_seen.getOrElse("").toString)
          rel.setProperty("last_seen", y.last_seen.getOrElse("").toString)
          rel.setProperty("count", y.count.getOrElse(0))
          rel.setProperty("summary", y.summary.getOrElse(""))
          rel.setProperty("observed_data_id", toIdStringArray(y.observed_data_refs))
          rel.setProperty("where_sighted_refs_id", toIdStringArray(y.where_sighted_refs))
          rel.setProperty("description", y.description.getOrElse(""))
        }
        // create relations between the sighting (SightingNode id) and the list of observed_data_refs ObservedData SDO id
        createRelToObjRef(y.id.toString(), y.observed_data_refs, "SIGHTED_OBSERVED_DATA")
        // create relations between the sighted SDO node and the list of where_sighted SDO nodes
        createRelToObjRef(y.sighting_of_ref.toString(), y.where_sighted_refs, "WAS_SIGHTED_BY")
      })
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

      case x: LanguageContent =>
        // the object marking relations
        createRelToObjRef(x.id.toString(), x.object_marking_refs, "HAS_MARKING")
        // the created_by relation
        createdByRel(x.id.toString(), x.created_by_ref)
        // the language contents relation from the LanguageContent object to the object_ref
        val relOpt = transactionOpt(DbService.graphDB) {
          val sourceNode = DbService.idIndex.get("id", x.id.toString()).getSingle
          val targetNode = DbService.idIndex.get("id", x.object_ref.toString()).getSingle
          val rel = sourceNode.createRelationshipTo(targetNode, asCleanLabel(x.`type`))
          rel.setProperty("object_modified", x.object_modified.time)
        }
        if(relOpt.isEmpty) println("---> could not process LanguageContent relation: " + x.id.toString() + " from: " + x.id.toString() + " to: " + x.object_ref.toString())
    }
  }

}
