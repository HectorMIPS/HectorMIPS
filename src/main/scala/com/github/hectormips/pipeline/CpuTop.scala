package com.github.hectormips.pipeline

import chisel3.util._
import chisel3._
import chisel3.util.experimental.forceName
import chisel3.stage.ChiselStage

import java.io.{File, PrintWriter}
import scala.io.Source

class CpuTopBundle extends Bundle {
  val inst_sram_en   : Bool = Output(Bool())
  val inst_sram_wen  : UInt = Output(UInt(4.W))
  val inst_sram_addr : UInt = Output(UInt(32.W))
  val inst_sram_wdata: UInt = Output(UInt(32.W))
  val inst_sram_rdata: UInt = Input(UInt(32.W))

  val data_sram_en   : Bool = Output(Bool())
  val data_sram_wen  : UInt = Output(UInt(4.W))
  val data_sram_addr : UInt = Output(UInt(32.W))
  val data_sram_wdata: UInt = Output(UInt(32.W))
  val data_sram_rdata: UInt = Input(UInt(32.W))

  val debug_wb_pc      : UInt = Output(UInt(32.W))
  val debug_wb_rf_wen  : UInt = Output(UInt(4.W))
  val debug_wb_rf_wnum : UInt = Output(UInt(5.W))
  val debug_wb_rf_wdata: UInt = Output(UInt(32.W))

  forceName(inst_sram_en, "inst_sram_en")
  forceName(inst_sram_wen, "inst_sram_wen")
  forceName(inst_sram_addr, "inst_sram_addr")
  forceName(inst_sram_wdata, "inst_sram_wdata")
  forceName(inst_sram_rdata, "inst_sram_rdata")

  forceName(data_sram_en, "data_sram_en")
  forceName(data_sram_wen, "data_sram_wen")
  forceName(data_sram_addr, "data_sram_addr")
  forceName(data_sram_wdata, "data_sram_wdata")
  forceName(data_sram_rdata, "data_sram_rdata")

  forceName(debug_wb_pc, "debug_wb_pc")
  forceName(debug_wb_rf_wen, "debug_wb_rf_wen")
  forceName(debug_wb_rf_wnum, "debug_wb_rf_wnum")
  forceName(debug_wb_rf_wdata, "debug_wb_rf_wdata")
}


object RegEnableWithValid {

  def apply[T <: WithValid](next: T, init: T, enable: Bool, valid_enable: Bool): T = {
    val r = RegInit(init)
    when(enable) {
      r := next
    }
    when(valid_enable) {
      r.bus_valid := next.bus_valid
    }
    r
  }
}

class CpuTop(pc_init: Int, reg_init: Int = 0) extends MultiIOModule {
  val io: CpuTopBundle = IO(new CpuTopBundle())

  // 内建寄存器
  // pc重置时默认为0xfffffffc，这样+4得到的就是第一条指令地址
  val pc_next: UInt = Wire(UInt(32.W))
  val pc_wen : Bool = Wire(Bool())
  val pc     : UInt = RegEnable(pc_next, pc_init.S(32.W).asUInt(), pc_wen)

  val hi_wen : Bool = Wire(Bool())
  val lo_wen : Bool = Wire(Bool())
  val hi_next: UInt = Wire(UInt(32.W))
  val lo_next: UInt = Wire(UInt(32.W))
  val hi     : UInt = RegEnable(hi_next, 0.U, hi_wen)
  val lo     : UInt = RegEnable(lo_next, 0.U, lo_wen)

  // 连线
  val if_id_bus : FetchDecodeBundle     = Wire(new FetchDecodeBundle)
  val id_ex_bus : DecodeExecuteBundle   = Wire(new DecodeExecuteBundle)
  val ex_ms_bus : ExecuteMemoryBundle   = Wire(new ExecuteMemoryBundle)
  val ms_wb_bus : MemoryWriteBackBundle = Wire(new MemoryWriteBackBundle)
  val id_pf_bus : DecodePreFetchBundle  = Wire(new DecodePreFetchBundle)
  val if_allowin: Bool                  = Wire(Bool())
  val id_allowin: Bool                  = Wire(Bool())
  val ex_allowin: Bool                  = Wire(Bool())
  val ms_allowin: Bool                  = Wire(Bool())
  val wb_allowin: Bool                  = Wire(Bool())
  val bypass_bus: DecodeBypassBundle    = Wire(new DecodeBypassBundle)
  val lw_ex_id  : Bool                  = Wire(Bool())
  bypass_bus.valid_lw_ex_id := lw_ex_id

  def addr_mapping(physical_addr: UInt): UInt = {
    val vaddr: UInt = Wire(UInt(32.W))
    vaddr := Mux((physical_addr >= 0x80000000L.U && physical_addr <= 0x9fffffffL.U) ||
      (physical_addr >= 0xa0000000L.U && physical_addr <= 0xbfffffffL.U),
      physical_addr & 0x1fffffff.U, physical_addr)
    vaddr
  }

  // 寄存器堆
  val regfile  : RegFile     = Module(new RegFile(reg_init))
  // 每个寄存器都以其需要被用于输入的阶段命名
  // 预取
  val pf_module: InsPreFetch = Module(new InsPreFetch)
  pf_module.io.in_valid := 1.U // 目前始终允许
  pf_module.io.id_pf_in := id_pf_bus
  pf_module.io.pc := pc
  pf_module.io.next_allowin := if_allowin
  io.inst_sram_addr := addr_mapping(pf_module.io.ins_ram_addr)
  io.inst_sram_en := pf_module.io.ins_ram_en
  io.inst_sram_wen := "b0000".U(4.W)
  io.inst_sram_wdata := DontCare
  pc_wen := pf_module.io.pc_wen
  pc_next := pf_module.io.next_pc


  val if_valid_reg: Bool = RegNext(next = pf_module.io.pf_if_valid, init = 0.B)

  // 取指
  val if_module: InsSufFetch = Module(new InsSufFetch)
  // 由于是伪阶段，不需要寄存器来存储延迟槽指令pc
  if_module.io.pf_if_valid := if_valid_reg
  if_module.io.delay_slot_pc_pf_if := pf_module.io.delay_slot_pc_pf_if
  if_module.io.ins_ram_data := io.inst_sram_rdata
  if_module.io.pc_debug_pf_if := pf_module.io.pc_debug_pf_if
  if_module.io.next_allowin := id_allowin
  if_allowin := if_module.io.this_allowin
  if_id_bus := if_module.io.if_id_out
  if_id_bus.bus_valid := if_module.io.if_id_out.bus_valid && if_valid_reg


  // 译码
  val id_reg: FetchDecodeBundle = RegEnableWithValid(next = if_id_bus, enable = id_allowin && if_id_bus.bus_valid, init = {
    val bundle: FetchDecodeBundle = Wire(new FetchDecodeBundle)
    bundle.defaults()
    bundle
  }, valid_enable = id_allowin)

  val id_module: InsDecode = Module(new InsDecode)
  id_module.io.if_id_in := id_reg
  id_module.io.regfile_read1 := regfile.io.rdata1
  id_module.io.regfile_read2 := regfile.io.rdata2
  id_module.io.bypass_bus := bypass_bus
  id_module.io.ex_out_valid := ex_ms_bus.bus_valid
  // 回馈给预取阶段的输出
  id_pf_bus := id_module.io.id_pf_out

  // 请求寄存器堆
  regfile.io.raddr1 := id_module.io.id_ex_out.inst_rs_id_ex
  regfile.io.raddr2 := id_module.io.id_ex_out.inst_rt_id_ex

  id_ex_bus := id_module.io.id_ex_out
  id_allowin := id_module.io.this_allowin


  // 执行
  val ex_reg: DecodeExecuteBundle = RegEnableWithValid(next = id_ex_bus, enable = ex_allowin && id_ex_bus.bus_valid, init = {
    val bundle: DecodeExecuteBundle = Wire(new DecodeExecuteBundle)
    bundle.defaults()
    bundle
  }, valid_enable = ex_allowin)

  val ex_module            : InsExecute        = Module(new InsExecute)
  val ex_divider_state_next: DividerState.Type = Wire(DividerState())
  val ex_divider_ready     : Bool              = ex_module.io.divider_tready
  val ex_divider_state_reg : DividerState.Type = RegEnable(next = ex_divider_state_next, init = DividerState.waiting,
    enable = ex_module.io.divider_required)
  ex_divider_state_next := MuxCase(DividerState.waiting, Seq(
    (ex_divider_state_reg === DividerState.waiting && ex_module.io.divider_required) -> DividerState.inputting,
    (ex_divider_state_reg === DividerState.inputting && ex_module.io.divider_tready) -> DividerState.processing,
    (ex_divider_state_reg === DividerState.processing && ex_module.io.this_allowin) -> DividerState.waiting
  ))
  ex_module.io.divider_tvalid := ex_divider_state_next === DividerState.inputting
  // 直接接入ram的通路
  ex_module.io.id_ex_in := ex_reg
  io.data_sram_en := ex_module.io.mem_en
  io.data_sram_wen := VecInit(Seq.fill(4)(ex_module.io.mem_wen)).asUInt()
  io.data_sram_addr := addr_mapping(ex_module.io.mem_addr)
  //  io.data_sram_wdata := ex_module.io.mem_wdata
  // sw => wdata := regfile2
  io.data_sram_wdata := ex_module.io.mem_wdata
  ex_ms_bus := ex_module.io.ex_ms_out
  ex_allowin := ex_module.io.this_allowin
  bypass_bus.bp_ex_id := ex_module.io.bypass_ex_id
  lw_ex_id := ex_module.io.valid_lw_ex_id
  hi_next := ex_module.io.hi_out
  lo_next := ex_module.io.lo_out
  hi_wen := ex_module.io.hi_wen
  lo_wen := ex_module.io.lo_wen


  // 访存
  val ms_reg: ExecuteMemoryBundle = RegEnableWithValid(next = ex_ms_bus, enable = ms_allowin && ex_ms_bus.bus_valid, init = {
    val bundle = Wire(new ExecuteMemoryBundle)
    bundle.defaults()
    bundle
  }, valid_enable = ms_allowin)

  val ms_module: InsMemory = Module(new InsMemory)
  ms_module.io.ex_ms_in := ms_reg
  ms_module.io.mem_rdata := io.data_sram_rdata
  ms_wb_bus := ms_module.io.ms_wb_out
  ms_allowin := ms_module.io.this_allowin
  bypass_bus.bp_ms_id := ms_module.io.bypass_ms_id

  // 写回
  val wb_reg   : MemoryWriteBackBundle = RegEnableWithValid(next = ms_wb_bus, enable = wb_allowin && ms_wb_bus.bus_valid, init = {
    val bundle = Wire(new MemoryWriteBackBundle)
    bundle.defaults()
    bundle
  }, valid_enable = wb_allowin)
  val wb_module: InsWriteBack          = Module(new InsWriteBack)
  wb_module.io.ms_wb_in := wb_reg
  regfile.io.wdata := wb_module.io.regfile_wdata
  regfile.io.waddr := wb_module.io.regfile_waddr
  regfile.io.we := wb_module.io.regfile_we
  wb_module.io.next_allowin := 1.B
  wb_allowin := wb_module.io.this_allowin
  bypass_bus.bp_wb_id := wb_module.io.bypass_wb_id

  io.debug_wb_pc := wb_module.io.pc_wb
  io.debug_wb_rf_wnum := wb_module.io.regfile_waddr
  io.debug_wb_rf_wen := VecInit(Seq.fill(4)(wb_module.io.regfile_we)).asUInt()
  io.debug_wb_rf_wdata := wb_module.io.regfile_wdata

  id_module.io.next_allowin := ex_allowin
  ex_module.io.next_allowin := ms_allowin
  ms_module.io.next_allowin := wb_allowin


  clock.suggestName("clk")
  reset.suggestName("reset")
}

object CpuTop extends App {
  val v_content = (new ChiselStage).emitVerilog(new CpuTop(0xbfbffffc))
}