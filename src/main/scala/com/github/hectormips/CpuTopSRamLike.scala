package com.github.hectormips

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._
import chisel3.util.experimental.forceName
import com.github.hectormips.pipeline._
import com.github.hectormips.utils.RegEnableWithValid

class CpuTopSRamLikeBundle extends Bundle {
  val interrupt        : UInt           = Input(UInt(6.W))
  val inst_sram_like_io: SRamLikeInstIO = new SRamLikeInstIO
  val data_sram_like_io: SRamLikeDataIO = new SRamLikeDataIO

  val debug: DebugBundle = new DebugBundle
  forceName(interrupt, "ext_int")

}


class CpuTopSRamLike(pc_init: Long, reg_init: Int = 0) extends MultiIOModule {
  // 命名
  val io: CpuTopSRamLikeBundle = IO(new CpuTopSRamLikeBundle())


  // 内建寄存器
  // pc重置时默认为0xfffffffc，这样+4得到的就是第一条指令地址
  val pc_next: UInt = Wire(UInt(32.W))
  val pc_wen : Bool = Wire(Bool())
  val pc     : UInt = RegEnable(pc_next, pc_init.U(32.W), pc_wen)

  val hi_wen : Bool = Wire(Bool())
  val lo_wen : Bool = Wire(Bool())
  val hi_next: UInt = Wire(UInt(32.W))
  val lo_next: UInt = Wire(UInt(32.W))
  val hi     : UInt = RegEnable(hi_next, 0.U, hi_wen)
  val lo     : UInt = RegEnable(lo_next, 0.U, lo_wen)

  // 连线
  val if_id_bus                    : FetchDecodeBundle     = Wire(new FetchDecodeBundle)
  val id_ex_bus                    : DecodeExecuteBundle   = Wire(new DecodeExecuteBundle)
  val ex_ms_bus                    : ExecuteMemoryBundle   = Wire(new ExecuteMemoryBundle)
  val ms_wb_bus                    : MemoryWriteBackBundle = Wire(new MemoryWriteBackBundle)
  val id_pf_bus                    : DecodePreFetchBundle  = Wire(new DecodePreFetchBundle)
  val if_allowin                   : Bool                  = Wire(Bool())
  val id_allowin                   : Bool                  = Wire(Bool())
  val ex_allowin                   : Bool                  = Wire(Bool())
  val ms_allowin                   : Bool                  = Wire(Bool())
  val wb_allowin                   : Bool                  = Wire(Bool())
  val bypass_bus                   : DecodeBypassBundle    = Wire(new DecodeBypassBundle)
  val cp0_ex                       : CP0ExecuteBundle      = Wire(new CP0ExecuteBundle)
  val ex_cp0                       : ExecuteCP0Bundle      = Wire(new ExecuteCP0Bundle)
  val pipeline_flush_ex            : Bool                  = Wire(Bool()) // 由执行阶段发出的流水线清空信号
  val to_exception_service_en_ex_pf: Bool                  = Wire(Bool())
  val to_epc_en_ex_pf              : Bool                  = Wire(Bool())
  val epc_cp0_pf                   : UInt                  = Wire(UInt(32.W))
  val cp0_hazard_bypass_ms_ex      : CP0HazardBypass       = Wire(new CP0HazardBypass)
  val cp0_hazard_bypass_wb_ex      : CP0HazardBypass       = Wire(new CP0HazardBypass)
  val cp0_status_im                : UInt                  = Wire(UInt(8.W))
  val cp0_cause_ip                 : UInt                  = Wire(UInt(8.W))

  def addr_mapping(physical_addr: UInt): UInt = {
    val vaddr: UInt = Wire(UInt(32.W))
    vaddr := Mux((physical_addr >= 0x80000000L.U && physical_addr <= 0x9fffffffL.U) ||
      (physical_addr >= 0xa0000000L.U && physical_addr <= 0xbfffffffL.U),
      physical_addr & 0x1fffffff.U, physical_addr)
    vaddr
  }

  // 寄存器堆
  val regfile: RegFile = Module(new RegFile(reg_init))
  // 每个寄存器都以其需要被用于输入的阶段命名


  val fetch_state_reg : RamState.Type    = RegInit(RamState.waiting_for_request)
  val branch_state_reg: BranchState.Type = RegInit(BranchState.no_branch)


  // 预取
  val pf_module   : InsPreFetch          = Module(new InsPreFetch)
  val id_pf_buffer: DecodePreFetchBundle = RegInit(new DecodePreFetchBundle, init = {
    val id_pf_bundle: DecodePreFetchBundle = Wire(new DecodePreFetchBundle)
    id_pf_bundle.defaults()
    id_pf_bundle
  })
  pf_module.io.in_valid := 1.U // 目前始终允许
  when(id_pf_bus.bus_valid && id_pf_bus.jump_taken && !pipeline_flush_ex) {
    id_pf_buffer := id_pf_bus
  }
  when(pipeline_flush_ex) {
    id_pf_buffer.bus_valid := 0.B
  }
  pf_module.io.id_pf_in := id_pf_buffer
  pf_module.io.pc := pc
  pf_module.io.next_allowin := if_allowin
  pf_module.io.to_exception_service_en_ex_pf := to_exception_service_en_ex_pf
  pf_module.io.to_epc_en_ex_pf := to_epc_en_ex_pf
  pf_module.io.cp0_pf_epc := epc_cp0_pf
  pf_module.io.flush := pipeline_flush_ex
  pf_module.io.ins_ram_data_ok := io.inst_sram_like_io.data_ok
  pf_module.io.fetch_state := fetch_state_reg
  pf_module.io.branch_state := branch_state_reg
  io.inst_sram_like_io.addr := addr_mapping(pf_module.io.ins_ram_addr)
  io.inst_sram_like_io.req := pf_module.io.ins_ram_en
  io.inst_sram_like_io.wr := 0.B
  io.inst_sram_like_io.wdata := DontCare
  io.inst_sram_like_io.size := 2.U
  pc_wen := pf_module.io.pc_wen
  pc_next := pf_module.io.next_pc
  when(!pipeline_flush_ex && !to_epc_en_ex_pf) {
    when((id_pf_buffer.bus_valid && id_pf_buffer.is_jump) ||
      (id_pf_bus.bus_valid && id_pf_bus.is_jump)) {
      when(branch_state_reg === BranchState.no_branch && ((id_pf_buffer.bus_valid && id_pf_buffer.jump_taken) ||
        (id_pf_bus.bus_valid && id_pf_bus.jump_taken))) {
        branch_state_reg := BranchState.delay_slot
      }.elsewhen(branch_state_reg === BranchState.no_branch && ((id_pf_buffer.bus_valid && !id_pf_buffer.jump_taken) ||
        (id_pf_bus.bus_valid && !id_pf_bus.jump_taken))) {
        branch_state_reg := BranchState.delay_slot_no_jump
      }.elsewhen(branch_state_reg === BranchState.delay_slot &&
        fetch_state_reg === RamState.waiting_for_read && id_allowin) {
        branch_state_reg := BranchState.branch_target
      }.elsewhen(branch_state_reg === BranchState.branch_target &&
        fetch_state_reg === RamState.waiting_for_read && id_allowin) {
        branch_state_reg := BranchState.no_branch
        id_pf_buffer.bus_valid := 0.B
      }
    }
    when(branch_state_reg === BranchState.delay_slot_no_jump && fetch_state_reg === RamState.waiting_for_read && id_allowin) {
      branch_state_reg := BranchState.no_branch
      id_pf_buffer.bus_valid := 0.B
    }
  }.otherwise {
    branch_state_reg := BranchState.no_branch
  }


  when(pf_module.io.ins_ram_en && fetch_state_reg === RamState.waiting_for_request) {
    when(!io.inst_sram_like_io.addr_ok) {
      fetch_state_reg := RamState.requesting
    }.elsewhen(io.inst_sram_like_io.addr_ok) {
      fetch_state_reg := RamState.waiting_for_response
    }
  }
  when(io.inst_sram_like_io.addr_ok && fetch_state_reg === RamState.requesting && pf_module.io.ins_ram_en) {
    fetch_state_reg := RamState.waiting_for_response
  }
  when(io.inst_sram_like_io.data_ok && fetch_state_reg === RamState.waiting_for_response) {
    fetch_state_reg := RamState.waiting_for_read
  }
  when(id_allowin && fetch_state_reg === RamState.waiting_for_read) {
    fetch_state_reg := RamState.waiting_for_request
  }
  when(pipeline_flush_ex || to_epc_en_ex_pf) {
    when(fetch_state_reg === RamState.waiting_for_response) {
      fetch_state_reg := RamState.cancel
    }.otherwise {
      fetch_state_reg := RamState.waiting_for_request
    }
  }
  when(fetch_state_reg === RamState.cancel && io.inst_sram_like_io.data_ok) {
    fetch_state_reg := RamState.waiting_for_request
  }


  // 取指
  val if_module: InsSufFetch = Module(new InsSufFetch)
  // 由于是伪阶段，不需要寄存器来存储延迟槽指令pc
  if_module.io.delay_slot_pc_pf_if := pf_module.io.delay_slot_pc_pf_if
  if_module.io.ins_ram_data := io.inst_sram_like_io.rdata
  if_module.io.pc_debug_pf_if := pf_module.io.pc_debug_pf_if
  if_module.io.next_allowin := id_allowin
  if_module.io.flush := pipeline_flush_ex
  if_module.io.ins_ram_data_ok := io.inst_sram_like_io.data_ok
  if_module.io.fetch_state := fetch_state_reg
  if_module.io.is_delay_slot := branch_state_reg === BranchState.delay_slot ||
    branch_state_reg === BranchState.delay_slot_no_jump
  if_allowin := if_module.io.this_allowin
  if_id_bus := if_module.io.if_id_out
  if_id_bus.bus_valid := if_module.io.if_id_out.bus_valid


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
  id_module.io.flush := pipeline_flush_ex
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
  val ex_divider_state_reg : DividerState.Type = RegEnable(next = ex_divider_state_next, init = DividerState.waiting,
    enable = ex_module.io.divider_required)
  ex_divider_state_next := MuxCase(ex_divider_state_reg, Seq(
    (ex_divider_state_reg === DividerState.waiting && ex_module.io.divider_required) -> DividerState.inputting,
    (ex_divider_state_reg === DividerState.inputting && ex_module.io.divider_tready) -> DividerState.handshaking,
    (ex_divider_state_reg === DividerState.handshaking && !ex_module.io.divider_tready) -> DividerState.calculating,
    (ex_divider_state_reg === DividerState.calculating && ex_module.io.this_allowin) -> DividerState.waiting,
    ex_allowin -> DividerState.waiting
  ))
  ex_module.io.divider_tvalid := ex_divider_state_next === DividerState.inputting ||
    ex_divider_state_next === DividerState.handshaking
  val data_sram_state_reg: RamState.Type = RegInit(init = RamState.waiting_for_request)

  // 直接接入ram的通路
  ex_module.io.id_ex_in := ex_reg
  ex_module.io.hi_in := hi
  ex_module.io.lo_in := lo
  ex_module.io.cp0_hazard_bypass_ms_ex := cp0_hazard_bypass_ms_ex
  ex_module.io.cp0_hazard_bypass_wb_ex := cp0_hazard_bypass_wb_ex
  ex_module.io.cp0_cause_ip := cp0_cause_ip
  ex_module.io.cp0_status_im := cp0_status_im
  ex_module.io.data_ram_state := data_sram_state_reg
  ex_module.io.data_ram_addr_ok := io.data_sram_like_io.addr_ok
  io.data_sram_like_io.req := ex_module.io.mem_en
  io.data_sram_like_io.wr := ex_module.io.mem_wen =/= 0.U
  io.data_sram_like_io.addr := addr_mapping(ex_module.io.mem_addr)
  io.data_sram_like_io.size := ex_module.io.mem_size
  when(!pipeline_flush_ex) {
    when(data_sram_state_reg === RamState.waiting_for_request && ex_module.io.mem_en &&
      ex_module.io.id_ex_in.bus_valid && !io.data_sram_like_io.addr_ok) {
      data_sram_state_reg := RamState.requesting
    }.elsewhen((data_sram_state_reg === RamState.requesting ||
      (data_sram_state_reg === RamState.waiting_for_request && ex_module.io.mem_en && ex_module.io.id_ex_in.bus_valid)) &&
      io.data_sram_like_io.addr_ok) {
      data_sram_state_reg := RamState.waiting_for_response
    }.elsewhen(data_sram_state_reg === RamState.waiting_for_response && io.data_sram_like_io.data_ok) {
      data_sram_state_reg := RamState.waiting_for_request
    }
  }.otherwise {
    when(data_sram_state_reg === RamState.waiting_for_response) {
      data_sram_state_reg := RamState.cancel
    }.otherwise {
      data_sram_state_reg := RamState.waiting_for_request
    }
  }
  when(data_sram_state_reg === RamState.cancel && io.data_sram_like_io.data_ok) {
    data_sram_state_reg := RamState.waiting_for_request
  }

  //  io.data_sram_wdata := ex_module.io.mem_wdata
  // sw => wdata := regfile2
  io.data_sram_like_io.wdata := ex_module.io.mem_wdata
  ex_ms_bus := ex_module.io.ex_ms_out
  ex_allowin := ex_module.io.this_allowin
  bypass_bus.bp_ex_id := ex_module.io.bypass_ex_id
  hi_next := ex_module.io.hi_out
  lo_next := ex_module.io.lo_out
  hi_wen := ex_module.io.hi_wen
  lo_wen := ex_module.io.lo_wen
  ex_module.io.cp0_ex_in := cp0_ex
  ex_cp0 := ex_module.io.ex_cp0_out
  pipeline_flush_ex := ex_module.io.pipeline_flush
  to_exception_service_en_ex_pf := ex_module.io.to_exception_service_en_ex_pf
  to_epc_en_ex_pf := ex_module.io.to_epc_en_ex_pf


  // 访存
  val ms_reg: ExecuteMemoryBundle = RegEnableWithValid(next = ex_ms_bus, enable = ms_allowin && ex_ms_bus.bus_valid, init = {
    val bundle = Wire(new ExecuteMemoryBundle)
    bundle.defaults()
    bundle
  }, valid_enable = ms_allowin)

  val ms_module: InsMemory = Module(new InsMemory)
  ms_module.io.ex_ms_in := ms_reg
  ms_module.io.mem_rdata := io.data_sram_like_io.rdata
  ms_module.io.data_ram_data_ok := io.data_sram_like_io.data_ok
  ms_module.io.data_ram_state := data_sram_state_reg
  ms_wb_bus := ms_module.io.ms_wb_out
  ms_allowin := ms_module.io.this_allowin
  cp0_hazard_bypass_ms_ex := ms_module.io.cp0_hazard_bypass_ms_ex
  bypass_bus.bp_ms_id := ms_module.io.bypass_ms_id

  // 写回
  val wb_reg: MemoryWriteBackBundle = RegEnableWithValid(next = ms_wb_bus, enable = wb_allowin && ms_wb_bus.bus_valid, init = {
    val bundle = Wire(new MemoryWriteBackBundle)
    bundle.defaults()
    bundle
  }, valid_enable = wb_allowin)

  val cp0: CP0 = Module(new CP0)
  cp0.io.ex_cp0_in := ex_cp0
  cp0_ex := cp0.io.cp0_ex_out
  epc_cp0_pf := cp0.io.epc
  cp0.io.int_in := io.interrupt
  cp0_cause_ip := cp0.io.cause_ip
  cp0_status_im := cp0.io.status_im

  val wb_module: InsWriteBack = Module(new InsWriteBack)
  wb_module.io.ms_wb_in := wb_reg
  regfile.io.wdata := wb_module.io.regfile_wdata
  regfile.io.waddr := wb_module.io.regfile_waddr
  regfile.io.we := wb_module.io.regfile_wen
  wb_module.io.next_allowin := 1.B
  wb_allowin := wb_module.io.this_allowin
  bypass_bus.bp_wb_id := wb_module.io.bypass_wb_id
  cp0.io.wen := wb_module.io.cp0_wen
  cp0.io.wdata := wb_module.io.cp0_wdata
  cp0.io.regsel := wb_module.io.cp0_sel
  cp0.io.regaddr := wb_module.io.cp0_addr
  wb_module.io.cp0_rdata := cp0.io.rdata
  cp0_hazard_bypass_wb_ex := wb_module.io.cp0_hazard_bypass_wb_ex

  io.debug.debug_wb_pc := wb_module.io.pc_wb
  io.debug.debug_wb_rf_wnum := wb_module.io.regfile_waddr
  io.debug.debug_wb_rf_wen := VecInit(Seq.fill(4)(wb_module.io.regfile_wen)).asUInt()
  io.debug.debug_wb_rf_wdata := wb_module.io.regfile_wdata

  id_module.io.next_allowin := ex_allowin
  ex_module.io.next_allowin := ms_allowin
  ms_module.io.next_allowin := wb_allowin


}

object CpuTopSRamLike extends App {
  (new ChiselStage).emitVerilog(new CpuTopSRamLike(0xbfbffffc))
}