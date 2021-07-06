package com.github.hectormips.pipeline

import chisel3.util._
import chisel3._
import chisel3.util.experimental.loadMemoryFromFile
import chisel3.stage.ChiselStage

class CpuTopBundle extends Bundle {
  val pc_debug      : UInt = Output(UInt(32.W))
  val regfile_rdata1: UInt = Output(UInt(32.W))
  val regfile_rdata2: UInt = Output(UInt(32.W))

  val regfile_wb_addr_debug: UInt = Output(UInt(5.W))
  val regfile_wb_data_debug: UInt = Output(UInt(32.W))
  val dataram_wdata        : UInt = Output(UInt(32.W))
  val dataram_waddr        : UInt = Output(UInt(32.W))
  val dataram_wen          : Bool = Output(Bool())
}

class CpuTop extends Module {
  val io: CpuTopBundle = IO(new CpuTopBundle())

  // 内建寄存器
  // pc重置时默认为0xfffffffc，这样+4得到的就是第一条指令地址
  val pc_next: UInt    = Wire(UInt(32.W))
  val pc_wen : Bool    = Wire(Bool())
  val pc     : UInt    = RegEnable(pc_next, 0xfffffffc.S(32.W).asUInt(), pc_wen)
  // 直接在cpu顶层内建ins ram和data ram
  val ins_ram: SyncRam = Module(new SyncRam(1024))
  loadMemoryFromFile(ins_ram.ram, "resource/inst1.hex.txt")


  val data_ram: SyncRam = Module(new SyncRam(1024))

  // 连线
  val if_id_bus : FetchDecodeBundle     = Wire(new FetchDecodeBundle)
  val id_ex_bus : DecodeExecuteBundle   = Wire(new DecodeExecuteBundle)
  val ex_ms_bus : ExecuteMemoryBundle   = Wire(new ExecuteMemoryBundle)
  val ms_wb_bus : MemoryWriteBackBundle = Wire(new MemoryWriteBackBundle)
  val id_pf_bus : DecodePreFetchBundle  = Wire(new DecodePreFetchBundle)
  val id_allowin: Bool                  = Wire(Bool())
  val ex_allowin: Bool                  = Wire(Bool())
  val ms_allowin: Bool                  = Wire(Bool())
  val wb_allowin: Bool                  = Wire(Bool())

  // 寄存器堆
  val regfile: RegFile = Module(new RegFile)
  io.regfile_rdata1 := regfile.io.rdata1
  io.regfile_rdata2 := regfile.io.rdata2
  // 每个寄存器都以其需要被用于输入的阶段命名
  // 预取
  val pf_module: InsPreFetch = Module(new InsPreFetch)
  pf_module.io.in_valid := 1.U // 目前始终允许
  pf_module.io.id_pf_in := id_pf_bus
  pf_module.io.regfile_read1 := regfile.io.rdata1
  pf_module.io.pc := pc
  pf_module.io.next_allowin := 1.B
  ins_ram.io.ram_addr := pf_module.io.ins_ram_addr
  ins_ram.io.ram_en := pf_module.io.ins_ram_en
  ins_ram.io.ram_wen := 0.U
  ins_ram.io.ram_wdata := DontCare
  pc_wen := pf_module.io.pc_wen
  pc_next := pf_module.io.next_pc


  // 取指
  val if_module: InsSufFetch = Module(new InsSufFetch)
  // 由于是伪阶段，不需要寄存器来存储延迟槽指令pc
  if_module.io.pf_if_valid := pf_module.io.pf_if_valid
  if_module.io.delay_slot_pc_pf_if := pf_module.io.delay_slot_pc_pf_if
  if_module.io.ins_ram_data := ins_ram.io.ram_rdata
  if_id_bus := if_module.io.if_id_out


  // 译码
  val id_reg   : FetchDecodeBundle = RegEnable(next = if_id_bus, enable = id_allowin)
  val id_module: InsDecode         = Module(new InsDecode)

  id_module.io.regfile_read1 := regfile.io.rdata1
  id_module.io.regfile_read2 := regfile.io.rdata2
  id_module.io.if_id_in := id_reg
  // 回馈给预取阶段的输出
  id_pf_bus := id_module.io.id_pf_out

  // 请求寄存器堆
  regfile.io.raddr1 := id_module.io.id_ex_out.inst_rs_id_ex
  regfile.io.raddr2 := id_module.io.id_ex_out.inst_rt_id_ex

  id_module.io.if_id_in.ins_if_id := if_id_bus.ins_if_id
  id_module.io.if_id_in.pc_if_id := if_id_bus.pc_if_id
  id_ex_bus := id_module.io.id_ex_out
  id_allowin := id_module.io.this_allowin


  // 执行
  val ex_reg: DecodeExecuteBundle = RegEnable(next = id_ex_bus, enable = ms_allowin)

  val ex_module: InsExecute = Module(new InsExecute)
  // 直接接入ram的通路
  ex_module.io.id_ex_in := ex_reg
  data_ram.io.ram_en := ex_module.io.mem_en
  data_ram.io.ram_wen := ex_module.io.mem_wen
  data_ram.io.ram_addr := ex_module.io.mem_addr
  data_ram.io.ram_wdata := ex_module.io.mem_wdata
  ex_ms_bus := ex_module.io.ex_ms_out
  ex_allowin := ex_module.io.this_allowin


  // 访存
  val ms_reg: ExecuteMemoryBundle = RegEnable(next = ex_ms_bus, enable = ms_allowin)

  val ms_module: InsMemory = Module(new InsMemory)
  ms_module.io.ex_ms_in := ms_reg
  ms_module.io.mem_rdata := data_ram.io.ram_rdata
  ms_wb_bus := ms_module.io.ms_wb_out
  ms_allowin := ms_module.io.this_allowin

  // 写回
  val wb_reg   : MemoryWriteBackBundle = RegEnable(next = ms_wb_bus, enable = wb_allowin)
  val wb_module: InsWriteBack          = Module(new InsWriteBack)
  wb_module.io.ms_wb_in := wb_reg
  regfile.io.wdata := wb_module.io.regfile_wdata
  regfile.io.waddr := wb_module.io.regfile_waddr
  regfile.io.we := wb_module.io.regfile_we
  wb_module.io.next_allowin := 1.B
  wb_allowin := wb_module.io.this_allowin

  io.pc_debug := pc
  io.regfile_wb_addr_debug := wb_module.io.regfile_waddr
  io.regfile_wb_data_debug := wb_module.io.regfile_wdata

  id_module.io.next_allowin := ex_allowin
  ex_module.io.next_allowin := ms_allowin
  ms_module.io.next_allowin := wb_allowin

  io.dataram_wdata := ex_module.io.mem_wdata
  io.dataram_wen := ex_module.io.mem_wen
  io.dataram_waddr := ex_module.io.mem_addr
}

object CpuTop extends App {
  (new ChiselStage).emitVerilog(new CpuTop)
}