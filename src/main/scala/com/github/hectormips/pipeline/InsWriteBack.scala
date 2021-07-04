package com.github.hectormips.pipeline

import chisel3._
import chisel3.util._

class InsWriteBackBundle extends Bundle {
  val regfile_waddr_sel_ms_wb: RegFileWAddrSel.Type = Input(RegFileWAddrSel())
  val inst_rd_ms_wb: UInt = Input(UInt(5.W))
  val inst_rt_ms_wb: UInt = Input(UInt(5.W))
  val regfile_we_ms_wb: Bool = Input(Bool())
  val regfile_wdata_ms_wb: UInt = Input(UInt(32.W))

  val regfile_wdata: UInt = Output(UInt(32.W))
  val regfile_waddr: UInt = Output(UInt(5.W))
  val regfile_we: Bool = Output(Bool())
}

class InsWriteBack extends Module {
  val io: InsWriteBackBundle = IO(new InsWriteBackBundle)
  io.regfile_we := io.regfile_we_ms_wb
  io.regfile_waddr := 0.U
  switch(io.regfile_waddr_sel_ms_wb) {
    is(RegFileWAddrSel.inst_rd) {
      io.regfile_waddr := io.inst_rd_ms_wb
    }
    is(RegFileWAddrSel.inst_rt) {
      io.regfile_waddr := io.inst_rt_ms_wb
    }
    is(RegFileWAddrSel.const_31) {
      io.regfile_waddr := 31.U
    }
  }
  io.regfile_wdata := io.regfile_wdata_ms_wb

}
