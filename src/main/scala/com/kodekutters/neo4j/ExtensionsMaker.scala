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
          xNode.setProperty("name", k) // todo <--- not part of the specs
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
          case x: PdfFileExt =>
          case x: RasterImgExt =>
          case x: WindowPEBinExt =>
          case _ =>
        }
      }
    })
  }

}
