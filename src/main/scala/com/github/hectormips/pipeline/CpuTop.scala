package com.github.hectormips.pipeline

import chisel3.util._
import chisel3._
import chisel3.stage.ChiselStage

class CpuTopBundle extends Bundle {
  val pc_debug      : UInt                  = Output(UInt(32.W))
  val regfile_rdata1: UInt                  = Output(UInt(32.W))
  val regfile_rdata2: UInt                  = Output(UInt(32.W))
  val if_id_debug   : FetchDecodeBundle     = Output(new FetchDecodeBundle)
  val id_ex_debug   : DecodeExecuteBundle   = Output(new DecodeExecuteBundle)
  val ex_ms_debug   : ExecuteMemoryBundle   = Output(new ExecuteMemoryBundle)
  val ms_wb_debug   : MemoryWriteBackBundle = Output(new MemoryWriteBackBundle)
  val id_pf_debug   : DecodePreFetchBundle  = Output(new DecodePreFetchBundle)
}

class CpuTop extends Module {
  val io: CpuTopBundle = IO(new CpuTopBundle())

  withClock(clock)(
    printf("regdata1 ===> %d\n", io.regfile_rdata1)
  )
  // 内建寄存器
  // pc重置时默认为0xfffffffc，这样+4得到的就是第一条指令地址
  val pc_next: UInt = Wire(UInt(32.W))
  val pc_wen : Bool = Wire(Bool())
  val pc     : UInt = RegEnable(pc_next, 0xfffffffc.S.asUInt(), pc_wen)

  // 直接在cpu顶层内建ins ram和data ram
  val ins_ram : SyncRam = Module(new SyncRam(1024))
  val data_ram: SyncRam = Module(new SyncRam(1024))

  // 连线
  val if_id_bus: FetchDecodeBundle     = Wire(new FetchDecodeBundle)
  val id_ex_bus: DecodeExecuteBundle   = Wire(new DecodeExecuteBundle)
  val ex_ms_bus: ExecuteMemoryBundle   = Wire(new ExecuteMemoryBundle)
  val ms_wb_bus: MemoryWriteBackBundle = Wire(new MemoryWriteBackBundle)
  val id_pf_bus: DecodePreFetchBundle  = Wire(new DecodePreFetchBundle)

  io.if_id_debug := if_id_bus
  io.id_ex_debug := id_ex_bus
  io.ex_ms_debug := ex_ms_bus
  io.ms_wb_debug := ms_wb_bus
  io.id_pf_debug := id_pf_bus


  // 寄存器堆
  val regfile: RegFile = Module(new RegFile)
  io.regfile_rdata1 := regfile.io.rdata1
  io.regfile_rdata2 := regfile.io.rdata2
  // 每个寄存器都以其需要被用于输入的阶段命名
  // 预取
  val pf_module: InsPreFetch = Module(new InsPreFetch)
  pf_module.io.id_pf_in := id_pf_bus
  pf_module.io.regfile_read1 := regfile.io.rdata1
  pf_module.io.pc := pc
  ins_ram.io.ram_addr := pf_module.io.ins_ram_addr
  ins_ram.io.ram_en := pf_module.io.ins_ram_en
  ins_ram.io.ram_wen := 0.U
  ins_ram.io.ram_wdata := DontCare
  pc_wen := pf_module.io.pc_wen
  pc_next := pf_module.io.next_pc


  // 取指
  val if_module: InsFetch = Module(new InsFetch)
  // 由于是伪阶段，不需要寄存器来存储延迟槽指令pc
  if_module.io.delay_slot_pc_pf_if := pf_module.io.delay_slot_pc_pf_if
  if_module.io.ins_ram_data := ins_ram.io.ram_rdata
  if_id_bus := if_module.io.if_id_out

  // 译码
  val id_reg   : FetchDecodeBundle = RegNext(if_id_bus)
  val id_module: InsDecode         = Module(new InsDecode)
  id_module.io.if_id_in := id_reg
  // 回馈给预取阶段的输出
  id_pf_bus := id_module.io.id_pf_out


  id_module.io.if_id_in.ins_if_id := if_id_bus.ins_if_id
  id_module.io.if_id_in.pc_if_id := if_id_bus.pc_if_id
  id_ex_bus := id_module.io.id_ex_out

  // 执行
  val ex_reg: DecodeExecuteBundle = RegNext(id_ex_bus)
  // 回馈给寄存器堆
  regfile.io.raddr1 := ex_reg.inst_rs_id_ex
  regfile.io.raddr2 := ex_reg.inst_rt_id_ex

  val ex_module: InsExecute = Module(new InsExecute)
  // 直接接入ram的通路
  ex_module.io.id_ex_in := ex_reg
  ex_module.io.regfile_read1 := regfile.io.rdata1
  ex_module.io.regfile_read2 := regfile.io.rdata2
  data_ram.io.ram_en := ex_module.io.mem_en
  data_ram.io.ram_wen := ex_module.io.mem_wen
  data_ram.io.ram_addr := ex_module.io.mem_addr
  data_ram.io.ram_wdata := ex_module.io.mem_wdata
  ex_ms_bus := ex_module.io.ex_ms_out


  // 访存
  val ms_reg: ExecuteMemoryBundle = RegNext(ex_ms_bus)

  val ms_module: InsMemory = Module(new InsMemory)
  ms_module.io.ex_ms_in := ms_reg
  ms_module.io.mem_rdata := data_ram.io.ram_rdata
  ms_wb_bus := ms_module.io.ms_wb_out

  // 写回
  val wb_reg   : MemoryWriteBackBundle = RegNext(ms_wb_bus)
  val wb_module: InsWriteBack          = Module(new InsWriteBack)
  wb_module.io.ms_wb_in := wb_reg
  regfile.io.wdata := wb_module.io.regfile_wdata
  regfile.io.waddr := wb_module.io.regfile_waddr
  regfile.io.we := wb_module.io.regfile_we
  io.pc_debug := pc
}

object CpuTop extends App {
  (new ChiselStage).emitVerilog(new CpuTop)
}