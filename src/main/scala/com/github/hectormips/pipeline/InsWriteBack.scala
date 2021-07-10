package com.github.hectormips.pipeline

import chisel3._
import chisel3.util._

class MemoryWriteBackBundle extends WithValid {
  val regfile_waddr_sel_ms_wb: RegFileWAddrSel.Type = RegFileWAddrSel()
  val inst_rd_ms_wb          : UInt                 = UInt(5.W)
  val inst_rt_ms_wb          : UInt                 = UInt(5.W)
  val regfile_we_ms_wb       : Bool                 = Bool()
  val regfile_wdata_ms_wb    : UInt                 = UInt(32.W)
  val pc_ms_wb               : UInt                 = UInt(32.W)

  override def defaults(): Unit = {
    super.defaults()
    regfile_waddr_sel_ms_wb := RegFileWAddrSel.inst_rt
    inst_rd_ms_wb := 0.U
    inst_rt_ms_wb := 0.U
    regfile_we_ms_wb := 0.B
    regfile_wdata_ms_wb := 0.U
    pc_ms_wb := 0.U
  }
}

class InsWriteBackBundle extends WithAllowin {

  val ms_wb_in: MemoryWriteBackBundle = Input(new MemoryWriteBackBundle)

  val regfile_wdata: UInt = Output(UInt(32.W))
  val regfile_waddr: UInt = Output(UInt(5.W))
  val regfile_we   : Bool = Output(Bool())
  val wb_valid     : Bool = Output(Bool())
  val pc_wb        : UInt = Output(UInt(32.W))

  val bypass_wb_id: BypassMsgBundle = Output(new BypassMsgBundle)
}

class InsWriteBack extends Module {
  val io: InsWriteBackBundle = IO(new InsWriteBackBundle)
  io.regfile_we := io.ms_wb_in.regfile_we_ms_wb && io.ms_wb_in.bus_valid
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

  val bus_valid: Bool = Wire(Bool())
  bus_valid := !reset.asBool() && io.ms_wb_in.bus_valid

  io.this_allowin := !reset.asBool()
  io.wb_valid := bus_valid
  io.pc_wb := io.ms_wb_in.pc_ms_wb

  io.bypass_wb_id.reg_valid := bus_valid && io.ms_wb_in.regfile_we_ms_wb
  io.bypass_wb_id.reg_data := io.ms_wb_in.regfile_wdata_ms_wb
  io.bypass_wb_id.reg_addr := Mux1H(Seq(
    (io.ms_wb_in.regfile_waddr_sel_ms_wb === RegFileWAddrSel.inst_rd) -> io.ms_wb_in.inst_rd_ms_wb,
    (io.ms_wb_in.regfile_waddr_sel_ms_wb === RegFileWAddrSel.inst_rt) -> io.ms_wb_in.inst_rt_ms_wb,
    (io.ms_wb_in.regfile_waddr_sel_ms_wb === RegFileWAddrSel.const_31) -> 31.U))
}
