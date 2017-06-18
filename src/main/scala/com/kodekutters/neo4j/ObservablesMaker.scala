package com.kodekutters.neo4j

import java.util.UUID

import com.kodekutters.stix._
import org.neo4j.graphdb.Label.label
import org.neo4j.graphdb.{Node, RelationshipType}

/**
  * make the Observables nodes and relations for the ObservedData SDO
  */
object ObservablesMaker {

  import MakerSupport._
  import DbService._

  /**
    * create the Observables node and relations for the parent ObservedData SDO node
    *
    * @param idString the parent ObservedData SDO node id
    * @param objects  the parent node Observables
    * @param obsIds   the Observable ids
    */
  def create(idString: String, objects: Map[String, Observable], obsIds: Map[String, String]) = {
    // create the observable nodes and relations for each Observable
    for ((k, obs) <- objects) {
      val obsId = obsIds(k)
      // create the extensions ids
      val ext_ids: Map[String, String] = (for (s <- obs.extensions.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
      // create the observable node
      var node: Node = null
      transaction(DbService.graphDB) {
        node = DbService.graphDB.createNode(label(asCleanLabel(obs.`type`)))
        node.addLabel(label("Observable"))
        node.setProperty("name", k) // todo <--- not part of the specs
        node.setProperty("type", obs.`type`)
        node.setProperty("observable_id", obsId)
        node.setProperty("extensions", ext_ids.values.toArray)
        node.setProperty("description", obs.description.getOrElse(""))
        DbService.observable_idIndex.add(node, "observable_id", node.getProperty("observable_id"))
      }
      // create the Extension nodes and relations to this observable
      ExtensionsMaker.create(obsId, obs.extensions, ext_ids)
      // specify the observable attributes
      specify(obsId, obs, node)
      // create the relation to the parent node
      transaction(DbService.graphDB) {
        // the ObservedData node
        val sourceNode = DbService.idIndex.get("id", idString).getSingle
        sourceNode.createRelationshipTo(node, RelationshipType.withName("HAS_OBSERVABLE"))
      }
    }
  }

  def specify(theObsId: String, observable: Observable, node: Node) = {
    // add the specific attributes to the observable node
    observable match {
      case x: Artifact =>
        // create the hashes ids
        val hashes_ids: Map[String, String] = (for (s <- x.hashes.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        transaction(DbService.graphDB) {
          node.setProperty("mime_type", x.mime_type.getOrElse(""))
          node.setProperty("payload_bin", x.payload_bin.getOrElse(""))
          node.setProperty("url", x.url.getOrElse(""))
          node.setProperty("hashes", hashes_ids.values.toArray)
        }
        // create the hashes objects and embedded relations
        createHashes(theObsId, x.hashes, hashes_ids)

      case x: AutonomousSystem =>
      case x: Directory =>
      case x: DomainName =>
      case x: EmailAddress =>
      case x: EmailMessage =>
      case x: File =>
      case x: IPv4Address =>
      case x: IPv6Address =>
      case x: MACAddress =>
      case x: Mutex =>
      case x: NetworkTraffic =>
      case x: Process =>
      case x: Software =>
      case x: URL =>
      case x: UserAccount =>
      case x: WindowsRegistryKey =>
      case x: X509Certificate =>
      case _ =>
    }

  }

}
