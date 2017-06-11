package com.kodekutters.neo4j

import java.io.{File, InputStream}
import java.util.UUID

import com.kodekutters.stix._
import com.kodekutters.stix.Bundle
import io.circe.generic.auto._
import io.circe.parser.decode

import scala.io.Source
import scala.language.implicitConversions
import scala.language.postfixOps
import scala.collection.JavaConverters._
//import com.typesafe.config.ConfigFactory
//import org.neo4j.driver.v1.{AuthTokens, GraphDatabase}
import org.neo4j.graphdb.{GraphDatabaseService, Label, Node, RelationshipType}
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.Label._
import org.neo4j.graphdb.index.Index

/**
  * loads Stix-2.1 objects and relationships into a Neo4j graph database
  *
  * @author R. Wathelet June 2017
  *
  *         ref: https://github.com/workingDog/scalastix
  */
object Neo4jLoader {

  // must use this constructor, class is private
  def apply(inFile: String, dbDir: String) = new Neo4jLoader(inFile, dbDir)

  // general transaction support
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
    * read a Bundle from the InputStream source
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

}

/**
  * loads Stix-2.1 objects (nodes) and relationships (edges) into a Neo4j database
  *
  * @param inFile the input file to process
  * @param dbDir  the neo4j graph database directory name
  */
class Neo4jLoader private(inFile: String, dbDir: String) {

  import Neo4jLoader._

  // not used
//  val config = ConfigFactory.parseFile(new File("application.conf"))

  val objectRefs = "object_refs"
  val observedDataRefs = "observed_data_refs"
  val whereSightedRefs = "where_sighted_refs"
  val markingObjRefs = "marking_object_refs"

  var idIndex: Index[Node] = _
  var marking_idIndex: Index[Node] = _
  var kill_chain_phase_idIndex: Index[Node] = _
  var external_reference_idIndex: Index[Node] = _
  var granular_marking_idIndex: Index[Node] = _
  var object_ref_idIndex: Index[Node] = _

  // will create a new database or add to the existing one
  val graphDB: GraphDatabaseService = new GraphDatabaseFactory().newEmbeddedDatabase(new File(dbDir))
  transaction(graphDB) {
    idIndex = graphDB.index.forNodes("id")
    marking_idIndex = graphDB.index.forNodes("marking_id")
    kill_chain_phase_idIndex = graphDB.index.forNodes("kill_chain_phase_id")
    external_reference_idIndex = graphDB.index.forNodes("external_reference_id")
    granular_marking_idIndex = graphDB.index.forNodes("granular_marking_id")
    object_ref_idIndex = graphDB.index.forNodes("object_ref_id")
  }

  private def closeAll() = {
    graphDB.shutdown()
  }

  // generate a unique random id
  private def newId = UUID.randomUUID().toString

  // process a Stix object according to its type
  private def convertObj(obj: StixObj) = {
    obj match {
      case stix if stix.isInstanceOf[SDO] => convertSDO(stix.asInstanceOf[SDO])
      case stix if stix.isInstanceOf[SRO] => convertSRO(stix.asInstanceOf[SRO])
      case stix if stix.isInstanceOf[StixObj] => convertStixObj(stix.asInstanceOf[StixObj])
      case stix => // do nothing for now
    }
  }

  // process a bundle of Stix objects in the following sequence:
  // SDO, MarkingDefinition, LanguageContent then SRO
  private def convertInSequence(bundle: Bundle) = {
    bundle.objects.filter(_.isInstanceOf[SDO]).foreach(convertObj(_))
    bundle.objects.filter(_.isInstanceOf[MarkingDefinition]).foreach(convertObj(_))
    bundle.objects.filter(_.isInstanceOf[LanguageContent]).foreach(convertObj(_))
    bundle.objects.filter(_.isInstanceOf[SRO]).foreach(convertObj(_))
  }

  /**
    * read a bundle of Stix objects from the input file,
    * convert it to neo4j node and relations and load it into the db
    */
  def convertBundleFile(): Unit = {
    // read a STIX bundle from the inFile
    val jsondoc = Source.fromFile(inFile).mkString
    // create a bundle object from it and convert its objects to nodes and relations
    decode[Bundle](jsondoc) match {
      case Left(failure) => println("\n-----> ERROR reading bundle in file: " + inFile)
      case Right(bundle) => convertInSequence(bundle)
    }
    // all done
    closeAll()
  }

  /**
    * read Stix bundles from the input zip file and
    * convert them to neo4j nodes and relations and load them into the db
    */
  def convertBundleZipFile(): Unit = {
    // get the zip file
    import scala.collection.JavaConverters._
    val rootZip = new java.util.zip.ZipFile(new File(inFile))
    // for each entry file
    rootZip.entries.asScala.filter(_.getName.toLowerCase.endsWith(".json")).foreach(f => {
      loadBundle(rootZip.getInputStream(f)) match {
        case Some(bundle) => convertInSequence(bundle)
        case None => println("-----> ERROR invalid bundle JSON in zip file: \n")
      }
    })
    // all done   
    closeAll()
  }

  /**
    * For processing very large text files.
    *
    * read Stix objects one by one from the input file,
    * convert them to neo4j nodes and relations and load them into the db
    *
    * The input file must contain a Stix object on one line ending with a new line.
    * All nodes (SDO) must appear first, followed by the relationships (SRO).
    *
    */
  def convertStixFile(): Unit = {
    // read a STIX object from the inFile, one line at a time
    for (line <- Source.fromFile(inFile).getLines) {
      // create a Stix object from it and convert it to node or relation
      decode[StixObj](line) match {
        case Left(failure) => println("\n-----> ERROR reading StixObj in file: " + inFile + " line: " + line)
        case Right(stixObj) => convertObj(stixObj)
      }
    }
    // all done   
    closeAll()
  }

  /**
    * For processing very large zip files.
    *
    * read Stix objects one by one from the input zip file,
    * convert them to neo4j nodes and relations and load them into the db
    *
    * There can be one or more file entries in the zip file,
    * each file must have the extension .json.
    *
    * Each entry file must have a Stix object on one line ending with a new line.
    * All nodes (SDO) must appear first, followed by the relationships (SRO).
    *
    */
  def convertStixZipFile(): Unit = {
    // get the input zip file
    val rootZip = new java.util.zip.ZipFile(new File(inFile))
    // for each entry file
    rootZip.entries.asScala.filter(_.getName.toLowerCase.endsWith(".json")).foreach(f => {
      // get the lines from the entry file
      val inputLines = Source.fromInputStream(rootZip.getInputStream(f)).getLines
      // read a Stix object from the inputLines, one line at a time
      for (line <- inputLines) {
        // create a Stix object from it, convert and write it out
        decode[StixObj](line) match {
          case Left(failure) => println("\n-----> ERROR reading StixObj in file: " + f.getName + " line: " + line)
          case Right(stixObj) => convertObj(stixObj)
        }
      }
    })
    // all done   
    closeAll()
  }

  // process the SDO
  def convertSDO(x: SDO) = {
    // common elements
    val labelsString = toStringArray(x.labels)
    val granular_markings_ids = toIdArray(x.granular_markings)
    val external_references_ids = toIdArray(x.external_references)
    val object_marking_refs_arr = toStringIds(x.object_marking_refs)
    val nodeLabel = asCleanLabel(x.`type`)
    // create a base node common to all SDO
    def baseNode(labl: String): Node = {
      var sdoNode: Node = null
      transaction(graphDB) {
        sdoNode = graphDB.createNode(label(labl))
        sdoNode.addLabel(label("SDO"))
        sdoNode.setProperty("id", x.id.toString())
        sdoNode.setProperty("type", x.`type`)
        sdoNode.setProperty("created", x.created.time)
        sdoNode.setProperty("modified", x.modified.time)
        sdoNode.setProperty("revoked", x.revoked.getOrElse("false"))
        sdoNode.setProperty("labels", labelsString)
        sdoNode.setProperty("confidence", x.confidence.getOrElse(0))
        sdoNode.setProperty("external_references", external_references_ids)
        sdoNode.setProperty("lang", clean(x.lang.getOrElse("")))
        sdoNode.setProperty("object_marking_refs", object_marking_refs_arr)
        sdoNode.setProperty("granular_markings", granular_markings_ids)
        sdoNode.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
        idIndex.add(sdoNode, "id", sdoNode.getProperty("id"))
      }
      // write the external_references
      writeExternRefs(x.id.toString(), x.external_references, external_references_ids)
      // write the granular_markings
      writeGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)
      // return the node
      sdoNode
    }

    x.`type` match {

      case AttackPattern.`type` =>
        val y = x.asInstanceOf[AttackPattern]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val sdoNode = baseNode(nodeLabel)
        transaction(graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
        }
        writeKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Identity.`type` =>
        val y = x.asInstanceOf[Identity]
        val sdoNode = baseNode(nodeLabel)
        transaction(graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("identity_class", clean(y.identity_class))
          sdoNode.setProperty("sectors", toStringArray(y.sectors))
          sdoNode.setProperty("contact_information", clean(y.contact_information.getOrElse("")))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }

      case Campaign.`type` =>
        val y = x.asInstanceOf[Campaign]
        val sdoNode = baseNode(nodeLabel)
        transaction(graphDB) {
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
        transaction(graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }

      case IntrusionSet.`type` =>
        val y = x.asInstanceOf[IntrusionSet]
        val sdoNode = baseNode(nodeLabel)
        transaction(graphDB) {
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
        transaction(graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
        }
        writeKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Report.`type` =>
        val y = x.asInstanceOf[Report]
        val object_refs_ids = toIdArray(y.object_refs)
        val sdoNode = baseNode(nodeLabel)
        transaction(graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("published", y.published.time)
          sdoNode.setProperty("object_refs_ids", object_refs_ids)
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }
        writeObjRefs(y.id.toString(), y.object_refs, object_refs_ids, objectRefs)

      case ThreatActor.`type` =>
        val y = x.asInstanceOf[ThreatActor]
        val sdoNode = baseNode(nodeLabel)
        transaction(graphDB) {
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
        transaction(graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
          sdoNode.setProperty("tool_version", clean(y.tool_version.getOrElse("")))
        }
        writeKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Vulnerability.`type` =>
        val y = x.asInstanceOf[Vulnerability]
        val sdoNode = baseNode(nodeLabel)
        transaction(graphDB) {
          sdoNode.setProperty("name", clean(y.name))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }

      case Indicator.`type` =>
        val y = x.asInstanceOf[Indicator]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val sdoNode = baseNode(nodeLabel)
        transaction(graphDB) {
          sdoNode.setProperty("name", clean(y.name.getOrElse("")))
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
          sdoNode.setProperty("pattern", clean(y.pattern))
          sdoNode.setProperty("valid_from", y.valid_from.toString())
          sdoNode.setProperty("valid_until", clean(y.valid_until.getOrElse("").toString))
          sdoNode.setProperty("kill_chain_phases", kill_chain_phases_ids)
        }
        writeKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      // todo  objects: Map[String, Observable],
      case ObservedData.`type` =>
        val y = x.asInstanceOf[ObservedData]
        val sdoNode = baseNode(nodeLabel)
        transaction(graphDB) {
          sdoNode.setProperty("first_observed", y.first_observed.toString())
          sdoNode.setProperty("last_observed", y.last_observed.toString())
          sdoNode.setProperty("number_observed", y.number_observed)
          sdoNode.setProperty("description", clean(y.description.getOrElse("")))
        }

      case _ => // do nothing for now
    }
  }

  // the Relationship and Sighting
  def convertSRO(x: SRO) = transaction(graphDB) {
    // common elements
    val labelsString = toStringArray(x.labels)
    val granular_markings_ids = toIdArray(x.granular_markings)
    val external_references_ids = toIdArray(x.external_references)
    val object_marking_refs_arr = toStringIds(x.object_marking_refs)

    def baseRel(sourceNode: Node, targetNode: Node, name: String): org.neo4j.graphdb.Relationship = {
      var rel: org.neo4j.graphdb.Relationship = null
      transaction(graphDB) {
        rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName(name))
        rel.setProperty("id", x.id.toString())
        rel.setProperty("type", x.`type`)
        rel.setProperty("created", x.created.time)
        rel.setProperty("modified", x.modified.time)
        rel.setProperty("revoked", x.revoked.getOrElse("false"))
        rel.setProperty("labels", labelsString)
        rel.setProperty("confidence", x.confidence.getOrElse(0))
        rel.setProperty("external_references", external_references_ids)
        rel.setProperty("lang", clean(x.lang.getOrElse("")))
        rel.setProperty("object_marking_refs", object_marking_refs_arr)
        rel.setProperty("granular_markings", granular_markings_ids)
        rel.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
      }
      // write the external_references
      writeExternRefs(x.id.toString(), x.external_references, external_references_ids)
      // write the granular_markings
      writeGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)
      // return the relation
      rel
    }

    if (x.isInstanceOf[Relationship]) {
      val y = x.asInstanceOf[Relationship]
      val sourceNode = idIndex.get("id", y.source_ref.toString()).getSingle
      val targetNode = idIndex.get("id", y.target_ref.toString()).getSingle
      val rel = baseRel(sourceNode, targetNode, asCleanLabel(y.relationship_type))
      transaction(graphDB) {
        rel.setProperty("source_ref", y.source_ref.toString())
        rel.setProperty("target_ref", y.target_ref.toString())
        rel.setProperty("relationship_type", asCleanLabel(y.relationship_type))
        rel.setProperty("description", clean(y.description.getOrElse("")))
      }
    }
    else { // must be a Sighting todo ---->  observed_data_refs
      val y = x.asInstanceOf[Sighting]
      val observed_data_ids = toIdArray(y.observed_data_refs)
      val where_sighted_refs_ids = toIdArray(y.where_sighted_refs)
      var sightingNode: Node = null
      // create a special SightingNode to be the source node in the sighting relationship
      transaction(graphDB) {
        sightingNode = graphDB.createNode(label("SightingNode"))
        sightingNode.setProperty("id", y.id.toString())
        sightingNode.setProperty("type", Sighting.`type`)
        idIndex.add(sightingNode, "id", sightingNode.getProperty("id"))
      }
      val targetNode = idIndex.get("id", y.sighting_of_ref.toString()).getSingle
      val rel = baseRel(sightingNode, targetNode, asCleanLabel(Sighting.`type`))
      transaction(graphDB) {
        rel.setProperty("sighting_of_ref", y.sighting_of_ref.toString())
        rel.setProperty("first_seen", y.first_seen.getOrElse("").toString)
        rel.setProperty("last_seen", y.last_seen.getOrElse("").toString)
        rel.setProperty("count", y.count.getOrElse(0))
        rel.setProperty("summary", y.summary.getOrElse(""))
        rel.setProperty("observed_data_id", observed_data_ids)
        rel.setProperty("where_sighted_refs_id", where_sighted_refs_ids)
        rel.setProperty("description", clean(y.description.getOrElse("")))
      }
      writeObjRefs(y.id.toString(), y.observed_data_refs, observed_data_ids, observedDataRefs)
      writeObjRefs(y.id.toString(), y.where_sighted_refs, where_sighted_refs_ids, whereSightedRefs)
    }
  }

  // convert MarkingDefinition and LanguageContent
  def convertStixObj(stixObj: StixObj) = {

    stixObj match {

      case x: MarkingDefinition =>
        val definition_id = newId
        val granular_markings_ids = toIdArray(x.granular_markings)
        val external_references_ids = toIdArray(x.external_references)
        val object_marking_refs_arr = toStringIds(x.object_marking_refs)
        val nodeLabel = asCleanLabel(x.`type`)
        transaction(graphDB) {
          val stixNode = graphDB.createNode(label(nodeLabel))
          stixNode.addLabel(label("StixObj"))
          stixNode.setProperty("id", x.id.toString())
          stixNode.setProperty("type", x.`type`)
          stixNode.setProperty("created", x.created.time)
          stixNode.setProperty("definition_type", clean(x.definition_type))
          stixNode.setProperty("definition_id", definition_id)
          stixNode.setProperty("external_references", external_references_ids)
          stixNode.setProperty("object_marking_refs", object_marking_refs_arr)
          stixNode.setProperty("granular_markings", granular_markings_ids)
          stixNode.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
          idIndex.add(stixNode, "id", stixNode.getProperty("id"))
        }
        // write the external_references
        writeExternRefs(x.id.toString(), x.external_references, external_references_ids)
        // write the granular_markings
        writeGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)
        // write the marking object definition
        writeMarkingObjRefs(x.id.toString(), x.definition, definition_id)

      // todo <----- contents: Map[String, Map[String, String]]
      case x: LanguageContent =>
        val labelsString = toStringArray(x.labels)
        val granular_markings_ids = toIdArray(x.granular_markings)
        val external_references_ids = toIdArray(x.external_references)
        val object_marking_refs_arr = toStringIds(x.object_marking_refs)
        val nodeLabel = asCleanLabel(x.`type`)
        transaction(graphDB) {
          val stixNode = graphDB.createNode(label(nodeLabel))
          stixNode.addLabel(label("StixObj"))
          stixNode.setProperty("id", x.id.toString())
          stixNode.setProperty("type", x.`type`)
          stixNode.setProperty("created", x.created.time)
          stixNode.setProperty("modified", x.modified.time)
          stixNode.setProperty("object_modified", x.object_modified.time)
          stixNode.setProperty("object_ref", x.object_ref.toString())
          stixNode.setProperty("labels", labelsString)
          stixNode.setProperty("revoked", x.revoked.getOrElse("false"))
          stixNode.setProperty("external_references", external_references_ids)
          stixNode.setProperty("object_marking_refs", object_marking_refs_arr)
          stixNode.setProperty("granular_markings", granular_markings_ids)
          stixNode.setProperty("created_by_ref", x.created_by_ref.getOrElse("").toString)
          idIndex.add(stixNode, "id", stixNode.getProperty("id"))
        }
        // write the external_references
        writeExternRefs(x.id.toString(), x.external_references, external_references_ids)
        // write the granular_markings
        writeGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)
    }
  }

  //--------------------------------------------------------------------------------------------

  // write the marking object and relationship
  def writeMarkingObjRefs(idString: String, definition: MarkingObject, definition_id: String) = {
    val mark: String = definition match {
      case s: StatementMarking => clean(s.statement) + ",statement"
      case s: TPLMarking => clean(s.tlp.value) + ",tlp"
      case _ => ""
    }
    var stixNode: Node = null
    transaction(graphDB) {
      stixNode = graphDB.createNode(label(markingObjRefs))
      stixNode.setProperty("marking_id", definition_id)
      stixNode.setProperty("marking", mark)
      marking_idIndex.add(stixNode, "marking_id", stixNode.getProperty("marking_id"))
    }
    transaction(graphDB) {
      val sourceNode = idIndex.get("id", idString).getSingle
      val targetNode = stixNode
      val rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("HAS_MARKING_OBJECT"))
    }
  }

  // write the kill_chain_phases nodes and relationships
  def writeKillPhases(idString: String, kill_chain_phases: Option[List[KillChainPhase]], kill_chain_phases_ids: String) = {
    val killphases = for (s <- kill_chain_phases.getOrElse(List.empty))
      yield (clean(s.kill_chain_name), clean(s.phase_name), asCleanLabel(s.`type`))
    if (killphases.nonEmpty) {
      val temp = kill_chain_phases_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip killphases).foreach({ case (a, (b, c, d)) =>
        transaction(graphDB) {
          val stixNode = graphDB.createNode(label(d))
          stixNode.setProperty("kill_chain_phase_id", a)
          stixNode.setProperty("kill_chain_name", b)
          stixNode.setProperty("phase_name", c)
          kill_chain_phase_idIndex.add(stixNode, "kill_chain_phase_id", stixNode.getProperty("kill_chain_phase_id"))
        }
      })
      for (k <- temp.split(",")) {
        transaction(graphDB) {
          val sourceNode = idIndex.get("id", idString).getSingle
          val targetNode = kill_chain_phase_idIndex.get("kill_chain_phase_id", k).getSingle
          val rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("HAS_KILL_CHAIN_PHASE"))
        }
      }
    }
  }

  // write the external_references nodes and relationships
  def writeExternRefs(idString: String, external_references: Option[List[ExternalReference]], external_references_ids: String) = {
    val externRefs = for (s <- external_references.getOrElse(List.empty))
      yield (clean(s.source_name), clean(s.description.getOrElse("")),
        clean(s.url.getOrElse("")), clean(s.external_id.getOrElse("")), asCleanLabel(s.`type`))
    if (externRefs.nonEmpty) {
      val temp = external_references_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip externRefs).foreach(
        { case (a, (b, c, d, e, f)) =>
          transaction(graphDB) {
            val stixNode = graphDB.createNode(label(f))
            stixNode.setProperty("external_reference_id", a)
            stixNode.setProperty("source_name", b)
            stixNode.setProperty("description", c)
            stixNode.setProperty("url", d)
            stixNode.setProperty("external_id", e)
            external_reference_idIndex.add(stixNode, "external_reference_id", stixNode.getProperty("external_reference_id"))
          }
        }
      )
      // write the external_reference relationships with the given ids
      for (k <- temp.split(",")) {
        transaction(graphDB) {
          val sourceNode = idIndex.get("id", idString).getSingle
          val targetNode = external_reference_idIndex.get("external_reference_id", k).getSingle
          val rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("HAS_EXTERNAL_REF"))
        }
      }
    }
  }

  // write the granular_markings nodes and relationships
  def writeGranulars(idString: String, granular_markings: Option[List[GranularMarking]], granular_markings_ids: String) = {
    val granulars = for (s <- granular_markings.getOrElse(List.empty))
      yield (toStringArray(Option(s.selectors)), clean(s.marking_ref.getOrElse("")),
        clean(s.lang.getOrElse("")), asCleanLabel(s.`type`))
    if (granulars.nonEmpty) {
      val temp = granular_markings_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip granulars).foreach(
        { case (a, (b, c, d, e)) =>
          transaction(graphDB) {
            val stixNode = graphDB.createNode(label(e))
            stixNode.setProperty("granular_marking_id", a)
            stixNode.setProperty("selectors", b)
            stixNode.setProperty("marking_ref", c)
            stixNode.setProperty("lang", d)
            granular_marking_idIndex.add(stixNode, "granular_marking_id", stixNode.getProperty("granular_marking_id"))
          }
        }
      )
      // write the granular_markings relationships with the given ids
      for (k <- temp.split(",")) {
        transaction(graphDB) {
          val sourceNode = idIndex.get("id", idString).getSingle
          val targetNode = granular_marking_idIndex.get("granular_marking_id", k).getSingle
          val rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName("HAS_GRANULAR_MARKING"))
        }
      }
    }
  }

  // write the object_refs nodes and relationships
  def writeObjRefs(idString: String, object_refs: Option[List[Identifier]], object_refs_ids: String, typeName: String) = {
    val objRefs = for (s <- object_refs.getOrElse(List.empty)) yield clean(s.toString())
    if (objRefs.nonEmpty) {
      val temp = object_refs_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip objRefs).foreach({
        case (a, b) =>
          transaction(graphDB) {
            val stixNode = graphDB.createNode(label(asCleanLabel(typeName)))
            stixNode.setProperty("object_ref_id", a)
            stixNode.setProperty("identifier", b)
            object_ref_idIndex.add(stixNode, "object_ref_id", stixNode.getProperty("object_ref_id"))
          }
      })
      // write the object_refs relationships with the given ids
      for (k <- temp.split(",")) {
        val rtype = "HAS_" + asCleanLabel(typeName.toUpperCase)
        transaction(graphDB) {
          val sourceNode = idIndex.get("id", idString).getSingle
          val targetNode = object_ref_idIndex.get("object_ref_id", k).getSingle
          val rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName(rtype))
        }
      }
    }
  }

  // clean the string, i.e. replace unwanted char
  private def clean(s: String) = s.replace(",", " ").replace(":", " ").replace("\'", " ").replace(";", " ").replace("\"", "").replace("\\", "").replace("\n", "").replace("\r", "")

  // make an array of id values from the input list
  private def toIdArray(dataList: Option[List[Any]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'$newId'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // make an array of cleaned string values from the input list
  private def toStringArray(dataList: Option[List[String]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'${clean(s)}'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // make an array of id strings --> no cleaning done here
  private def toStringIds(dataList: Option[List[Identifier]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'${s.toString()}'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // the Neo4j :LABEL and :TYPE cannot deal with "-", so clean and replace with "_"
  private def asCleanLabel(s: String) = clean(s).replace("-", "_")

}
