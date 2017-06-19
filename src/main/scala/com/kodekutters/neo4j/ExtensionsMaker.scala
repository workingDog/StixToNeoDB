package com.kodekutters.neo4j

import java.util.UUID

import com.kodekutters.stix._
import org.neo4j.graphdb.Label.label
import org.neo4j.graphdb.{Node, RelationshipType}

/**
  * create an Extension node and associated relations
  */
object ExtensionsMaker {

  import MakerSupport._
  import DbService._

  /**
    * create the Extension nodes and embedded relation for the Observable object
    *
    * @param theObsId  the Observable object id
    * @param extMapOpt the Extensions
    * @param ext_ids   the Extensions ids
    */
  def create(theObsId: String, extMapOpt: Option[Map[String, Extension]], ext_ids: Map[String, String]) = {
    extMapOpt.foreach(extMap => {
      // for each extension
      for ((k, extention) <- extMap) {
        // create the Extension node
        var xNode: Node = null
        transaction(DbService.graphDB) {
          xNode = DbService.graphDB.createNode(label(asCleanLabel(extention.`type`)))
          xNode.addLabel(label("Extension"))
          xNode.setProperty("type", extention.`type`)
          xNode.setProperty("extension_id", ext_ids(k))
          DbService.extension_idIndex.add(xNode, "extension_id", xNode.getProperty("extension_id"))
        }
        // create a relation between the parent Observable node and this Extension node
        transaction(DbService.graphDB) {
          val sourceNode = DbService.observable_idIndex.get("observable_id", theObsId).getSingle
          sourceNode.createRelationshipTo(xNode, RelationshipType.withName("HAS_EXTENSION"))
        }
        // add the specific attributes to the extension node
        extention match {
          case x: ArchiveFileExt =>
            transaction(DbService.graphDB) {
              xNode.setProperty("contains_refs", x.contains_refs.getOrElse(List.empty).toArray)
              xNode.setProperty("version", x.version.getOrElse(""))
              xNode.setProperty("comment", x.comment.getOrElse(""))
            }

          case x: NTFSFileExt =>
            val altStream_ids = toIdArray(x.alternate_data_streams)
            transaction(DbService.graphDB) {
              xNode.setProperty("sid", x.sid.getOrElse(""))
              xNode.setProperty("alternate_data_streams", altStream_ids)
            }
            createAltDataStream(ext_ids(k), x.alternate_data_streams, altStream_ids)

          case x: PdfFileExt =>
            transaction(DbService.graphDB) {
              xNode.setProperty("version", x.version.getOrElse(""))
              xNode.setProperty("is_optimized", x.is_optimized.getOrElse(false))
              xNode.setProperty("pdfid0", x.pdfid0.getOrElse(""))
              xNode.setProperty("pdfid1", x.pdfid1.getOrElse(""))
            }

          case x: RasterImgExt =>
            val exitTags_ids: Map[String, String] = (for (s <- x.exif_tags.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
            transaction(DbService.graphDB) {
              xNode.setProperty("image_height", x.image_height.getOrElse(0))
              xNode.setProperty("image_width", x.image_width.getOrElse(0))
              xNode.setProperty("bits_per_pixel", x.bits_per_pixel.getOrElse(0))
              xNode.setProperty("exif_tags", exitTags_ids.values.toArray)
              xNode.setProperty("image_compression_algorithm", x.image_compression_algorithm.getOrElse(""))
            }
            createExifTags(ext_ids(k), x.exif_tags, exitTags_ids)

          case x: WindowPEBinExt =>
            transaction(DbService.graphDB) {
              xNode.setProperty("pe_type", x.pe_type)
              xNode.setProperty("imphash", x.imphash.getOrElse(""))
              xNode.setProperty("machine_hex", x.machine_hex.getOrElse(""))
              xNode.setProperty("number_of_sections", x.number_of_sections.getOrElse(0))
              xNode.setProperty("time_date_stamp", x.time_date_stamp.getOrElse("").toString)
              xNode.setProperty("pointer_to_symbol_table_hex", x.pointer_to_symbol_table_hex.getOrElse(""))
              xNode.setProperty("number_of_symbols", x.number_of_symbols.getOrElse(0))
              xNode.setProperty("size_of_optional_header", x.size_of_optional_header.getOrElse(0))
              xNode.setProperty("characteristics_hex", x.characteristics_hex.getOrElse(""))
              // todo file_header_hashes
              // todo optional_header
              // todo sections
            }

          case _ =>
        }
      }
    })
  }

  private def createAltDataStream(extIdString: String, altStreamOpt: Option[List[AlternateDataStream]], ids: Array[String]) = {
    altStreamOpt.foreach(altStream => {
      for ((kp, i) <- altStream.zipWithIndex) {
        val hashes_ids: Map[String, String] = (for (s <- kp.hashes.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        var stixNode: Node = null
        transaction(DbService.graphDB) {
          val stixNode = DbService.graphDB.createNode(label(asCleanLabel(kp.`type`)))
          stixNode.setProperty("alternate_data_stream_id", ids(i))
          stixNode.setProperty("name", kp.name)
          stixNode.setProperty("size", kp.size.getOrElse(0))
          stixNode.setProperty("hashes", hashes_ids.values.toArray)
          DbService.altStream_idIndex.add(stixNode, "alternate_data_stream_id", stixNode.getProperty("alternate_data_stream_id"))
        }
        createHashes(ids(i), kp.hashes, hashes_ids)
        transaction(DbService.graphDB) {
          val sourceNode = DbService.extension_idIndex.get("extension_id", extIdString).getSingle
          sourceNode.createRelationshipTo(stixNode, RelationshipType.withName("HAS_ALTERNATE_DATA_STREAM"))
        }
      }
    })
  }

  // create the hashes objects and their relationship to the AlternateDataStream node
  private def createHashes(altIdString: String, hashesOpt: Option[Map[String, String]], ids: Map[String, String]) = {
    hashesOpt.foreach(hashes =>
      for ((k, obs) <- hashes) {
        var hashNode: Node = null
        transaction(DbService.graphDB) {
          hashNode = DbService.graphDB.createNode(label("hashes"))
          hashNode.setProperty("hash_id", ids(k))
          hashNode.setProperty(k, obs)
          DbService.hash_idIndex.add(hashNode, "hash_id", hashNode.getProperty("hash_id"))
        }
        transaction(DbService.graphDB) {
          val sourceNode = DbService.altStream_idIndex.get("alternate_data_stream_id", altIdString).getSingle
          sourceNode.createRelationshipTo(hashNode, RelationshipType.withName("HAS_HASHES"))
        }
      }
    )
  }

  private def createExifTags(extIdString: String, exitTagsOpt: Option[Map[String, Either[Int, String]]], ids: Map[String, String]) = {
    exitTagsOpt.foreach(exitTags =>
      for ((k, obs) <- exitTags) {
        // either a int or string
        val tagValue = obs match {
          case Right(x) => x
          case Left(x)  => x
        }
        var node: Node = null
        transaction(DbService.graphDB) {
          node = DbService.graphDB.createNode(label(asCleanLabel("exif_tags")))
          node.setProperty("exif_tags_id", ids(k))
          node.setProperty(k, tagValue)
          DbService.exif_tags_idIndex.add(node, "exif_tags_id", node.getProperty("exif_tags_id"))
        }
        transaction(DbService.graphDB) {
          val sourceNode = DbService.extension_idIndex.get("extension_id", extIdString).getSingle
          sourceNode.createRelationshipTo(node, RelationshipType.withName("HAS_EXIF_TAGS"))
        }
      }
    )
  }

}
