package io.eels

import com.sksamuel.exts.Logging
import io.eels.datastream.{DataStream, DataStreamSource, Publisher}
import io.eels.schema.StructType
import io.eels.util.{JacksonSupport, JsonRow}

/**
  * A Source is a provider of data.
  *
  * A source implementation must provide two methods:
  *
  * 1: schema() which returns an eel schema for the data source.
  *
  * 2: parts() which returns zero or more Part instances representing the data.
  *
  * A part instance is a subset of the data in a Source, and allows for concurrent
  * reading of that data. For example a part could be a single file in a multi-file source, or
  * a partition in a partitioned source.
  */
trait Source extends Logging {
  outer =>

  def schema: StructType

  def parts(): Seq[Publisher[Seq[Row]]]

  def load[T: Manifest]: Seq[T] = {
    toDataStream.collect.map { row =>
      val node = JsonRow(row)
      JacksonSupport.mapper.readerFor[T].readValue(node)
    }
  }

  def toDataStream(): DataStream = new DataStreamSource(this)
  def toDataStream(listener: Listener): DataStream = new DataStreamSource(this).listener(listener)
}