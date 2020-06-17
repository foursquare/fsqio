package io.fsq.twofishes.indexer.output

import io.fsq.rogue.Iter
import io.fsq.twofishes.core.Indexes
import io.fsq.twofishes.indexer.mongo.NameIndex
import io.fsq.twofishes.indexer.mongo.RogueImplicits._
import io.fsq.twofishes.model.gen.ThriftNameIndex
import io.fsq.twofishes.util.StoredFeatureId
import scala.collection.mutable.{HashSet, ListBuffer}

class NameIndexer(
  override val basepath: String,
  override val fidMap: FidMap,
  outputPrefixIndex: Boolean
) extends Indexer {
  val index = Indexes.NameIndex
  override val outputs = Seq(index) ++
    (if (outputPrefixIndex) {
       Seq(PrefixIndexer.index)
     } else {
       Seq.empty
     })

  def writeIndexImpl() {
    var nameCount = 0
    val nameSize: Long = executor.count(Q(ThriftNameIndex))

    var prefixSet = new HashSet[String]

    var lastName = ""
    val nameFids = new ListBuffer[StoredFeatureId]

    val writer = buildHFileV1Writer(
      index,
      Map("FEATURES_SORTED_BY_STATIC_IMPORTANCE" -> "true")
    )

    def writeFidsForLastName() {
      writer.append(lastName, fidsToCanonicalFids(nameFids.toList.distinct))
      if (outputPrefixIndex) {
        for {
          length <- 1 to math.min(PrefixIndexer.MaxPrefixLength, lastName.size)
        } {
          prefixSet.add(lastName.substring(0, length))
        }
      }
    }

    executor.iterate(
      // sort by nameBytes asc
      // then by eligibility for prefix matching
      // finally static importance desc
      Q(ThriftNameIndex).orderAsc(_.name).andAsc(_.excludeFromPrefixIndex).andDesc(_.pop),
      ()
    )((_: Unit, event: Iter.Event[ThriftNameIndex]) => {
      event match {
        case Iter.OnNext(unwrapped) => {
          val n = new NameIndex(unwrapped)
          if (n.nameOption.exists(_.nonEmpty)) {
            if (lastName != n.nameOrThrow) {
              if (lastName != "") {
                writeFidsForLastName()
              }
              nameFids.clear()
              lastName = n.nameOrThrow
            }

            nameFids.append(n.fidAsFeatureId)

            nameCount += 1
            if (nameCount % 100000 == 0) {
              log.info("processed %d of %d names".format(nameCount, nameSize))
            }
          }
          Iter.Continue(())
        }
        case Iter.OnComplete => Iter.Return(())
        case Iter.OnError(e) => throw e
      }
    })

    writeFidsForLastName()
    writer.close()

    if (outputPrefixIndex) {
      val prefixIndexer = new PrefixIndexer(basepath, fidMap, prefixSet)
      prefixIndexer.writeIndex()
    }
  }
}
