package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._

object MemorySrc extends ChiselEnum {
  val alu_val : Type = Value(1.U)
  val mem_addr: Type = Value(2.U)
}

class ExecuteMemoryBundle extends WithValid {
  val alu_val_ex_ms          : UInt                 = UInt(32.W)
  val regfile_wsrc_sel_ex_ms : Bool                 = Bool()
  val regfile_waddr_sel_ex_ms: RegFileWAddrSel.Type = RegFileWAddrSel()
  val inst_rd_ex_ms          : UInt                 = UInt(5.W)
  val inst_rt_ex_ms          : UInt                 = UInt(5.W)
  val regfile_we_ex_ms       : Bool                 = Bool()
  val pc_ex_ms_debug         : UInt                 = UInt(32.W)

  override def defaults(): Unit = {
    super.defaults()
    alu_val_ex_ms := 0.U
    regfile_wsrc_sel_ex_ms := 0.B
    regfile_waddr_sel_ex_ms := RegFileWAddrSel.inst_rt
    inst_rd_ex_ms := 0.U
    inst_rt_ex_ms := 0.U
    regfile_we_ex_ms := 0.B
    pc_ex_ms_debug := 0.U
  }
}

class InsMemoryBundle extends WithAllowin {
  val mem_rdata: UInt                  = Input(UInt(32.W))
  val ex_ms_in : ExecuteMemoryBundle   = Input(new ExecuteMemoryBundle)
  val ms_wb_out: MemoryWriteBackBundle = Output(new MemoryWriteBackBundle)

  val bypass_ms_id: BypassMsgBundle = Output(new BypassMsgBundle)
}

class InsMemory extends Module {
  val io: InsMemoryBundle = IO(new InsMemoryBundle)
  io.ms_wb_out.regfile_waddr_sel_ms_wb := io.ex_ms_in.regfile_waddr_sel_ex_ms
  io.ms_wb_out.inst_rd_ms_wb := io.ex_ms_in.inst_rd_ex_ms
  io.ms_wb_out.inst_rt_ms_wb := io.ex_ms_in.inst_rt_ex_ms
  io.ms_wb_out.regfile_we_ms_wb := io.ex_ms_in.regfile_we_ex_ms
  io.ms_wb_out.regfile_wdata_ms_wb := Mux(io.ex_ms_in.regfile_wsrc_sel_ex_ms, io.mem_rdata, io.ex_ms_in.alu_val_ex_ms)


  val bus_valid: Bool = Wire(Bool())
  bus_valid := io.ex_ms_in.bus_valid && !reset.asBool()
  io.this_allowin := io.next_allowin && !reset.asBool()
  io.ms_wb_out.bus_valid := bus_valid
  io.ms_wb_out.pc_ms_wb := io.ex_ms_in.pc_ex_ms_debug

  io.bypass_ms_id.reg_valid := bus_valid && io.ex_ms_in.regfile_we_ex_ms
  io.bypass_ms_id.reg_data := Mux(io.ex_ms_in.regfile_wsrc_sel_ex_ms, io.mem_rdata, io.ex_ms_in.alu_val_ex_ms)
  io.bypass_ms_id.reg_addr := Mux(io.ex_ms_in.regfile_waddr_sel_ex_ms === RegFileWAddrSel.inst_rd,
    io.ex_ms_in.inst_rd_ex_ms, io.ex_ms_in.inst_rt_ex_ms)
}
