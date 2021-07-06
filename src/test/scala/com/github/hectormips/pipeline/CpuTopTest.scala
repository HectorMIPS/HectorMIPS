package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class CpuTopTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "CpuTop"

  it should "execute 5 commands" in {
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

      c.clock.step()
      // addu r3, r3, r3
      c.io.regfile_wb_addr_debug.expect(3.U)
      c.io.regfile_wb_data_debug.expect(20.U)

      c.clock.step()
      // and r4, r3, r6
      c.io.regfile_wb_addr_debug.expect(6.U)
      c.io.regfile_wb_data_debug.expect(0.U)


    }
  }


}
