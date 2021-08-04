package com.github.hectormips

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._
import chisel3.util.experimental.forceName
import com.github.hectormips.pipeline._
import com.github.hectormips.pipeline.cp0.{CP0, ExecuteCP0Bundle}
import com.github.hectormips.pipeline.issue.InstFIFO
import com.github.hectormips.predict.{BHT, BTB, BTBTrue}
import com.github.hectormips.utils.{RegAutoFlip, RegDualAutoFlip}

class CpuTopSRamLikeBundle extends Bundle {
  val interrupt        : UInt                = Input(UInt(6.W))
  val inst_sram_like_io: SRamLikeInstIO      = new SRamLikeInstIO
  val data_sram_like_io: Vec[SRamLikeDataIO] = Vec(2, new SRamLikeDataIO)

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
  val if_fifo_bus                  : FetchDecodeBundle           = Wire(new FetchDecodeBundle)
  val fifo_id_bus                  : FetchDecodeBundle           = Wire(new FetchDecodeBundle)
  val id_ex_bus                    : Vec[DecodeExecuteBundle]    = Wire(Vec(2, new DecodeExecuteBundle))
  val ex_ms_bus                    : Vec[ExecuteMemoryBundle]    = Wire(Vec(2, new ExecuteMemoryBundle))
  val ms_wb_bus                    : Vec[MemoryWriteBackBundle]  = Wire(Vec(2, new MemoryWriteBackBundle))
  val id_pf_bus                    : DecodePreFetchBundle        = Wire(new DecodePreFetchBundle)
  val if_allowin                   : Bool                        = Wire(Bool())
  val id_allowin                   : Bool                        = Wire(Bool())
  val ex_allowin                   : Bool                        = Wire(Bool())
  val ms_allowin                   : Bool                        = Wire(Bool())
  val wb_allowin                   : Bool                        = Wire(Bool())
  val bypass_bus                   : DecodeBypassBundle          = Wire(new DecodeBypassBundle)
  val cp0_ex                       : CP0ExecuteBundle            = Wire(new CP0ExecuteBundle)
  val ex_cp0                       : ExecuteCP0Bundle            = Wire(new ExecuteCP0Bundle)
  val pipeline_flush_ex            : Bool                        = Wire(Bool()) // 由执行阶段发出的流水线清空信号
  val to_exception_service_en_ex_pf: Bool                        = Wire(Bool())
  val to_epc_en_ex_pf              : Bool                        = Wire(Bool())
  val epc_cp0_pf                   : UInt                        = Wire(UInt(32.W))
  val cp0_hazard_bypass_ms_ex      : Vec[CP0HazardBypass]        = Wire(Vec(2, new CP0HazardBypass))
  val cp0_hazard_bypass_wb_ex      : Vec[CP0HazardBypass]        = Wire(Vec(2, new CP0HazardBypass))
  val cp0_status_im                : UInt                        = Wire(UInt(8.W))
  val cp0_cause_ip                 : UInt                        = Wire(UInt(8.W))
  val decoder_predictor            : DecoderPredictorBundle      = Wire(new DecoderPredictorBundle)
  val predictor_fetcher            : Vec[PredictorFetcherBundle] = Wire(Vec(2, new PredictorFetcherBundle))
  val fetcher_predictor            : Vec[FetcherPredictorBundle] = Wire(Vec(2, new FetcherPredictorBundle))
  // 将ex阶段的flush回馈延迟一个周期
  val feedback_flipper             : Bool                        = RegInit(0.B)
  val branch_state                 : BranchState.Type            = RegInit(BranchState.no_branch)
  val fetch_force_cancel           : Bool                        = feedback_flipper || branch_state === BranchState.flushing

  val predictor: BTB = Module(new BTB(16, 4))
  when(feedback_flipper === 1.B) {
    feedback_flipper := 0.B
  }.elsewhen(to_epc_en_ex_pf || to_exception_service_en_ex_pf || pipeline_flush_ex) {
    feedback_flipper := 1.B
  }
  predictor.io.en_ex := decoder_predictor.en_ex
  predictor.io.ex_pc := decoder_predictor.ex_pc
  predictor.io.ex_success := decoder_predictor.ex_success
  predictor.io.ex_target := decoder_predictor.ex_target

  for (i <- 0 to 1) {
    predictor_fetcher(i).predict := predictor.io.predicts(i).predict
    predictor_fetcher(i).target := predictor.io.predicts(i).target

    predictor.io.predicts(i).pc := fetcher_predictor(i).pc
  }


  // 寄存器堆
  val regfile: RegFile = Module(new RegFile(reg_init))


  val fetch_state_reg  : RamState.Type = RegInit(RamState.waiting_for_request)
  // 默认情况下只有第一条指令有效
  val inst_valid_buffer: UInt          = RegInit(1.U(2.W))
  when(fetch_force_cancel || branch_state === BranchState.flushing) {
    inst_valid_buffer := 1.U
  }.otherwise {
    when(fetch_state_reg === RamState.waiting_for_response && io.inst_sram_like_io.data_ok) {
      inst_valid_buffer := io.inst_sram_like_io.inst_valid
    }
  }
  when(id_pf_bus.bus_valid) {
    branch_state := BranchState.flushing
  }.elsewhen(branch_state === BranchState.flushing) {
    branch_state := BranchState.branching
  }
  io.debug.debug_flush := branch_state === BranchState.flushing

  // 预取
  val pf_module   : InsPreFetch           = Module(new InsPreFetch)
  val id_pf_buffer: DecodePreFetchBundle  = RegInit(init = {
    val id_pf_bundle: DecodePreFetchBundle = Wire(new DecodePreFetchBundle)
    id_pf_bundle.defaults()
    id_pf_bundle
  })
  val ex_pf_buffer: ExecutePrefetchBundle = RegInit(init = {
    val ex_pf_bundle: ExecutePrefetchBundle = Wire(new ExecutePrefetchBundle)
    ex_pf_bundle.defaults()
    ex_pf_bundle
  })
  pf_module.io.in_valid := 1.U // 目前始终允许
  when(pipeline_flush_ex) {
    id_pf_buffer.bus_valid := 0.B
  }.elsewhen(id_pf_bus.bus_valid) {
    id_pf_buffer := id_pf_bus
    branch_state := BranchState.flushing
  }.elsewhen(pf_module.io.ins_ram_en && io.inst_sram_like_io.addr_ok && !fetch_force_cancel) {
    id_pf_buffer.bus_valid := 0.B
    branch_state := BranchState.no_branch
  }
  when(to_epc_en_ex_pf || to_exception_service_en_ex_pf) {
    ex_pf_buffer.to_epc_en_ex_pf := to_epc_en_ex_pf
    ex_pf_buffer.to_exception_service_en_ex_pf := to_exception_service_en_ex_pf
  }.elsewhen(pf_module.io.ins_ram_en && io.inst_sram_like_io.addr_ok) {
    ex_pf_buffer.defaults()
  }
  pf_module.io.id_pf_in := id_pf_buffer
  pf_module.io.inst_sram_ins_valid := Mux(io.inst_sram_like_io.data_ok && !fetch_force_cancel,
    io.inst_sram_like_io.inst_valid, inst_valid_buffer)
  pf_module.io.pc := pc
  pf_module.io.next_allowin := if_allowin
  pf_module.io.to_exception_service_en_ex_pf := ex_pf_buffer.to_exception_service_en_ex_pf
  pf_module.io.to_epc_en_ex_pf := ex_pf_buffer.to_epc_en_ex_pf
  pf_module.io.cp0_pf_epc := epc_cp0_pf
  pf_module.io.flush := fetch_force_cancel
  pf_module.io.fetch_state := fetch_state_reg
  pf_module.io.data_ok := io.inst_sram_like_io.data_ok
  pf_module.io.addr_ok := io.inst_sram_like_io.addr_ok
  fetcher_predictor := pf_module.io.pf_pred_out
  pf_module.io.pred_pf_in := predictor_fetcher
  for (i <- 0 to 1) {
    io.inst_sram_like_io.inst_predict_jump_out(i) := predictor.io.predicts(i).predict
    io.inst_sram_like_io.inst_predict_jump_target_out(i) := predictor.io.predicts(i).target
  }
  io.inst_sram_like_io.addr := pf_module.io.ins_ram_addr
  io.inst_sram_like_io.req := pf_module.io.ins_ram_en
  io.inst_sram_like_io.wr := 0.B
  io.inst_sram_like_io.wdata := DontCare
  io.inst_sram_like_io.size := 2.U
  pc_wen := pf_module.io.pc_wen
  pc_next := pf_module.io.next_pc


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
    // 如果等待返回的过程中正好可以data_ok并且可以发送新的请求，则继续等待新的请求结果
    when(pf_module.io.ins_ram_en) {
      when(io.inst_sram_like_io.addr_ok) {
        fetch_state_reg := RamState.waiting_for_response
      }.otherwise {
        fetch_state_reg := RamState.requesting
      }
    }.otherwise {
      fetch_state_reg := RamState.waiting_for_request
    }
  }
  // 如果发生了跳转，也取消当前的取指行为
  when(fetch_force_cancel && fetch_state_reg =/= RamState.cancel) {
    when(fetch_state_reg === RamState.waiting_for_response && !io.inst_sram_like_io.data_ok) {
      fetch_state_reg := RamState.cancel
    }.otherwise {
      fetch_state_reg := RamState.waiting_for_request
    }
  }
  when(fetch_state_reg === RamState.cancel && io.inst_sram_like_io.data_ok) {
    when(io.inst_sram_like_io.addr_ok && pf_module.io.ins_ram_en) {
      fetch_state_reg := RamState.waiting_for_response
    }.otherwise {
      fetch_state_reg := RamState.waiting_for_request
    }
  }


  // 取指
  val if_module: InsSufFetch = Module(new InsSufFetch)
  // 由于是伪阶段，不需要寄存器来存储延迟槽指令pc
  if_module.io.ins_ram_data := io.inst_sram_like_io.rdata
  if_module.io.pc_debug_pf_if := io.inst_sram_like_io.inst_pc
  if_module.io.next_allowin := id_allowin
  if_module.io.flush := fetch_force_cancel
  if_module.io.ins_ram_data_ok := io.inst_sram_like_io.data_ok
  if_module.io.ins_ram_data_valid := io.inst_sram_like_io.inst_valid
  if_module.io.ins_ram_predict_jump_taken := io.inst_sram_like_io.inst_predict_jump_in
  if_module.io.ins_ram_predict_jump_target := io.inst_sram_like_io.inst_predict_jump_target_in
  if_module.io.fetch_state := fetch_state_reg
  if_allowin := if_module.io.this_allowin
  if_fifo_bus := if_module.io.if_id_out


  val inst_fifo: InstFIFO = Module(new InstFIFO(64))
  // if-fifo
  inst_fifo.io.in.bits.inst_bundle.inst := if_fifo_bus.ins_if_id
  inst_fifo.io.in.bits.inst_bundle.pc := if_fifo_bus.pc_debug_if_id
  inst_fifo.io.in.bits.inst_bundle.inst_valid := if_fifo_bus.ins_valid_if_id
  for (i <- 0 to 1) {
    inst_fifo.io.in.bits.inst_bundle.pred_jump_target(i) := predictor.io.predicts(i).target
    inst_fifo.io.in.bits.inst_bundle.pred_jump_taken(i) := predictor.io.predicts(i).predict
  }
  inst_fifo.io.in.valid := if_fifo_bus.bus_valid
  if_module.io.next_allowin := inst_fifo.io.in.ready
  // fifo-id
  fifo_id_bus.ins_if_id := inst_fifo.io.out.bits.inst_bundle.inst
  fifo_id_bus.pc_debug_if_id := inst_fifo.io.out.bits.inst_bundle.pc
  fifo_id_bus.ins_valid_if_id := inst_fifo.io.out.bits.inst_bundle.inst_valid
  fifo_id_bus.bus_valid := inst_fifo.io.out.valid && !fetch_force_cancel && !id_pf_bus.bus_valid
  fifo_id_bus.predict_jump_target_if_id := inst_fifo.io.out.bits.inst_bundle.pred_jump_target
  fifo_id_bus.predict_jump_taken_if_id := inst_fifo.io.out.bits.inst_bundle.pred_jump_taken
  inst_fifo.io.out.ready := id_allowin
  inst_fifo.io.in.bits.flush := id_pf_bus.bus_valid || pipeline_flush_ex


  // 译码
  // 使用fifo来替代译码阶段的来源寄存器

  val id_reg: FetchDecodeBundle = RegAutoFlip(next = fifo_id_bus, init = {
    val bundle: FetchDecodeBundle = Wire(new FetchDecodeBundle)
    bundle.defaults()
    bundle
  }, this_allowin = id_allowin, force_reset = reset.asBool() || pipeline_flush_ex)


  val id_module: InsDecode = Module(new InsDecode)
  id_module.io.if_id_in := id_reg
  id_module.io.regfile_read1 := regfile.io.rdata1
  id_module.io.regfile_read2 := regfile.io.rdata2
  id_module.io.bypass_bus := bypass_bus
  id_module.io.flush := pipeline_flush_ex
  decoder_predictor := id_module.io.id_pred_out
  // 回馈给预取阶段的输出
  id_pf_bus := id_module.io.id_pf_out

  // 请求寄存器堆
  regfile.io.raddr1 := id_module.io.regfile_raddr1
  regfile.io.raddr2 := id_module.io.regfile_raddr2

  id_ex_bus := id_module.io.id_ex_out
  id_allowin := id_module.io.this_allowin


  // 执行
  val ex_reg: Vec[DecodeExecuteBundle] = RegDualAutoFlip(next = id_ex_bus, init = {
    val bundle: DecodeExecuteBundle = Wire(new DecodeExecuteBundle)
    bundle.defaults()
    bundle
  }, this_allowin = ex_allowin)

  val ex_module: InsExecute = Module(new InsExecute)


  // 直接接入ram的通路
  ex_module.io.id_ex_in := ex_reg
  ex_module.io.ex_hilo.hi_in := hi
  ex_module.io.ex_hilo.lo_in := lo
  ex_module.io.cp0_hazard_bypass_ms_ex := cp0_hazard_bypass_ms_ex
  ex_module.io.cp0_hazard_bypass_wb_ex := cp0_hazard_bypass_wb_ex
  ex_module.io.cp0_ex_in.cp0_cause_ip := cp0_cause_ip
  ex_module.io.cp0_ex_in.cp0_status_im := cp0_status_im
  for (i <- 0 to 1) {
    ex_module.io.data_ram_addr_ok := io.data_sram_like_io(i).addr_ok
  }
  io.data_sram_like_io(0).req := ex_module.io.ex_ram_out.mem_en
  io.data_sram_like_io(0).wr := ex_module.io.ex_ram_out.mem_wen
  io.data_sram_like_io(0).addr := ex_module.io.ex_ram_out.mem_addr
  io.data_sram_like_io(0).size := ex_module.io.ex_ram_out.mem_size
  io.data_sram_like_io(0).wdata := ex_module.io.ex_ram_out.mem_wdata
  io.data_sram_like_io(1) := DontCare
  val ms_ready_go: Bool      = Wire(Bool())
  val ms_ram_wen : Vec[Bool] = Wire(Vec(2, Bool()))


  ex_ms_bus := ex_module.io.ex_ms_out
  ex_allowin := ex_module.io.this_allowin
  bypass_bus.bp_ex_id := ex_module.io.bypass_ex_id
  hi_next := ex_module.io.ex_hilo.hi_out
  lo_next := ex_module.io.ex_hilo.lo_out
  hi_wen := ex_module.io.ex_hilo.hi_wen
  lo_wen := ex_module.io.ex_hilo.lo_wen
  ex_module.io.cp0_ex_in := cp0_ex
  ex_cp0 := ex_module.io.ex_cp0_out
  pipeline_flush_ex := ex_module.io.pipeline_flush
  to_exception_service_en_ex_pf := ex_module.io.ex_pf_out.to_exception_service_en_ex_pf
  to_epc_en_ex_pf := ex_module.io.ex_pf_out.to_epc_en_ex_pf


  // 访存
  val ms_reg: Vec[ExecuteMemoryBundle] = RegDualAutoFlip(next = ex_ms_bus, init = {
    val bundle = Wire(new ExecuteMemoryBundle)
    bundle.defaults()
    bundle
  }, this_allowin = ms_allowin)

  val ms_module: InsMemory = Module(new InsMemory)
  ms_module.io.ex_ms_in := ms_reg
  ms_module.io.mem_rdata := io.data_sram_like_io(0).rdata
  ms_module.io.data_ram_data_ok := io.data_sram_like_io(0).data_ok
  ms_wb_bus := ms_module.io.ms_wb_out
  ms_allowin := ms_module.io.this_allowin
  for (i <- 0 to 1) {
    ms_ram_wen(i) := ms_reg(i).mem_wen
  }
  cp0_hazard_bypass_ms_ex := ms_module.io.cp0_hazard_bypass_ms_ex
  bypass_bus.bp_ms_id := ms_module.io.bypass_ms_id
  ms_ready_go := ms_module.io.ram_access_done
  // 写回
  val wb_reg: Vec[MemoryWriteBackBundle] = RegDualAutoFlip(next = ms_wb_bus, init = {
    val bundle = Wire(new MemoryWriteBackBundle)
    bundle.defaults()
    bundle
  }, this_allowin = wb_allowin)

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
  for (i <- 0 to 1) {
    cp0.io.wb_cp0(i).wen := wb_module.io.cp0_wen(i)
    cp0.io.wb_cp0(i).wdata := wb_module.io.cp0_wdata(i)
    cp0.io.wb_cp0(i).regsel := wb_module.io.cp0_sel(i)
    cp0.io.wb_cp0(i).regaddr := wb_module.io.cp0_addr(i)
    wb_module.io.cp0_rdata(i) := cp0.io.wb_cp0(i).rdata
  }
  cp0_hazard_bypass_wb_ex := wb_module.io.cp0_hazard_bypass_wb_ex

  io.debug.debug_wb_pc := Cat(wb_module.io.pc_wb(1), wb_module.io.pc_wb(0))
  io.debug.debug_wb_rf_wnum := Cat(wb_module.io.regfile_waddr(1), wb_module.io.regfile_waddr(0))
  io.debug.debug_wb_rf_wen := Cat(VecInit(Seq.fill(4)(wb_module.io.regfile_wen(1))).asUInt(),
    VecInit(Seq.fill(4)(wb_module.io.regfile_wen(0))).asUInt())
  io.debug.debug_wb_rf_wdata := Cat(wb_module.io.regfile_wdata(1), wb_module.io.regfile_wdata(0))

  //  io.debug.debug_wb_pc := wb_module.io.pc_wb(0)
  //  io.debug.debug_wb_rf_wnum := wb_module.io.regfile_waddr(0)
  //  io.debug.debug_wb_rf_wen := VecInit(Seq.fill(4)(wb_module.io.regfile_wen(0))).asUInt()
  //  io.debug.debug_wb_rf_wdata := wb_module.io.regfile_wdata(0)

  id_module.io.next_allowin := ex_allowin
  ex_module.io.next_allowin := ms_allowin
  ms_module.io.next_allowin := wb_allowin


}

object CpuTopSRamLike extends App {
  (new ChiselStage).emitVerilog(new CpuTopSRamLike(0xbfbffffcL))
}