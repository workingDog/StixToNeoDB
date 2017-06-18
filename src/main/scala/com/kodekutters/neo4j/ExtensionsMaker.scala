package com.kodekutters.neo4j

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
            transaction(DbService.graphDB) {
              xNode.setProperty("sid", x.sid.getOrElse(""))
              // todo alternate_data_streams
            }

          case x: PdfFileExt =>
            transaction(DbService.graphDB) {
              xNode.setProperty("version", x.version.getOrElse(""))
              xNode.setProperty("is_optimized", x.is_optimized.getOrElse(false))
              xNode.setProperty("pdfid0", x.pdfid0.getOrElse(""))
              xNode.setProperty("pdfid1", x.pdfid1.getOrElse(""))
            }

          case x: RasterImgExt =>
            transaction(DbService.graphDB) {
              xNode.setProperty("image_height", x.image_height.getOrElse(0))
              xNode.setProperty("image_width", x.image_width.getOrElse(0))
              xNode.setProperty("bits_per_pixel", x.bits_per_pixel.getOrElse(0))
              xNode.setProperty("image_compression_algorithm", x.image_compression_algorithm.getOrElse(""))
              // todo exif_tags
            }

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

}
