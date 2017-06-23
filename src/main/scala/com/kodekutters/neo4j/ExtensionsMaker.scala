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
    * @param sourceNode the Observable object
    * @param extMapOpt  the Extensions
    * @param ext_ids    the Extensions ids
    */
  def create(sourceNode: Node, extMapOpt: Option[Map[String, Extension]], ext_ids: Map[String, String]) = {
    extMapOpt.foreach(extMap => {
      // for each extension
      for ((k, extention) <- extMap) {
        // create the Extension node
        val xNodeOpt = transaction {
          val node = DbService.graphDB.createNode(label(asCleanLabel(extention.`type`)))
          node.addLabel(label("Extension"))
          node.setProperty("type", extention.`type`)
          node.setProperty("extension_id", ext_ids(k))
          node
        }
        xNodeOpt match {
          case Some(xNode) =>
            // create a relation between the parent Observable node and this Extension node
            transaction {
              sourceNode.createRelationshipTo(xNode, "HAS_EXTENSION")
            }.getOrElse(println("---> could not process HAS_EXTENSION relation"))

            // add the specific attributes to the extension node
            extention match {
              case x: ArchiveFileExt =>
                transaction {
                  xNode.setProperty("contains_refs", x.contains_refs.getOrElse(List.empty).toArray)
                  xNode.setProperty("version", x.version.getOrElse(""))
                  xNode.setProperty("comment", x.comment.getOrElse(""))
                }

              case x: NTFSFileExt =>
                val altStream_ids = toIdArray(x.alternate_data_streams)
                transaction {
                  xNode.setProperty("sid", x.sid.getOrElse(""))
                  xNode.setProperty("alternate_data_streams", altStream_ids)
                }
                createAltDataStream(xNode, x.alternate_data_streams, altStream_ids)

              case x: PdfFileExt =>
                transaction {
                  xNode.setProperty("version", x.version.getOrElse(""))
                  xNode.setProperty("is_optimized", x.is_optimized.getOrElse(false))
                  xNode.setProperty("pdfid0", x.pdfid0.getOrElse(""))
                  xNode.setProperty("pdfid1", x.pdfid1.getOrElse(""))
                }

              case x: RasterImgExt =>
                val exitTags_ids: Map[String, String] = (for (s <- x.exif_tags.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
                transaction {
                  xNode.setProperty("image_height", x.image_height.getOrElse(0))
                  xNode.setProperty("image_width", x.image_width.getOrElse(0))
                  xNode.setProperty("bits_per_pixel", x.bits_per_pixel.getOrElse(0))
                  xNode.setProperty("exif_tags", exitTags_ids.values.toArray)
                  xNode.setProperty("image_compression_algorithm", x.image_compression_algorithm.getOrElse(""))
                }
                createExifTags(xNode, x.exif_tags, exitTags_ids)

              case x: WindowPEBinExt =>
                transaction {
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

          case None => println("---> could not create node Extension")
        }

      }
    })
  }

  private def createAltDataStream(fromNode: Node, altStreamOpt: Option[List[AlternateDataStream]], ids: Array[String]) = {
    altStreamOpt.foreach(altStream => {
      for ((kp, i) <- altStream.zipWithIndex) {
        val hashes_ids: Map[String, String] = (for (s <- kp.hashes.getOrElse(Map.empty).keySet) yield s -> UUID.randomUUID().toString).toMap
        val tgtNodeOpt = transaction {
          val node = DbService.graphDB.createNode(label(asCleanLabel(kp.`type`)))
          node.setProperty("alternate_data_stream_id", ids(i))
          node.setProperty("name", kp.name)
          node.setProperty("size", kp.size.getOrElse(0))
          node.setProperty("hashes", hashes_ids.values.toArray)
          node
        }
        tgtNodeOpt.foreach(tgtNode => {
          createHashes(tgtNode, kp.hashes, hashes_ids)
          transaction {
            fromNode.createRelationshipTo(tgtNode, "HAS_ALTERNATE_DATA_STREAM")
          }.getOrElse(println("---> could not process HAS_ALTERNATE_DATA_STREAM relation"))
        })
      }
    })
  }

  private def createExifTags(fromNode: Node, exitTagsOpt: Option[Map[String, Either[Int, String]]], ids: Map[String, String]) = {
    exitTagsOpt.foreach(exitTags =>
      for ((k, obs) <- exitTags) {
        // either a int or string
        val theValue = obs match {
          case Right(x) => x
          case Left(x) => x
        }
        val tgtNodeOpt = transaction {
          val node = DbService.graphDB.createNode(label(asCleanLabel("exif_tags")))
          node.setProperty("exif_tags_id", ids(k))
          node.setProperty(k, theValue)
          node
        }
        tgtNodeOpt.foreach(tgtNode => {
          transaction {
            fromNode.createRelationshipTo(tgtNode, "HAS_EXIF_TAGS")
          }.getOrElse(println("---> could not process HAS_EXIF_TAGS relation"))
        })
      }
    )
  }

}
