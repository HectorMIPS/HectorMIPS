package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._

object MemorySrc extends ChiselEnum {
  val alu_val : Type = Value(1.U)
  val mem_addr: Type = Value(2.U)
}

object MemRDataSel extends ChiselEnum {
  val byte : Type = Value(1.U)
  val hword: Type = Value(2.U)
  val word : Type = Value(4.U)
}

class ExecuteMemoryBundle extends WithValid {
  val alu_val_ex_ms                   : UInt                 = UInt(32.W)
  val regfile_wsrc_sel_ex_ms          : Bool                 = Bool()
  val regfile_waddr_sel_ex_ms         : RegFileWAddrSel.Type = RegFileWAddrSel()
  val inst_rd_ex_ms                   : UInt                 = UInt(5.W)
  val inst_rt_ex_ms                   : UInt                 = UInt(5.W)
  val regfile_we_ex_ms                : Bool                 = Bool()
  val pc_ex_ms_debug                  : UInt                 = UInt(32.W)
  val mem_rdata_offset                : UInt                 = UInt(2.W)
  val mem_rdata_sel_ex_ms             : MemRDataSel.Type     = MemRDataSel() // 假设数据已经将指定地址对齐到最低位
  val mem_rdata_extend_is_signed_ex_ms: Bool                 = Bool()
  val cp0_wen_ex_ms                   : Bool                 = Bool()
  val cp0_addr_ex_ms                  : UInt                 = UInt(5.W)
  val cp0_sel_ex_ms                   : UInt                 = UInt(3.W)
  val regfile_wdata_from_cp0_ex_ms    : Bool                 = Bool()

  override def defaults(): Unit = {
    super.defaults()
    alu_val_ex_ms := 0.U
    regfile_wsrc_sel_ex_ms := 0.B
    regfile_waddr_sel_ex_ms := RegFileWAddrSel.inst_rt
    inst_rd_ex_ms := 0.U
    inst_rt_ex_ms := 0.U
    regfile_we_ex_ms := 0.B
    pc_ex_ms_debug := 0.U
    mem_rdata_offset := 0.U
    mem_rdata_sel_ex_ms := MemRDataSel.word
    mem_rdata_extend_is_signed_ex_ms := 0.B
    cp0_wen_ex_ms := 0.B
    cp0_addr_ex_ms := 0.U
    cp0_sel_ex_ms := 0.U
    regfile_wdata_from_cp0_ex_ms := 0.B
  }
}

class InsMemoryBundle extends WithAllowin {
  val mem_rdata: UInt                  = Input(UInt(32.W))
  val ex_ms_in : ExecuteMemoryBundle   = Input(new ExecuteMemoryBundle)
  val ms_wb_out: MemoryWriteBackBundle = Output(new MemoryWriteBackBundle)

  val bypass_ms_id           : BypassMsgBundle = Output(new BypassMsgBundle)
  val cp0_hazard_bypass_ms_ex: CP0HazardBypass = Output(new CP0HazardBypass)
}

class InsMemory extends Module {
  val io: InsMemoryBundle = IO(new InsMemoryBundle)
  io.ms_wb_out.regfile_waddr_sel_ms_wb := io.ex_ms_in.regfile_waddr_sel_ex_ms
  io.ms_wb_out.inst_rd_ms_wb := io.ex_ms_in.inst_rd_ex_ms
  io.ms_wb_out.inst_rt_ms_wb := io.ex_ms_in.inst_rt_ex_ms
  io.ms_wb_out.regfile_we_ms_wb := io.ex_ms_in.regfile_we_ex_ms
  val mem_rdata_fixed: UInt      = Wire(UInt(32.W))
  val mem_rdata_vec  : Vec[UInt] = Wire(Vec(4, UInt(8.W)))
  for (i <- 0 until 4) {
    mem_rdata_vec(i) := mem_rdata_fixed(31 - i * 8, 24 - i * 8)
  }
  val mem_rdata_offset_byte: UInt = Wire(UInt(5.W))
  // 以字节为单位进行位移操作
  mem_rdata_offset_byte := io.ex_ms_in.mem_rdata_offset << 3.U
  mem_rdata_fixed := (io.mem_rdata >> mem_rdata_offset_byte).asUInt()
  val mem_rdata_out: UInt = Wire(UInt(32.W))

  def extendBySignFlag(sign_bit: Bool, width: Int): UInt = {
    VecInit(Seq.fill(width)(io.ex_ms_in.mem_rdata_extend_is_signed_ex_ms & sign_bit)).asUInt()
  }

  mem_rdata_out := MuxCase(mem_rdata_fixed, Seq(
    (io.ex_ms_in.mem_rdata_sel_ex_ms === MemRDataSel.byte) -> Cat(extendBySignFlag(mem_rdata_fixed(7), 24), mem_rdata_fixed(7, 0)),
    (io.ex_ms_in.mem_rdata_sel_ex_ms === MemRDataSel.hword) -> Cat(extendBySignFlag(mem_rdata_fixed(15), 16), mem_rdata_fixed(15, 0)),
    (io.ex_ms_in.mem_rdata_sel_ex_ms === MemRDataSel.word) -> mem_rdata_fixed
  ))


  io.ms_wb_out.regfile_wdata_ms_wb := Mux(io.ex_ms_in.regfile_wsrc_sel_ex_ms, mem_rdata_out, io.ex_ms_in.alu_val_ex_ms)


  val bus_valid: Bool = Wire(Bool())
  bus_valid := io.ex_ms_in.bus_valid && !reset.asBool()
  io.this_allowin := io.next_allowin && !reset.asBool()
  io.ms_wb_out.bus_valid := bus_valid
  io.ms_wb_out.pc_ms_wb := io.ex_ms_in.pc_ex_ms_debug

  io.bypass_ms_id.reg_valid := bus_valid && io.ex_ms_in.regfile_we_ex_ms
  io.bypass_ms_id.reg_data := Mux(io.ex_ms_in.regfile_wsrc_sel_ex_ms, io.mem_rdata, io.ex_ms_in.alu_val_ex_ms)
  io.bypass_ms_id.reg_addr := 0.U
  io.bypass_ms_id.reg_addr := Mux1H(Seq(
    (io.ex_ms_in.regfile_waddr_sel_ex_ms === RegFileWAddrSel.inst_rd) -> io.ex_ms_in.inst_rd_ex_ms,
    (io.ex_ms_in.regfile_waddr_sel_ex_ms === RegFileWAddrSel.inst_rt) -> io.ex_ms_in.inst_rt_ex_ms,
    (io.ex_ms_in.regfile_waddr_sel_ex_ms === RegFileWAddrSel.const_31) -> 31.U))
  io.bypass_ms_id.force_stall := io.ms_wb_out.regfile_wdata_from_cp0_ms_wb

  io.ms_wb_out.cp0_addr_ms_wb := io.ex_ms_in.cp0_addr_ex_ms
  io.ms_wb_out.cp0_wen_ms_wb := io.ex_ms_in.cp0_wen_ex_ms
  io.ms_wb_out.cp0_sel_ms_wb := io.ex_ms_in.cp0_sel_ex_ms
  io.ms_wb_out.regfile_wdata_from_cp0_ms_wb := io.ex_ms_in.regfile_wdata_from_cp0_ex_ms

  io.cp0_hazard_bypass_ms_ex.bus_valid := bus_valid
  io.cp0_hazard_bypass_ms_ex.cp0_en := io.ex_ms_in.regfile_wdata_from_cp0_ex_ms || io.ex_ms_in.cp0_wen_ex_ms
}
