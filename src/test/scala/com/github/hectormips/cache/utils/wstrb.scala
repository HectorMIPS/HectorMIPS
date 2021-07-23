package com.github.hectormips.cache.utils

import chisel3._
import chiseltest._
import chiseltest.ChiselScalatestTester
import chiseltest.internal.WriteVcdAnnotation
import org.scalatest.{FlatSpec, Matchers}
import chiseltest.experimental.TestOptionBuilder._

class TestWstrb extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "TestWstrb"
  it should "TestWstrb test good" in {
    test(new Wstrb()).withAnnotations(Seq(WriteVcdAnnotation)) { wstrb =>
      wstrb.io.offset.poke(0.U)
      wstrb.io.size.poke(0.U)
      wstrb.io.mask.expect("b0001".U)

      wstrb.io.offset.poke(1.U)
      wstrb.io.size.poke(0.U)
      wstrb.io.mask.expect("b0010".U)

      wstrb.io.offset.poke(2.U)
      wstrb.io.size.poke(0.U)
      wstrb.io.mask.expect("b0100".U)

      wstrb.io.offset.poke(3.U)
      wstrb.io.size.poke(0.U)
      wstrb.io.mask.expect("b1000".U)

      wstrb.io.offset.poke(0.U)
      wstrb.io.size.poke(1.U)
      wstrb.io.mask.expect("b0011".U)

      wstrb.io.offset.poke(2.U)
      wstrb.io.size.poke(1.U)
      wstrb.io.mask.expect("b1100".U)

      wstrb.io.offset.poke(0.U)
      wstrb.io.size.poke(2.U)
      wstrb.io.mask.expect("b1111".U)

      wstrb.io.offset.poke(1.U)
      wstrb.io.size.poke(2.U)
      wstrb.io.mask.expect("b0000".U)
    }
  }
}