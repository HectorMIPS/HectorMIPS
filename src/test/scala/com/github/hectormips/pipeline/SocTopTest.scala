package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class SocTopTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SocTop"

  it should "execute and reset" in {
    test(new SocTop()) { c =>

      for (i <- 1 to 5) {
        c.clock.step()
        println(s"pc ===> ${c.io.debug_wb_pc.peek()}, we ===> ${c.io.debug_wb_rf_wen.peek()}")
      }
      // addu r1, r2, r3
      c.io.debug_wb_rf_wnum.expect(3.U)
      c.io.debug_wb_rf_wdata.expect(10.U)

      c.clock.step(1)
      println(s"pc ===> ${c.io.debug_wb_pc.peek()}")
      // subu r16, r17, r18
      c.io.debug_wb_rf_wnum.expect("b10010".U)
      c.io.debug_wb_rf_wdata.expect(0.U)

      c.clock.step()
      // slt r1, r2, r4
      c.io.debug_wb_rf_wnum.expect(4.U)
      c.io.debug_wb_rf_wdata.expect(0.U)

      // addu r0, r0, r0
      c.clock.step(2)
      // addu r3, r3, r3
      c.io.debug_wb_rf_wnum.expect(3.U)
      c.io.debug_wb_rf_wdata.expect(20.U)

      c.clock.step()
      // and r4, r3, r6
      c.io.debug_wb_rf_wnum.expect(6.U)
      c.io.debug_wb_rf_wdata.expect(0.U)
      // jr 24
      println(s"pc ===> ${c.io.debug_wb_pc.peek()}")

      c.clock.step()
      // lui 8, 0x0005
      c.io.debug_wb_rf_wnum.expect(8.U)
      c.io.debug_wb_rf_wdata.expect(0x53240000.U)

      println(s"pc ===> ${c.io.debug_wb_pc.peek()}")

      c.clock.step()

      c.reset.poke(1.B)
      c.clock.step(4)
      c.reset.poke(0.B)
      c.clock.step(4)
      println(s"pc ===> ${c.io.debug_wb_pc.peek()}")
      c.io.debug_wb_rf_wnum.expect(3.U)
      c.io.debug_wb_rf_wdata.expect(10.U)

    }
  }


}
