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
    * @param objects  the Observables
    * @param obsIds   the Observables ids
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

  private def specify(theObsId: String, observable: Observable, node: Node) = {
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
        transaction(DbService.graphDB) {
          node.setProperty("number", x.number)
          node.setProperty("name", x.name.getOrElse(""))
          node.setProperty("rir", x.rir.getOrElse(""))
        }

      case x: Directory =>
        transaction(DbService.graphDB) {
          node.setProperty("path", x.path)
          node.setProperty("path_enc", x.path_enc.getOrElse(""))
          node.setProperty("created", x.created.getOrElse("").toString)
          node.setProperty("modified", x.modified.getOrElse("").toString)
          node.setProperty("accessed", x.accessed.getOrElse("").toString)
          node.setProperty("contains_refs", x.contains_refs.getOrElse(List.empty).toArray)
        }

      case x: DomainName =>
        transaction(DbService.graphDB) {
          node.setProperty("value", x.value)
          node.setProperty("resolves_to_refs", x.resolves_to_refs.getOrElse(List.empty).toArray)
        }

      case x: EmailAddress =>
        transaction(DbService.graphDB) {
          node.setProperty("display_name", x.display_name.getOrElse(""))
          node.setProperty("belongs_to_ref", x.belongs_to_ref.getOrElse(""))
        }

      case x: EmailMessage =>
        transaction(DbService.graphDB) {
          node.setProperty("is_multipart", x.is_multipart)
          // todo EmailMimeType
          node.setProperty("body", x.body.getOrElse(""))
          node.setProperty("date", x.date.getOrElse("").toString)
          node.setProperty("content_type", x.content_type.getOrElse(""))
          node.setProperty("from_ref", x.from_ref.getOrElse(""))
          node.setProperty("sender_ref", x.sender_ref.getOrElse(""))
          node.setProperty("to_refs", x.to_refs.getOrElse(List.empty).toArray)
          node.setProperty("cc_refs", x.cc_refs.getOrElse(List.empty).toArray)
          node.setProperty("bcc_refs", x.bcc_refs.getOrElse(List.empty).toArray)
          node.setProperty("subject", x.subject.getOrElse(""))
          node.setProperty("received_lines", x.received_lines.getOrElse(List.empty).toArray)
          // todo additional_header_fields
          node.setProperty("raw_email_ref", x.raw_email_ref.getOrElse(""))
        }

      case x: File =>
        val hashes_ids: Map[String, String] = (for (s <- x.hashes.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        transaction(DbService.graphDB) {
          node.setProperty("size", x.size.getOrElse(0))
          node.setProperty("name", x.name.getOrElse(""))
          node.setProperty("name_enc", x.name_enc.getOrElse(""))
          node.setProperty("magic_number_hex", x.magic_number_hex.getOrElse(""))
          node.setProperty("mime_type", x.mime_type.getOrElse(""))
          node.setProperty("created", x.created.getOrElse("").toString)
          node.setProperty("modified", x.modified.getOrElse("").toString)
          node.setProperty("accessed", x.accessed.getOrElse("").toString)
          node.setProperty("parent_directory_ref", x.parent_directory_ref.getOrElse(""))
          node.setProperty("is_encrypted", x.is_encrypted.getOrElse(false))
          node.setProperty("encryption_algorithm", x.encryption_algorithm.getOrElse(""))
          node.setProperty("decryption_key", x.decryption_key.getOrElse(""))
          node.setProperty("contains_refs", x.contains_refs.getOrElse(List.empty).toArray)
          node.setProperty("content_ref", x.content_ref.getOrElse(""))
          node.setProperty("hashes", hashes_ids.values.toArray)
        }
        createHashes(theObsId, x.hashes, hashes_ids)

      case x: IPv4Address =>
        transaction(DbService.graphDB) {
          node.setProperty("value", x.value)
          node.setProperty("resolves_to_refs", x.resolves_to_refs.getOrElse(List.empty).toArray)
          node.setProperty("belongs_to_refs", x.belongs_to_refs.getOrElse(List.empty).toArray)
        }

      case x: IPv6Address =>
        transaction(DbService.graphDB) {
          node.setProperty("value", x.value)
          node.setProperty("resolves_to_refs", x.resolves_to_refs.getOrElse(List.empty).toArray)
          node.setProperty("belongs_to_refs", x.belongs_to_refs.getOrElse(List.empty).toArray)
        }

      case x: MACAddress =>
        transaction(DbService.graphDB) {
          node.setProperty("value", x.value)
        }

      case x: Mutex =>
        transaction(DbService.graphDB) {
          node.setProperty("name", x.name)
        }

      case x: NetworkTraffic =>
        transaction(DbService.graphDB) {
          node.setProperty("start", x.start.getOrElse("").toString)
          node.setProperty("end", x.end.getOrElse("").toString)
          node.setProperty("is_active", x.is_active.getOrElse(false))
          node.setProperty("src_ref", x.src_ref.getOrElse(""))
          node.setProperty("dst_ref", x.dst_ref.getOrElse(""))
          node.setProperty("src_port", x.src_port.getOrElse(0)) // todo <--- not correct
          node.setProperty("dst_port", x.dst_port.getOrElse(0)) // todo <--- not correct
          node.setProperty("protocols", x.protocols.getOrElse(List.empty).toArray)
          node.setProperty("src_byte_count", x.src_byte_count.getOrElse(0))
          node.setProperty("dst_byte_count", x.dst_byte_count.getOrElse(0))
          node.setProperty("src_packets", x.src_packets.getOrElse(0))
          node.setProperty("dst_packets", x.dst_packets.getOrElse(0))
          // todo ipfix
          node.setProperty("src_payload_ref", x.src_payload_ref.getOrElse(""))
          node.setProperty("dst_payload_ref", x.dst_payload_ref.getOrElse(""))
          node.setProperty("encapsulates_refs", x.encapsulates_refs.getOrElse(List.empty).toArray)
          node.setProperty("encapsulated_by_ref", x.encapsulated_by_ref.getOrElse(""))
        }

      case x: Process =>
        transaction(DbService.graphDB) {
          node.setProperty("is_hidden", x.is_hidden.getOrElse(false).toString)
          node.setProperty("pid", x.pid.getOrElse(0)) // todo <-- not correct
          node.setProperty("name", x.name.getOrElse(""))
          node.setProperty("created", x.created.getOrElse("").toString)
          node.setProperty("cwd", x.cwd.getOrElse(""))
          node.setProperty("arguments", x.arguments.getOrElse(List.empty).toArray)
          node.setProperty("command_line", x.cwd.getOrElse(""))
          // todo environment_variables
          node.setProperty("opened_connection_refs", x.opened_connection_refs.getOrElse(List.empty).toArray)
          node.setProperty("creator_user_ref", x.creator_user_ref.getOrElse(""))
          node.setProperty("binary_ref", x.binary_ref.getOrElse(""))
          node.setProperty("parent_ref", x.parent_ref.getOrElse(""))
          node.setProperty("child_refs", x.child_refs.getOrElse(List.empty).toArray)
        }

      case x: Software =>
        transaction(DbService.graphDB) {
          node.setProperty("name", x.name)
          node.setProperty("cpe", x.cpe.getOrElse(""))
          node.setProperty("languages", x.languages.getOrElse(List.empty).toArray)
          node.setProperty("vendor", x.vendor.getOrElse(""))
          node.setProperty("version", x.version.getOrElse(""))
        }

      case x: URL =>
        transaction(DbService.graphDB) {
          node.setProperty("value", x.value)
        }

      case x: UserAccount =>
        transaction(DbService.graphDB) {
          node.setProperty("user_id", x.user_id)
          node.setProperty("account_login", x.account_login.getOrElse(""))
          node.setProperty("account_type", x.account_type.getOrElse(""))
          node.setProperty("display_name", x.display_name.getOrElse(""))
          node.setProperty("is_service_account", x.is_service_account.getOrElse(false))
          node.setProperty("is_privileged", x.is_privileged.getOrElse(false))
          node.setProperty("can_escalate_privs", x.can_escalate_privs.getOrElse(false))
          node.setProperty("is_disabled", x.is_disabled.getOrElse(false))
          node.setProperty("account_created", x.account_created.getOrElse("").toString)
          node.setProperty("account_expires", x.account_expires.getOrElse("").toString)
          node.setProperty("password_last_changed", x.password_last_changed.getOrElse("").toString)
          node.setProperty("account_first_login", x.account_first_login.getOrElse("").toString)
          node.setProperty("account_last_login", x.account_last_login.getOrElse("").toString)
        }

      case x: WindowsRegistryKey =>
        transaction(DbService.graphDB) {
          node.setProperty("key", x.key)
          // todo values Option[List[WindowsRegistryValueType]]
          node.setProperty("modified", x.modified.getOrElse("").toString)
          node.setProperty("creator_user_ref", x.creator_user_ref.getOrElse(""))
          node.setProperty("number_of_subkeys", x.number_of_subkeys.getOrElse(0))
        }

      case x: X509Certificate =>
        val hashes_ids: Map[String, String] = (for (s <- x.hashes.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        transaction(DbService.graphDB) {
          node.setProperty("is_self_signed", x.is_self_signed.getOrElse(false))
          node.setProperty("version", x.version.getOrElse(""))
          node.setProperty("serial_number", x.serial_number.getOrElse(""))
          node.setProperty("signature_algorithm", x.signature_algorithm.getOrElse(""))
          node.setProperty("issuer", x.issuer.getOrElse(""))
          node.setProperty("validity_not_before", x.validity_not_before.getOrElse("").toString)
          node.setProperty("validity_not_after", x.validity_not_after.getOrElse("").toString)
          node.setProperty("subject", x.subject.getOrElse(""))
          node.setProperty("subject_public_key_algorithm", x.subject_public_key_algorithm.getOrElse(""))
          node.setProperty("subject_public_key_modulus", x.subject_public_key_modulus.getOrElse(""))
          node.setProperty("subject_public_key_exponent", x.subject_public_key_exponent.getOrElse(0))
          node.setProperty("hashes", hashes_ids.values.toArray)
          // todo x509_v3_extensions
        }
        createHashes(theObsId, x.hashes, hashes_ids)

      case _ =>
    }
  }

  // create the hashes objects and their relationship to the Observable parent node
  private def createHashes(obsIdString: String, hashesOpt: Option[Map[String, String]], ids: Map[String, String]) = {
    hashesOpt.foreach(hashes =>
      for ((k, obs) <- hashes) {
        var hashNode: Node = null
        transaction(DbService.graphDB) {
          hashNode = DbService.graphDB.createNode(label("hashes"))
          hashNode.setProperty("hash_id", ids(k))
          hashNode.setProperty("type", k)
          hashNode.setProperty("hash", obs)
          DbService.hash_idIndex.add(hashNode, "hash_id", hashNode.getProperty("hash_id"))
        }
        transaction(DbService.graphDB) {
          val sourceNode = DbService.observable_idIndex.get("observable_id", obsIdString).getSingle
          sourceNode.createRelationshipTo(hashNode, RelationshipType.withName("HAS_HASHES"))
        }
      }
    )
  }

}
