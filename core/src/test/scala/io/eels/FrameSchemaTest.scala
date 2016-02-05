package io.eels

import org.scalatest.{WordSpec, Matchers}

class FrameSchemaTest extends WordSpec with Matchers {

  val columns = List(Column("a"), Column("b"))
  val frame = Frame(Row(columns, List("1", "2")), Row(columns, List("3", "4")))

  "FrameSchema" should {
    "pretty print in desired format" in {
      frame.schema.print shouldBe "- a [String]\n- b [String]"
    }
  }
}
