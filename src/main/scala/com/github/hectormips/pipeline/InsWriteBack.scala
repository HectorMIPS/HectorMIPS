package com.github.hectormips.pipeline

import chisel3._
import chisel3.util._

class MemoryWriteBackBundle extends Bundle {
  val regfile_waddr_sel_ms_wb: RegFileWAddrSel.Type = RegFileWAddrSel()
  val inst_rd_ms_wb          : UInt                 = UInt(5.W)
  val inst_rt_ms_wb          : UInt                 = UInt(5.W)
  val regfile_we_ms_wb       : Bool                 = Bool()
  val regfile_wdata_ms_wb    : UInt                 = UInt(32.W)
}

class InsWriteBackBundle extends Bundle {

  val ms_wb_in: MemoryWriteBackBundle = Input(new MemoryWriteBackBundle)

  val regfile_wdata: UInt = Output(UInt(32.W))
  val regfile_waddr: UInt = Output(UInt(5.W))
  val regfile_we   : Bool = Output(Bool())
}

class InsWriteBack extends Module {
  val io: InsWriteBackBundle = IO(new InsWriteBackBundle)
  io.regfile_we := io.ms_wb_in.regfile_we_ms_wb
  io.regfile_waddr := 0.U
  switch(io.ms_wb_in.regfile_waddr_sel_ms_wb) {
    is(RegFileWAddrSel.inst_rd) {
      io.regfile_waddr := io.ms_wb_in.inst_rd_ms_wb
    }
    is(RegFileWAddrSel.inst_rt) {
      io.regfile_waddr := io.ms_wb_in.inst_rt_ms_wb
    }
    is(RegFileWAddrSel.const_31) {
      io.regfile_waddr := 31.U
    }
  }
  io.regfile_wdata := io.ms_wb_in.regfile_wdata_ms_wb

}
