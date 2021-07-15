package com.github.hectormips.utils

import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation
import org.scalatest._

class LruTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Lru"

  it should "Lru without valid" in {
    test(new Lru(8)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)

      c.clock.step(1)
      c.io.en_visitor.poke(1.B)
      c.io.visitor.poke(1.U)
      c.clock.step(1)
      c.io.visitor.poke(2.U)
      c.clock.step(1)
      c.io.visitor.poke(3.U)
      c.clock.step(1)
      c.io.visitor.poke(4.U)
      c.clock.step(1)
      c.io.next.expect(0.U)
      c.io.visitor.poke(0.U)
      c.clock.step(1)
      c.io.next.expect(5.U)
      c.io.visitor.poke(5.U)
      c.clock.step(1)
      c.io.next.expect(6.U)
      c.io.visitor.poke(6.U)
      c.clock.step(1)
      c.io.next.expect(7.U)
      c.io.visitor.poke(7.U)
      c.clock.step(1)
      c.io.next.expect(1.U)
    }
  }
}
