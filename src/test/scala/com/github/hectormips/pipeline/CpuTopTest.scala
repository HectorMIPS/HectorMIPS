package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class CpuTopTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "CpuTop"

  it should "execute and reset" in {
    test(new CpuTop()) { c =>
      c.io.regfile_rdata1.expect(5.U)

      c.clock.step(4)
      // addu r1, r2, r3
      c.io.regfile_wb_addr_debug.expect(3.U)
      c.io.regfile_wb_data_debug.expect(10.U)

      c.clock.step(1)
      // subu r16, r17, r18
      c.io.regfile_wb_addr_debug.expect("b10010".U)
      c.io.regfile_wb_data_debug.expect(0.U)

      c.clock.step()
      // slt r1, r2, r4
      c.io.regfile_wb_addr_debug.expect(4.U)
      c.io.regfile_wb_data_debug.expect(0.U)

      // addu r0, r0, r0
      c.clock.step(2)
      // addu r3, r3, r3
      c.io.regfile_wb_addr_debug.expect(3.U)
      c.io.regfile_wb_data_debug.expect(20.U)

      c.clock.step()
      // and r4, r3, r6
      c.io.regfile_wb_addr_debug.expect(6.U)
      c.io.regfile_wb_data_debug.expect(0.U)
      // jr 24
      c.io.pc_debug.expect(5.U)
      println(s"pc ===> ${c.io.pc_debug.peek()}")

      c.clock.step()
      // lui 8, 0x0005
      c.io.regfile_wb_addr_debug.expect(8.U)
      c.io.regfile_wb_data_debug.expect(0x53240000.U)

      println(s"pc ===> ${c.io.pc_debug.peek()}")

      c.clock.step()

      c.reset.poke(1.B)
      c.clock.step(4)
      c.reset.poke(0.B)
      c.clock.step(4)
      println(s"pc ===> ${c.io.pc_debug.peek()}")
      c.io.regfile_wb_addr_debug.expect(3.U)
      c.io.regfile_wb_data_debug.expect(10.U)

    }
  }

  it should "jump" in {

  }


}
