package com.kodekutters.neo4j

import java.util.UUID
import com.kodekutters.stix._
import org.neo4j.graphdb.Node

object ObservablesMaker {

  import MakerSupport._
  import DbService._

  def specify(theObsId: String, observable: Observable, observableNode: Node) = {
    // add the specific attributes to the observable node
    observable match {
      case x: Artifact =>
        // create the hashes ids
        val hashes_ids: Map[String, String] = {
          if (x.hashes.isDefined)
            (for (s <- x.hashes.get.keySet) yield s -> UUID.randomUUID().toString).toMap
          else Map.empty
        }
        transaction(DbService.graphDB) {
          observableNode.setProperty("mime_type", x.mime_type.getOrElse(""))
          observableNode.setProperty("payload_bin", x.payload_bin.getOrElse(""))
          observableNode.setProperty("url", x.url.getOrElse(""))
          observableNode.setProperty("hashes", hashes_ids.values.toArray)
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
