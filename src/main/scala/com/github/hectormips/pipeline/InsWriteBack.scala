package com.github.hectormips.pipeline

import chisel3.{Mux, _}
import chisel3.util._

class MemoryWriteBackBundle extends WithVEI {
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

  val ms_wb_in: Vec[MemoryWriteBackBundle] = Input(Vec(2, new MemoryWriteBackBundle))

  val regfile_wdata: Vec[UInt] = Output(Vec(2, UInt(32.W)))
  val regfile_waddr: Vec[UInt] = Output(Vec(2, UInt(5.W)))
  val regfile_wen  : Vec[Bool] = Output(Vec(2, Bool()))
  val pc_wb        : Vec[UInt] = Output(Vec(2, UInt(32.W)))

  val cp0_rdata: Vec[UInt] = Input(Vec(2, UInt(32.W)))
  val cp0_wen  : Vec[Bool] = Output(Vec(2, Bool()))
  val cp0_addr : Vec[UInt] = Output(Vec(2, UInt(5.W)))
  val cp0_wdata: Vec[UInt] = Output(Vec(2, UInt(32.W)))
  val cp0_sel  : Vec[UInt] = Output(Vec(2, UInt(3.W)))

  val bypass_wb_id           : Vec[BypassMsgBundle] = Output(Vec(2, new BypassMsgBundle))
  val cp0_hazard_bypass_wb_ex: Vec[CP0HazardBypass] = Output(Vec(2, new CP0HazardBypass))
}

class InsWriteBack extends Module {
  val io: InsWriteBackBundle = IO(new InsWriteBackBundle)
  for (i <- 0 to 1) {
    io.regfile_wen(i) := io.ms_wb_in(i).regfile_we_ms_wb && io.ms_wb_in(i).bus_valid
    io.regfile_waddr(i) := 0.U
    switch(io.ms_wb_in(i).regfile_waddr_sel_ms_wb) {
      is(RegFileWAddrSel.inst_rd) {
        io.regfile_waddr(i) := io.ms_wb_in(i).inst_rd_ms_wb
      }
      is(RegFileWAddrSel.inst_rt) {
        io.regfile_waddr(i) := io.ms_wb_in(i).inst_rt_ms_wb
      }
      is(RegFileWAddrSel.const_31) {
        io.regfile_waddr(i) := 31.U
      }
    }
    val regfile_wdata: UInt = Mux(io.ms_wb_in(i).regfile_wdata_from_cp0_ms_wb, io.cp0_rdata(i),
      io.ms_wb_in(i).regfile_wdata_ms_wb)
    // mfc0的写入值直接在写回阶段取出，简化前递逻辑
    io.regfile_wdata(i) := regfile_wdata

    val bus_valid: Bool = Wire(Bool())
    bus_valid := !reset.asBool() && io.ms_wb_in(i).bus_valid

    io.this_allowin := !reset.asBool()
    io.pc_wb := io.ms_wb_in(i).pc_ms_wb

    io.cp0_wen := io.ms_wb_in(i).cp0_wen_ms_wb && io.ms_wb_in(i).bus_valid
    io.cp0_addr := io.ms_wb_in(i).cp0_addr_ms_wb
    io.cp0_sel := io.ms_wb_in(i).cp0_sel_ms_wb
    io.cp0_wdata := io.ms_wb_in(i).regfile_wdata_ms_wb

    io.bypass_wb_id(i).bus_valid := bus_valid && io.ms_wb_in(i).regfile_we_ms_wb
    io.bypass_wb_id(i).data_valid := 1.B
    io.bypass_wb_id(i).reg_data := regfile_wdata
    io.bypass_wb_id(i).reg_addr := Mux1H(Seq(
      (io.ms_wb_in(i).regfile_waddr_sel_ms_wb === RegFileWAddrSel.inst_rd) -> io.ms_wb_in(i).inst_rd_ms_wb,
      (io.ms_wb_in(i).regfile_waddr_sel_ms_wb === RegFileWAddrSel.inst_rt) -> io.ms_wb_in(i).inst_rt_ms_wb,
      (io.ms_wb_in(i).regfile_waddr_sel_ms_wb === RegFileWAddrSel.const_31) -> 31.U))

    io.cp0_hazard_bypass_wb_ex(i).bus_valid := bus_valid
    io.cp0_hazard_bypass_wb_ex(i).cp0_en := io.ms_wb_in(i).regfile_wdata_from_cp0_ms_wb || io.ms_wb_in(i).cp0_wen_ms_wb
    io.cp0_hazard_bypass_wb_ex(i).cp0_ip_wen := io.ms_wb_in(i).cp0_addr_ms_wb === CP0Const.CP0_REGADDR_CAUSE &&
      io.ms_wb_in(i).cp0_sel_ms_wb === 0.U && io.ms_wb_in(i).cp0_wen_ms_wb
  }
}
