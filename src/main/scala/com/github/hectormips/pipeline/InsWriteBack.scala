package com.github.hectormips.pipeline

import chisel3.{Mux, _}
import chisel3.util._

class MemoryWriteBackBundle extends WithValid {
  val regfile_waddr_sel_ms_wb     : RegFileWAddrSel.Type = RegFileWAddrSel()
  val inst_rd_ms_wb               : UInt                 = UInt(5.W)
  val inst_rt_ms_wb               : UInt                 = UInt(5.W)
  val regfile_we_ms_wb            : Bool                 = Bool()
  val regfile_wdata_ms_wb         : UInt                 = UInt(32.W)
  val pc_ms_wb                    : UInt                 = UInt(32.W)
  val cp0_wen_ms_wb               : Bool                 = Bool()
  val cp0_addr_ms_wb              : UInt                 = UInt(5.W)
  val cp0_sel_ms_wb               : UInt                 = UInt(3.W)
  val regfile_wdata_from_cp0_ms_wb: Bool                 = Bool()

  override def defaults(): Unit = {
    super.defaults()
    regfile_waddr_sel_ms_wb := RegFileWAddrSel.inst_rt
    inst_rd_ms_wb := 0.U
    inst_rt_ms_wb := 0.U
    regfile_we_ms_wb := 0.B
    regfile_wdata_ms_wb := 0.U
    pc_ms_wb := 0.U
    cp0_wen_ms_wb := 0.B
    cp0_addr_ms_wb := 0.U
    cp0_sel_ms_wb := 0.U
    regfile_wdata_from_cp0_ms_wb := 0.B
  }
}

class InsWriteBackBundle extends WithAllowin {

  val ms_wb_in: MemoryWriteBackBundle = Input(new MemoryWriteBackBundle)

  val regfile_wdata: UInt = Output(UInt(32.W))
  val regfile_waddr: UInt = Output(UInt(5.W))
  val regfile_wen  : Bool = Output(Bool())
  val wb_valid     : Bool = Output(Bool())
  val pc_wb        : UInt = Output(UInt(32.W))

  val cp0_wen  : Bool = Output(Bool())
  val cp0_addr : UInt = Output(UInt(5.W))
  val cp0_rdata: UInt = Input(UInt(32.W))
  val cp0_wdata: UInt = Output(UInt(32.W))
  val cp0_sel  : UInt = Output(UInt(3.W))

  val bypass_wb_id           : BypassMsgBundle = Output(new BypassMsgBundle)
  val cp0_hazard_bypass_wb_ex: CP0HazardBypass = Output(new CP0HazardBypass)
}

class InsWriteBack extends Module {
  val io: InsWriteBackBundle = IO(new InsWriteBackBundle)
  io.regfile_wen := io.ms_wb_in.regfile_we_ms_wb && io.ms_wb_in.bus_valid
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
  val regfile_wdata: UInt = Mux(io.ms_wb_in.regfile_wdata_from_cp0_ms_wb, io.cp0_rdata,
    io.ms_wb_in.regfile_wdata_ms_wb)
  // mfc0的写入值直接在写回阶段取出，简化前递逻辑
  io.regfile_wdata := regfile_wdata

  val bus_valid: Bool = Wire(Bool())
  bus_valid := !reset.asBool() && io.ms_wb_in.bus_valid

  io.this_allowin := !reset.asBool()
  io.wb_valid := bus_valid
  io.pc_wb := io.ms_wb_in.pc_ms_wb

  io.cp0_wen := io.ms_wb_in.cp0_wen_ms_wb && io.ms_wb_in.bus_valid
  io.cp0_addr := io.ms_wb_in.cp0_addr_ms_wb
  io.cp0_sel := io.ms_wb_in.cp0_sel_ms_wb
  io.cp0_wdata := io.ms_wb_in.regfile_wdata_ms_wb

  io.bypass_wb_id.reg_valid := bus_valid && io.ms_wb_in.regfile_we_ms_wb
  io.bypass_wb_id.reg_data := regfile_wdata
  io.bypass_wb_id.reg_addr := Mux1H(Seq(
    (io.ms_wb_in.regfile_waddr_sel_ms_wb === RegFileWAddrSel.inst_rd) -> io.ms_wb_in.inst_rd_ms_wb,
    (io.ms_wb_in.regfile_waddr_sel_ms_wb === RegFileWAddrSel.inst_rt) -> io.ms_wb_in.inst_rt_ms_wb,
    (io.ms_wb_in.regfile_waddr_sel_ms_wb === RegFileWAddrSel.const_31) -> 31.U))
  // 此时一定可以从cp0取得数据，force_stall用于反馈是否可以结束暂停
  io.bypass_wb_id.force_stall := !io.ms_wb_in.regfile_wdata_from_cp0_ms_wb

  io.cp0_hazard_bypass_wb_ex.bus_valid := bus_valid
  io.cp0_hazard_bypass_wb_ex.cp0_en := io.ms_wb_in.regfile_wdata_from_cp0_ms_wb || io.ms_wb_in.cp0_wen_ms_wb
}
