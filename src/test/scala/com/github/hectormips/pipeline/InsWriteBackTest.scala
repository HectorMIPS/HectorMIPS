package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class InsWriteBackTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "InsMemory"

  it should "write back" in {
    test(new InsWriteBack()) { c =>
      c.io.ms_wb_in.regfile_waddr_sel_ms_wb.poke(RegFileWAddrSel.inst_rd)
      c.io.ms_wb_in.inst_rd_ms_wb.poke(2.U)
      c.io.ms_wb_in.inst_rt_ms_wb.poke(3.U)
      c.io.ms_wb_in.regfile_we_ms_wb.poke(1.B)
      c.io.ms_wb_in.regfile_wdata_ms_wb.poke(114515.U)

      c.io.regfile_waddr.expect(2.U)
      c.io.regfile_wdata.expect(114515.U)
      c.io.regfile_we.expect(1.B)

      c.io.ms_wb_in.regfile_waddr_sel_ms_wb.poke(RegFileWAddrSel.inst_rt)
      c.io.regfile_waddr.expect(3.U)

      c.io.ms_wb_in.regfile_waddr_sel_ms_wb.poke(RegFileWAddrSel.const_31)
      c.io.regfile_waddr.expect(31.U)
    }
  }


}
