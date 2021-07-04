package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._

object MemorySrc extends ChiselEnum {
  val alu_val: Type = Value(1.U)
  val mem_addr: Type = Value(2.U)
}

class InsMemoryBundle extends Bundle {
  val mem_rdata: UInt = Input(UInt(32.W))
  val alu_val_ex_ms: UInt = Input(UInt(32.W))
  val regfile_wsrc_sel_ex_ms: Bool = Input(Bool())
  val regfile_waddr_sel_ex_ms: RegFileWAddrSel.Type = Input(RegFileWAddrSel())
  val inst_rd_ex_ms: UInt = Input(UInt(5.W))
  val inst_rt_ex_ms: UInt = Input(UInt(5.W))
  val regfile_we_ex_ms: Bool = Input(Bool())

  val regfile_waddr_sel_ms_wb: RegFileWAddrSel.Type = Output(RegFileWAddrSel())
  val inst_rd_ms_wb: UInt = Output(UInt(5.W))
  val inst_rt_ms_wb: UInt = Output(UInt(5.W))
  val regfile_we_ms_wb: Bool = Output(Bool())
  val regfile_wdata_ms_wb: UInt = Output(UInt(32.W))

}

class InsMemory extends Module {
  val io: InsMemoryBundle = IO(new InsMemoryBundle)
  io.regfile_waddr_sel_ms_wb := io.regfile_waddr_sel_ex_ms
  io.inst_rd_ms_wb := io.inst_rd_ex_ms
  io.inst_rt_ms_wb := io.inst_rt_ex_ms
  io.regfile_we_ms_wb := io.regfile_we_ex_ms
  io.regfile_wdata_ms_wb := Mux(io.regfile_wsrc_sel_ex_ms, io.mem_rdata, io.alu_val_ex_ms)
}
