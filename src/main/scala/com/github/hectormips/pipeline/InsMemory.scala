package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.stage.ChiselStage
import chisel3.util._
import com.github.hectormips.RamState
import com.github.hectormips.pipeline.cp0.CP0Const

object MemorySrc extends ChiselEnum {
  val alu_val : Type = Value(1.U)
  val mem_addr: Type = Value(2.U)
}

object MemDataSel extends ChiselEnum {
  val byte : Type = Value(1.U)
  val hword: Type = Value(2.U)
  val word : Type = Value(4.U)
}

class ExecuteMemoryBundle extends WithVEI {
  val alu_val_ex_ms                   : UInt                 = UInt(32.W)
  val regfile_wsrc_sel_ex_ms          : Bool                 = Bool()
  val regfile_waddr_sel_ex_ms         : RegFileWAddrSel.Type = RegFileWAddrSel()
  val inst_rd_ex_ms                   : UInt                 = UInt(5.W)
  val inst_rt_ex_ms                   : UInt                 = UInt(5.W)
  val regfile_we_ex_ms                : Bool                 = Bool()
  val pc_ex_ms_debug                  : UInt                 = UInt(32.W)
  val mem_rdata_offset                : UInt                 = UInt(2.W)
  val mem_rdata_sel_ex_ms             : MemDataSel.Type      = MemDataSel() // 假设数据已经将指定地址对齐到最低位
  val mem_rdata_extend_is_signed_ex_ms: Bool                 = Bool()
  val cp0_wen_ex_ms                   : Bool                 = Bool()
  val cp0_addr_ex_ms                  : UInt                 = UInt(5.W)
  val cp0_sel_ex_ms                   : UInt                 = UInt(3.W)
  val regfile_wdata_from_cp0_ex_ms    : Bool                 = Bool()
  val mem_req                         : Bool                 = Bool()
  val mem_wen                         : Bool                 = Bool()

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
    mem_rdata_sel_ex_ms := MemDataSel.word
    mem_rdata_extend_is_signed_ex_ms := 0.B
    cp0_wen_ex_ms := 0.B
    cp0_addr_ex_ms := 0.U
    cp0_sel_ex_ms := 0.U
    regfile_wdata_from_cp0_ex_ms := 0.B
    mem_req := 0.B
    mem_wen := 0.B
  }
}

class InsMemoryBundle extends WithAllowin {
  val mem_rdata     : Vec[UInt]                  = Input(Vec(2, UInt(32.W)))
  val ex_ms_in      : Vec[ExecuteMemoryBundle]   = Input(Vec(2, new ExecuteMemoryBundle))
  val data_ram_state: Vec[RamState.Type]         = Input(Vec(2, RamState()))
  val ms_wb_out     : Vec[MemoryWriteBackBundle] = Output(Vec(2, new MemoryWriteBackBundle))

  val bypass_ms_id           : Vec[BypassMsgBundle] = Output(Vec(2, new BypassMsgBundle))
  val cp0_hazard_bypass_ms_ex: Vec[CP0HazardBypass] = Output(Vec(2, new CP0HazardBypass))
  val ram_access_done        : Bool                 = Output(Bool())
}


class InsMemory extends Module {
  val io: InsMemoryBundle = IO(new InsMemoryBundle)


  // 如果有需要请求数据ram的指令则需要等待其访问完毕两条指令才能继续向下行进
  val ready_go: Bool = Mux(io.ex_ms_in(0).mem_req && !io.ex_ms_in(0).mem_wen && io.ex_ms_in(0).bus_valid,
    io.data_ram_state(0) === RamState.waiting_for_read, 1.B) &&
    Mux(io.ex_ms_in(1).mem_req && !io.ex_ms_in(0).mem_wen && io.ex_ms_in(1).bus_valid,
      io.data_ram_state(1) === RamState.waiting_for_read, 1.B)
  io.this_allowin := io.next_allowin && !reset.asBool() && ready_go

  io.ram_access_done := io.ex_ms_in(0).bus_valid && ready_go
  for (i <- 0 to 1) {
    io.ms_wb_out(i).regfile_waddr_sel_ms_wb := io.ex_ms_in(i).regfile_waddr_sel_ex_ms
    io.ms_wb_out(i).inst_rd_ms_wb := io.ex_ms_in(i).inst_rd_ex_ms
    io.ms_wb_out(i).inst_rt_ms_wb := io.ex_ms_in(i).inst_rt_ex_ms
    io.ms_wb_out(i).regfile_we_ms_wb := io.ex_ms_in(i).regfile_we_ex_ms
    io.ms_wb_out(i).issue_num := io.ex_ms_in(i).issue_num
    val mem_rdata_fixed: UInt      = Wire(UInt(32.W))
    val mem_rdata_vec  : Vec[UInt] = Wire(Vec(4, UInt(8.W)))
    for (i <- 0 until 4) {
      mem_rdata_vec(i) := mem_rdata_fixed(31 - i * 8, 24 - i * 8)
    }
    val mem_rdata_offset_byte: UInt = Wire(UInt(5.W))
    // 以字节为单位进行位移操作
    mem_rdata_offset_byte := io.ex_ms_in(i).mem_rdata_offset << 3.U
    mem_rdata_fixed := (io.mem_rdata(i) >> mem_rdata_offset_byte).asUInt()
    val mem_rdata_out: UInt = Wire(UInt(32.W))

    def extendBySignFlag(sign_bit: Bool, width: Int): UInt = {
      VecInit(Seq.fill(width)(io.ex_ms_in(i).mem_rdata_extend_is_signed_ex_ms & sign_bit)).asUInt()
    }

    mem_rdata_out := MuxCase(mem_rdata_fixed, Seq(
      (io.ex_ms_in(i).mem_rdata_sel_ex_ms === MemDataSel.byte) -> Cat(extendBySignFlag(mem_rdata_fixed(7), 24), mem_rdata_fixed(7, 0)),
      (io.ex_ms_in(i).mem_rdata_sel_ex_ms === MemDataSel.hword) -> Cat(extendBySignFlag(mem_rdata_fixed(15), 16), mem_rdata_fixed(15, 0)),
      (io.ex_ms_in(i).mem_rdata_sel_ex_ms === MemDataSel.word) -> mem_rdata_fixed
    ))


    io.ms_wb_out(i).regfile_wdata_ms_wb := Mux(io.ex_ms_in(i).regfile_wsrc_sel_ex_ms, mem_rdata_out, io.ex_ms_in(i).alu_val_ex_ms)


    io.ms_wb_out(i).bus_valid := io.ex_ms_in(i).bus_valid && !reset.asBool() && ready_go
    io.ms_wb_out(i).pc_ms_wb := io.ex_ms_in(i).pc_ex_ms_debug

    val bypass_bus_valid: Bool = io.ex_ms_in(i).bus_valid

    io.bypass_ms_id(i).bus_valid := bypass_bus_valid
    io.bypass_ms_id(i).data_valid := io.ex_ms_in(i).bus_valid && !reset.asBool() && ready_go && io.ex_ms_in(i).regfile_we_ex_ms &&
      Mux(io.ex_ms_in(i).regfile_wsrc_sel_ex_ms, io.data_ram_state(i) === RamState.waiting_for_read, 1.B) &&
      !io.ex_ms_in(i).regfile_wdata_from_cp0_ex_ms
    io.bypass_ms_id(i).reg_data := Mux(io.ex_ms_in(i).regfile_wsrc_sel_ex_ms, mem_rdata_out, io.ex_ms_in(i).alu_val_ex_ms)
    io.bypass_ms_id(i).reg_addr := MuxCase(0.U, Seq(
      (io.ex_ms_in(i).regfile_waddr_sel_ex_ms === RegFileWAddrSel.inst_rd) -> io.ex_ms_in(i).inst_rd_ex_ms,
      (io.ex_ms_in(i).regfile_waddr_sel_ex_ms === RegFileWAddrSel.inst_rt) -> io.ex_ms_in(i).inst_rt_ex_ms,
      (io.ex_ms_in(i).regfile_waddr_sel_ex_ms === RegFileWAddrSel.const_31) -> 31.U))

    io.ms_wb_out(i).cp0_addr_ms_wb := io.ex_ms_in(i).cp0_addr_ex_ms
    io.ms_wb_out(i).cp0_wen_ms_wb := io.ex_ms_in(i).cp0_wen_ex_ms
    io.ms_wb_out(i).cp0_sel_ms_wb := io.ex_ms_in(i).cp0_sel_ex_ms
    io.ms_wb_out(i).regfile_wdata_from_cp0_ms_wb := io.ex_ms_in(i).regfile_wdata_from_cp0_ex_ms
    io.ms_wb_out(i).exception_flags := DontCare

    io.cp0_hazard_bypass_ms_ex(i).bus_valid := bypass_bus_valid
    io.cp0_hazard_bypass_ms_ex(i).cp0_en := io.ex_ms_in(i).regfile_wdata_from_cp0_ex_ms ||
      io.ex_ms_in(i).cp0_wen_ex_ms
    io.cp0_hazard_bypass_ms_ex(i).cp0_ip_wen := io.ex_ms_in(i).cp0_addr_ex_ms === CP0Const.CP0_REGADDR_CAUSE &&
      io.ex_ms_in(i).cp0_sel_ex_ms === 0.U && io.ex_ms_in(i).cp0_wen_ex_ms
  }
}

object InsMemory extends App {
  (new ChiselStage).emitVerilog(new InsMemory)
}
