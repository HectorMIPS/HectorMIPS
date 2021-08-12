package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.stage.ChiselStage
import chisel3.util._
import com.github.hectormips.RamState
import com.github.hectormips.pipeline.cp0.ExceptionConst

object InsJumpSel extends OneHotEnum {
  val seq_pc            : Type = Value(1.U)
  val pc_add_offset     : Type = Value(2.U)
  val pc_cat_instr_index: Type = Value(4.U)
  val regfile_read1     : Type = Value(8.U)
}

// TODO: 将跳转失败的判断组合逻辑放在buffer中
class DecodePreFetchBundle extends Bundle {

  val bus_valid            : Bool                = Bool()
  val predict_branch_bundle: PredictBranchBundle = new PredictBranchBundle

  def defaults(): Unit = {
    predict_branch_bundle.defaults()
    bus_valid := 0.B
  }
}

class ExecutePrefetchBundle extends Bundle {
  val to_exception_service_en_ex_pf    : Bool = Bool()
  val to_exception_service_tlb_en_ex_pf: Bool = Bool()
  val to_epc_en_ex_pf                  : Bool = Bool()
  val refetch_flag                     : Bool = Bool()
  val refetch_pc                       : UInt = UInt(32.W)

  def defaults(): Unit = {
    to_exception_service_en_ex_pf := 0.B
    to_exception_service_tlb_en_ex_pf := 0.B
    to_epc_en_ex_pf := 0.B
    refetch_flag := 0.B
    refetch_pc := 0xbfc00000L.U
  }

  def valid: Bool = {
    to_epc_en_ex_pf || to_exception_service_en_ex_pf || refetch_flag || to_exception_service_tlb_en_ex_pf
  }
}


class InsPreFetchBundle extends WithAllowin {
  val pc                 : UInt                        = Input(UInt(32.W))
  val pc_seq4            : UInt                        = Input(UInt(32.W))
  val pc_seq8            : UInt                        = Input(UInt(32.W))
  val id_pf_in           : DecodePreFetchBundle        = Input(new DecodePreFetchBundle)
  val ins_ram_addr       : UInt                        = Output(UInt(32.W))
  val ins_ram_en         : Bool                        = Output(Bool())
  val next_pc            : UInt                        = Output(UInt(32.W))
  val pc_wen             : Bool                        = Output(Bool())
  // 一次读入两条指令
  val ex_pf_in           : ExecutePrefetchBundle       = Input(new ExecutePrefetchBundle)
  val flush              : Bool                        = Input(Bool())
  val cp0_pf_epc         : UInt                        = Input(UInt(32.W))
  val fetch_state        : RamState.Type               = Input(RamState())
  // 上一个请求的valid信号，每次data_ok的时候被刷新，用于判断下一次跳转+4/+8
  val inst_sram_ins_valid: UInt                        = Input(UInt(2.W))
  val data_ok            : Bool                        = Input(Bool())
  val addr_ok            : Bool                        = Input(Bool())
  val pred_pf_in         : Vec[PredictorFetcherBundle] = Input(Vec(2, new PredictorFetcherBundle))
  val pf_pred_out        : Vec[FetcherPredictorBundle] = Output(Vec(2, new FetcherPredictorBundle))
  val in_valid           : Bool                        = Input(Bool()) // 传入预取的输入是否有效

}

// 预取阶段，向同步RAM发起请求
class InsPreFetch extends Module {
  val io       : InsPreFetchBundle = IO(new InsPreFetchBundle())
  val pc_jump  : UInt              = Wire(UInt(32.W))
  val seq_pc_4 : UInt              = io.pc_seq4
  // 如果上次取出的指令只有一个有效，则下一个取的指令只+4
  val seq_pc_8 : UInt              = Mux(!io.inst_sram_ins_valid(1), io.pc_seq4, io.pc_seq8)
  val seq_epc_8: UInt              = io.cp0_pf_epc
  // 如果有load-to-branch的情况，清空了队列之后还需要等待
  val req      : Bool              = io.next_allowin &&
    (io.fetch_state === RamState.waiting_for_request || io.fetch_state === RamState.requesting || io.data_ok) &&
    !io.flush
  pc_jump := io.id_pf_in.predict_branch_bundle.jumpTarget
  // 如果上一次取出的指令是跳转指令但是需要等待延迟槽指令的时候，则将跳转地址存进buffer里
  val branch_predict_target_buffer      : UInt = RegInit(0.U(32.W))
  val branch_predict_target_buffer_valid: Bool = RegInit(0.B)

  // 如果来自id的bus有效 说明跳转被取消了 或者没有成功预测跳转
  val target_from_id: Bool = io.id_pf_in.predict_branch_bundle.hasPredictFail &&
    io.id_pf_in.bus_valid
  // 直到当前指令可以被decode接收时才发送新的请求
  val ready_go      : Bool = io.next_allowin


  val pc_out: UInt = MuxCase(seq_pc_8, Seq(
    io.ex_pf_in.refetch_flag -> io.ex_pf_in.refetch_pc,
    io.ex_pf_in.to_epc_en_ex_pf -> seq_epc_8,
    io.ex_pf_in.to_exception_service_tlb_en_ex_pf -> ExceptionConst.EXCEPTION_PROGRAM_ADDR_REFILL,
    io.ex_pf_in.to_exception_service_en_ex_pf -> ExceptionConst.EXCEPTION_PROGRAM_ADDR,
    // 如果队列已满或者还上一条请求还没有返回则原地踏步
    // 如果发起了请求但是没有addr_ok同样需要原地踏步
    (!io.next_allowin || (io.fetch_state === RamState.waiting_for_response && !io.data_ok) ||
      (req && !io.addr_ok &&
        (io.fetch_state === RamState.waiting_for_request || io.fetch_state === RamState.requesting))) -> io.pc,
    // 如果decode阶段要求重新取指，则需要清空队列、取消当前请求，并且请求目的地址的指令
    (ready_go && target_from_id) -> pc_jump,
    (branch_predict_target_buffer_valid && !target_from_id) -> branch_predict_target_buffer,
    // 如果预测上一次取出的第一条指令需要跳转并且延迟槽指令已经被取出，则直接跳转前往目的地
    (io.pred_pf_in(0).predict && io.inst_sram_ins_valid(1)) -> io.pred_pf_in(0).target,
    // 如果第一条指令需要跳转但是取出的第二条指令无效，则需要先取出延迟槽指令之后再跳转前往目的地
    // 如果第二条指令需要跳转，则必须等待延迟槽指令
    (io.pred_pf_in(0).predict && !io.inst_sram_ins_valid(1)) -> seq_pc_4,
  ))
  io.pf_pred_out(0).pc := io.pc
  io.pf_pred_out(1).pc := seq_pc_4

  when(!target_from_id && !io.ex_pf_in.refetch_flag && !io.ex_pf_in.to_exception_service_en_ex_pf &&
    !io.ex_pf_in.to_epc_en_ex_pf) {
    when(((io.pred_pf_in(0).predict && !io.inst_sram_ins_valid(1)) ||
      (io.inst_sram_ins_valid(1) && io.pred_pf_in(1).predict)) && req &&
      io.addr_ok && !branch_predict_target_buffer_valid) {
      // 当需要等待延迟槽指令并且请求已经被发出的时候将buffer置为valid
      branch_predict_target_buffer_valid := 1.B
      branch_predict_target_buffer := Mux(io.pred_pf_in(0).predict, io.pred_pf_in(0).target, io.pred_pf_in(1).target)

    }.elsewhen(req && io.addr_ok && branch_predict_target_buffer_valid) {
      // 当延迟槽指令请求完毕之后将buffer置无效
      branch_predict_target_buffer_valid := 0.B
    }
  }.otherwise {
    branch_predict_target_buffer_valid := 0.B
  }
  // 如果当前正在取的指令
  io.next_pc := pc_out
  // 只要fifo没满就继续发请求
  io.ins_ram_en := req
  io.ins_ram_addr := pc_out

  // 发送完一条请求更新一次pc
  io.pc_wen := req && io.addr_ok
  io.this_allowin := DontCare

}

class InsSufFetchBundle extends WithAllowin {
  val ins_ram_data: UInt = Input(UInt(64.W))

  val if_id_out                  : FetchDecodeBundle = Output(new FetchDecodeBundle)
  val pc_debug_icache_if         : UInt              = Input(UInt(32.W))
  val pc_debug_pf_if             : UInt              = Input(UInt(32.W))
  val flush                      : Bool              = Input(Bool())
  val ins_ram_data_ok            : Bool              = Input(Bool())
  val ins_ram_req                : Bool              = Input(Bool())
  val ins_ram_addr_ok            : Bool              = Input(Bool())
  val ins_ram_ex                 : UInt              = Input(UInt(3.W))
  val ins_ram_data_valid         : UInt              = Input(UInt(2.W))
  val ins_ram_predict_jump_taken : Vec[Bool]         = Input(Vec(2, Bool()))
  val ins_ram_predict_jump_target: Vec[UInt]         = Input(Vec(2, UInt(32.W)))
  val fetch_state                : RamState.Type     = Input(RamState())
}

// 获取同步RAM的数据
class InsSufFetch extends Module {
  val io: InsSufFetchBundle = IO(new InsSufFetchBundle())

  val fetch_tlb_ex: Bool = io.ins_ram_req && io.ins_ram_addr_ok && io.ins_ram_ex =/= 0.U
  // 取消sufetch模块的缓冲，直接放入fifo
  io.if_id_out.ins_if_id := Mux(fetch_tlb_ex, 0.U, io.ins_ram_data)
  io.if_id_out.exception_flag := Mux(io.ins_ram_ex(0), ExceptionConst.EXCEPTION_TLB_REFILL_FETCH, 0.U) |
    Mux(io.ins_ram_ex(1), ExceptionConst.EXCEPTION_TLB_INVALID_FETCH, 0.U)
  io.if_id_out.ins_valid_if_id := Mux(fetch_tlb_ex, 1.U, io.ins_ram_data_valid)
  io.if_id_out.bus_valid := !reset.asBool() && !io.flush &&
    // 由缓冲寄存器读出
    ((io.ins_ram_data_ok && io.fetch_state === RamState.waiting_for_response) ||
      // 如果发送请求的过程中出现了例外，则直接使用带有exception_flag的nop传向后方流水
      fetch_tlb_ex)
  io.if_id_out.pc_debug_if_id := Mux(fetch_tlb_ex, io.pc_debug_pf_if, io.pc_debug_icache_if)
  for (i <- 0 to 1) {
    io.if_id_out.predict_jump_taken_if_id(i) := Mux(fetch_tlb_ex, 0.B, io.ins_ram_predict_jump_taken(i))
    io.if_id_out.predict_jump_target_if_id(i) := io.ins_ram_predict_jump_target(i)
  }
  io.this_allowin := !reset.asBool() && io.next_allowin
}

object InsFetch extends App {
  (new ChiselStage).emitVerilog(new InsPreFetch)
  (new ChiselStage).emitVerilog(new InsSufFetch)
}