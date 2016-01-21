// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.server

import io.fsq.spindle.common.thrift.json.TReadableJSONProtocol
import io.fsq.twofishes.gen.{GeocodeServingFeatureEdit, GeocodeServingFeatureEdits}
import java.io.{File, FileOutputStream}
import org.apache.thrift.TSerializer

// TODO: come up with better tooling for building hotfix files
object JsonHotfixFileBuilder {
  // build individual objects here and run build-hotfix-file.py to produce json file
  // DO NOT push commits with changes to this list back to twofishes
/* example: adding UES name to Upper East Side
    GeocodeServingFeatureEdit.newBuilder
      .editType(EditType.Modify)
      .longId(72057594044543258L)
      .namesEdits(List(FeatureNameListEdit.newBuilder
        .editType(EditType.Add)
        .name("UES")
        .lang("en")
        .flagsEdits(List(FeatureNameFlagsListEdit.newBuilder
          .editType(EditType.Add)
          .value(FeatureNameFlags.LOCAL_LANG)
          .result()
        ))
        .result()
      ))
      .result()
*/


  val edits: List[GeocodeServingFeatureEdit] = List(
  )

  val editsWrapper = GeocodeServingFeatureEdits(edits)

  def main(args: Array[String]) {
    if (args.size > 0) {
      val output = new FileOutputStream(new File(args(0)), false)
      val serializer = new TSerializer(new TReadableJSONProtocol.Factory(true))
      output.write(serializer.serialize(editsWrapper))
      output.close()
    } else {
      println(s"Supply an output file name: ${getClass.getName} [filename]")
      System.exit(1)
    }
  }
}
