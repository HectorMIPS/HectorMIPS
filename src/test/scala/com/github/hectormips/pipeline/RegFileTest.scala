package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation
import org.scalatest._

class RegFileTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "RegFile"

  it should "write and read" in {
    test(new RegFile(0)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      for (i <- 0 until 31) {
        c.io.we.poke(true.B)
        c.io.waddr.poke(i.U)
        c.io.wdata.poke((i * 13 + 1).U)
        c.clock.step(1)
        if (i > 1) {
          c.io.raddr1.poke((i - 1).U)
          c.io.rdata1.expect(((i - 1) * 13 + 1).U)
        }
      }
      c.io.we.poke(true.B)
      c.io.waddr.poke(0.U)
      c.io.wdata.poke(114.U)
      c.clock.step(1)
      c.io.raddr2.poke(0.U)
      c.io.raddr2.expect(0.U)

    }
  }
}
