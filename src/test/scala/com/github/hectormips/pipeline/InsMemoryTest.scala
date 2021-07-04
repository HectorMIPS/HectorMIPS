package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class InsMemoryTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "InsMemory"

  it should "fetch ram" in {
    test(new InsMemory()) { c =>
      c.io.alu_val_ex_ms.poke(114.U)
      c.io.mem_rdata.poke(514.U)

      c.io.regfile_wsrc_sel_ex_ms.poke(0.B)
      c.io.regfile_wdata_ms_wb.expect(114.U)

      c.io.regfile_wsrc_sel_ex_ms.poke(1.B)
      c.io.regfile_wdata_ms_wb.expect(514.U)
    }
  }


}
