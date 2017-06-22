package com.kodekutters.neo4j

import java.util.UUID

import com.kodekutters.stix._
import org.neo4j.graphdb.Label.label
import org.neo4j.graphdb.Node

/**
  * make the Observables nodes and relations for the ObservedData SDO
  */
object ObservablesMaker {

  import MakerSupport._
  import DbService._

  /**
    * create the Observables nodes and relations for the parent ObservedData SDO node
    *
    * @param sourceNode the parent ObservedData SDO node
    * @param objects    the Observables
    * @param obsIds     the Observables ids
    */
  def create(sourceNode: Node, objects: Map[String, Observable], obsIds: Map[String, String]) = {
    // create the observable nodes and relations for each Observable
    for ((k, obs) <- objects) {
      // create the extensions ids
      val ext_ids: Map[String, String] = (for (s <- obs.extensions.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
      // create the observable node
      val nodeOpt =
        transactionOpt(DbService.graphDB) {
          val theNode = DbService.graphDB.createNode(label(asCleanLabel(obs.`type`)))
          theNode.addLabel(label("Observable"))
          theNode.setProperty("type", obs.`type`)
          theNode.setProperty("observable_id", obsIds(k))
          theNode.setProperty("extensions", ext_ids.values.toArray)
          theNode.setProperty("description", obs.description.getOrElse(""))
          DbService.observable_idIndex.add(theNode, "observable_id", theNode.getProperty("observable_id"))
          theNode
        }
      nodeOpt match {
        case Some(node) =>
          // create the Extension nodes and relations to this observable
          ExtensionsMaker.create(node, obs.extensions, ext_ids)
          // specify the observable attributes
          specify(node, obs)
          // create the relation to the parent node
          transactionOpt(DbService.graphDB) {
            sourceNode.createRelationshipTo(node, "HAS_OBSERVABLE")
          }.getOrElse(println("---> could not process HAS_OBSERVABLE relation"))

        case None => println("---> could not create node Observable")
      }
    }
  }

  private def specify(node: Node, observable: Observable) = {
    // add the specific attributes to the observable node
    observable match {
      case x: Artifact =>
        // create the hashes ids
        val hashes_ids: Map[String, String] = (for (s <- x.hashes.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        transactionOpt(DbService.graphDB) {
          node.setProperty("mime_type", x.mime_type.getOrElse(""))
          node.setProperty("payload_bin", x.payload_bin.getOrElse(""))
          node.setProperty("url", x.url.getOrElse(""))
          node.setProperty("hashes", hashes_ids.values.toArray)
        }
        // create the hashes objects and embedded relations
        createHashes(node, x.hashes, hashes_ids)

      case x: AutonomousSystem =>
        transactionOpt(DbService.graphDB) {
          node.setProperty("number", x.number)
          node.setProperty("name", x.name.getOrElse(""))
          node.setProperty("rir", x.rir.getOrElse(""))
        }

      case x: Directory =>
        transactionOpt(DbService.graphDB) {
          node.setProperty("path", x.path)
          node.setProperty("path_enc", x.path_enc.getOrElse(""))
          node.setProperty("created", x.created.getOrElse("").toString)
          node.setProperty("modified", x.modified.getOrElse("").toString)
          node.setProperty("accessed", x.accessed.getOrElse("").toString)
          node.setProperty("contains_refs", x.contains_refs.getOrElse(List.empty).toArray)
        }

      case x: DomainName =>
        transactionOpt(DbService.graphDB) {
          node.setProperty("value", x.value)
          node.setProperty("resolves_to_refs", x.resolves_to_refs.getOrElse(List.empty).toArray)
        }

      case x: EmailAddress =>
        transactionOpt(DbService.graphDB) {
          node.setProperty("display_name", x.display_name.getOrElse(""))
          node.setProperty("belongs_to_ref", x.belongs_to_ref.getOrElse(""))
        }

      case x: EmailMessage =>
        val headers_ids: Map[String, String] = (for (s <- x.additional_header_fields.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        transactionOpt(DbService.graphDB) {
          node.setProperty("is_multipart", x.is_multipart)
          // todo body_multipart: Option[List[EmailMimeType]]
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
          node.setProperty("additional_header_fields", headers_ids.values.toArray)
          node.setProperty("raw_email_ref", x.raw_email_ref.getOrElse(""))
        }
        createHeaders(node, x.additional_header_fields, headers_ids)

      case x: File =>
        val hashes_ids: Map[String, String] = (for (s <- x.hashes.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        transactionOpt(DbService.graphDB) {
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
        createHashes(node, x.hashes, hashes_ids)

      case x: IPv4Address =>
        transactionOpt(DbService.graphDB) {
          node.setProperty("value", x.value)
          node.setProperty("resolves_to_refs", x.resolves_to_refs.getOrElse(List.empty).toArray)
          node.setProperty("belongs_to_refs", x.belongs_to_refs.getOrElse(List.empty).toArray)
        }

      case x: IPv6Address =>
        transactionOpt(DbService.graphDB) {
          node.setProperty("value", x.value)
          node.setProperty("resolves_to_refs", x.resolves_to_refs.getOrElse(List.empty).toArray)
          node.setProperty("belongs_to_refs", x.belongs_to_refs.getOrElse(List.empty).toArray)
        }

      case x: MACAddress =>
        transactionOpt(DbService.graphDB) {
          node.setProperty("value", x.value)
        }

      case x: Mutex =>
        transactionOpt(DbService.graphDB) {
          node.setProperty("name", x.name)
        }

      case x: NetworkTraffic =>
        val ipfix_ids: Map[String, String] = (for (s <- x.ipfix.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        transactionOpt(DbService.graphDB) {
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
          node.setProperty("ipfix", ipfix_ids.values.toArray)
          node.setProperty("src_payload_ref", x.src_payload_ref.getOrElse(""))
          node.setProperty("dst_payload_ref", x.dst_payload_ref.getOrElse(""))
          node.setProperty("encapsulates_refs", x.encapsulates_refs.getOrElse(List.empty).toArray)
          node.setProperty("encapsulated_by_ref", x.encapsulated_by_ref.getOrElse(""))
        }
        createIpfix(node, x.ipfix, ipfix_ids)

      case x: Process =>
        val env_ids: Map[String, String] = (for (s <- x.environment_variables.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        transactionOpt(DbService.graphDB) {
          node.setProperty("is_hidden", x.is_hidden.getOrElse(false).toString)
          node.setProperty("pid", x.pid.getOrElse(0)) // todo <-- not correct
          node.setProperty("name", x.name.getOrElse(""))
          node.setProperty("created", x.created.getOrElse("").toString)
          node.setProperty("cwd", x.cwd.getOrElse(""))
          node.setProperty("arguments", x.arguments.getOrElse(List.empty).toArray)
          node.setProperty("command_line", x.cwd.getOrElse(""))
          node.setProperty("environment_variables", env_ids.values.toArray)
          node.setProperty("opened_connection_refs", x.opened_connection_refs.getOrElse(List.empty).toArray)
          node.setProperty("creator_user_ref", x.creator_user_ref.getOrElse(""))
          node.setProperty("binary_ref", x.binary_ref.getOrElse(""))
          node.setProperty("parent_ref", x.parent_ref.getOrElse(""))
          node.setProperty("child_refs", x.child_refs.getOrElse(List.empty).toArray)
        }
        createEnv(node, x.environment_variables, env_ids)

      case x: Software =>
        transactionOpt(DbService.graphDB) {
          node.setProperty("name", x.name)
          node.setProperty("cpe", x.cpe.getOrElse(""))
          node.setProperty("languages", x.languages.getOrElse(List.empty).toArray)
          node.setProperty("vendor", x.vendor.getOrElse(""))
          node.setProperty("version", x.version.getOrElse(""))
        }

      case x: URL =>
        transactionOpt(DbService.graphDB) {
          node.setProperty("value", x.value)
        }

      case x: UserAccount =>
        transactionOpt(DbService.graphDB) {
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
        transactionOpt(DbService.graphDB) {
          node.setProperty("key", x.key)
          // todo values Option[List[WindowsRegistryValueType]]
          node.setProperty("modified", x.modified.getOrElse("").toString)
          node.setProperty("creator_user_ref", x.creator_user_ref.getOrElse(""))
          node.setProperty("number_of_subkeys", x.number_of_subkeys.getOrElse(0))
        }

      case x: X509Certificate =>
        val hashes_ids: Map[String, String] = (for (s <- x.hashes.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        transactionOpt(DbService.graphDB) {
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
          // todo x509_v3_extensions: Option[X509V3ExtenstionsType]
        }
        createHashes(node, x.hashes, hashes_ids)

      case _ =>
    }
  }

  private def createEnv(sourceNode: Node, envOpt: Option[Map[String, String]], ids: Map[String, String]) = {
    envOpt.foreach(env =>
      for ((k, obs) <- env) {
        val nodeOpt =
          transactionOpt(DbService.graphDB) {
            val theNode = DbService.graphDB.createNode(label("environment_variables"))
            theNode.setProperty("environment_variables_id", ids(k))
            theNode.setProperty(k, obs)
            DbService.environment_variables_idIndex.add(theNode, "environment_variables_id", theNode.getProperty("environment_variables_id"))
            theNode
          }
        nodeOpt.foreach(node => {
          transactionOpt(DbService.graphDB) {
            sourceNode.createRelationshipTo(node, "HAS_ENVIRONMENT_VARIABLE")
          }.getOrElse(println("---> could not process HAS_ENVIRONMENT_VARIABLE relation"))
        })
      }
    )
  }

  private def createHeaders(sourceNode: Node, headersOpt: Option[Map[String, String]], ids: Map[String, String]) = {
    headersOpt.foreach(headers =>
      for ((k, obs) <- headers) {
        val nodeOpt =
          transactionOpt(DbService.graphDB) {
            val theNode = DbService.graphDB.createNode(label("additional_header_fields"))
            theNode.setProperty("additional_header_fields_id", ids(k))
            theNode.setProperty(k, obs)
            DbService.additional_header_fields_idIndex.add(theNode, "additional_header_fields_id", theNode.getProperty("additional_header_fields_id"))
            theNode
          }
        nodeOpt.foreach(node => {
          transactionOpt(DbService.graphDB) {
            sourceNode.createRelationshipTo(node, "HAS_ADDITIONAL_HEADER_FIELD")
          }.getOrElse(println("---> could not process HAS_ADDITIONAL_HEADER_FIELD relation"))
        })
      }
    )
  }

  private def createIpfix(sourceNode: Node, ipfixOpt: Option[Map[String, Either[Int, String]]], ids: Map[String, String]) = {
    ipfixOpt.foreach(ipfix =>
      for ((k, obs) <- ipfix) {
        // either a int or string
        val theValue = obs match {
          case Right(x) => x
          case Left(x) => x
        }
        val nodeOpt =
          transactionOpt(DbService.graphDB) {
            val theNode = DbService.graphDB.createNode(label(asCleanLabel("ipfix")))
            theNode.setProperty("ipfix_id", ids(k))
            theNode.setProperty(k, theValue)
            DbService.ipfix_idIndex.add(theNode, "ipfix_id", theNode.getProperty("ipfix_id"))
            theNode
          }

        nodeOpt.foreach(node => {
          transactionOpt(DbService.graphDB) {
            sourceNode.createRelationshipTo(node, "HAS_IPFIX")
          }.getOrElse(println("---> could not process HAS_IPFIX relation"))
        })
      }
    )
  }

}
