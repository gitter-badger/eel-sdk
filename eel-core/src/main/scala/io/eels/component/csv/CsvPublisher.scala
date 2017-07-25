package io.eels.component.csv

import java.io.InputStream

import com.sksamuel.exts.Logging
import com.sksamuel.exts.io.Using
import com.univocity.parsers.csv.CsvParser
import io.eels.Row
import io.eels.datastream.{DataStream, Publisher, Subscriber}
import io.eels.schema.StructType

class CsvPublisher(createParser: () => CsvParser,
                   inputFn: () => InputStream,
                   header: Header,
                   skipBadRows: Boolean,
                   schema: StructType) extends Publisher[Seq[Row]] with Logging with Using {

  val rowsToSkip: Int = header match {
    case Header.FirstRow => 1
    case _ => 0
  }

  override def subscribe(subscriber: Subscriber[Seq[Row]]): Unit = {
    using(inputFn()) { input =>

      val parser = createParser()

      try {

        parser.beginParsing(input)

        val iterator = Iterator.continually(parser.parseNext).takeWhile(_ != null).drop(rowsToSkip).map { records =>
          Row(schema, records.toVector)
        }

        iterator.grouped(DataStream.batchSize).foreach(subscriber.next)
        subscriber.completed()

      } catch {
        case t: Throwable => subscriber.error(t)
      } finally {
        parser.stopParsing()
        input.close()
      }
    }
  }
}