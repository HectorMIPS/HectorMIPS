package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.stage.ChiselStage
import chisel3.util._
import com.github.hectormips.RamState

object InsJumpSel extends OneHotEnum {
  val seq_pc            : Type = Value(1.U)
  val pc_add_offset     : Type = Value(2.U)
  val pc_cat_instr_index: Type = Value(4.U)
  val regfile_read1     : Type = Value(8.U)
}

class DecodePreFetchBundle extends Bundle {
  val jump_sel_id_pf  : InsJumpSel.Type = Output(InsJumpSel())
  val jump_val_id_pf  : Vec[UInt]       = Output(Vec(3, UInt(32.W)))
  val is_jump         : Bool            = Output(Bool())
  val bus_valid       : Bool            = Output(Bool())
  val jump_taken      : Bool            = Output(Bool())
  val stall_id_pf     : Bool            = Output(Bool())
  // 请求新获取的指令数
  val inst_num_request: UInt            = Output(UInt(2.W))
  // 是否需要将pc回退4
  val pc_force_step_4 : Bool            = Output(Bool())

  def defaults(): Unit = {
    jump_sel_id_pf := InsJumpSel.seq_pc
    jump_val_id_pf := VecInit(Seq(0.U, 0.U, 0.U))
    is_jump := 0.B
    bus_valid := 1.B
    jump_taken := 0.B
    stall_id_pf := 0.B
    inst_num_request := 2.U
    pc_force_step_4 := 1.B
  }
}

class ExecutePrefetchBundle extends Bundle {
  val to_exception_service_en_ex_pf: Bool = Bool()
  val to_epc_en_ex_pf              : Bool = Bool()

  def defaults(): Unit = {
    to_exception_service_en_ex_pf := 0.B
    to_epc_en_ex_pf := 0.B
  }
}


object BranchState extends ChiselEnum {
  val no_branch, waiting_for_delay_slot = Value
}

class InsPreFetchBundle extends WithAllowin {
  val pc                           : UInt                 = Input(UInt(32.W))
  val id_pf_in                     : DecodePreFetchBundle = Flipped(new DecodePreFetchBundle)
  val ins_ram_addr                 : UInt                 = Output(UInt(32.W))
  val ins_ram_en                   : Bool                 = Output(Bool())
  val next_pc                      : UInt                 = Output(UInt(32.W))
  val pc_wen                       : Bool                 = Output(Bool())
  // 一次读入两条指令
  val pc_debug_pf_if               : UInt                 = Output(UInt(32.W))
  val to_exception_service_en_ex_pf: Bool                 = Input(Bool())
  val to_epc_en_ex_pf              : Bool                 = Input(Bool())
  val flush                        : Bool                 = Input(Bool())
  val cp0_pf_epc                   : UInt                 = Input(UInt(32.W))
  val fetch_state                  : RamState.Type        = Input(RamState())
  val ins_ram_data_ok              : Bool                 = Input(Bool())
  val inst_num_request             : UInt                 = Output(UInt(2.W))

  val in_valid: Bool = Input(Bool()) // 传入预取的输入是否有效

}

// 预取阶段，向同步RAM发起请求
class InsPreFetch extends Module {
  val io               : InsPreFetchBundle = IO(new InsPreFetchBundle())
  val pc_jump          : UInt              = Wire(UInt(32.W))
  val seq_pc_4         : UInt              = io.pc + 4.U
  val seq_pc_8         : UInt              = Mux(io.id_pf_in.pc_force_step_4, seq_pc_4, io.pc + 8.U)
  val id_or_ex_in_valid: Bool              = io.id_pf_in.bus_valid || io.to_exception_service_en_ex_pf || io.to_epc_en_ex_pf
  val req              : Bool              = !io.id_pf_in.stall_id_pf && io.next_allowin && id_or_ex_in_valid &&
    (io.fetch_state === RamState.waiting_for_request || io.fetch_state === RamState.requesting)
  pc_jump := seq_pc_4

  switch(io.id_pf_in.jump_sel_id_pf) {
    is(InsJumpSel.seq_pc) {
      pc_jump := Mux(io.id_pf_in.inst_num_request === 2.U, seq_pc_8, seq_pc_4)
    }
    is(InsJumpSel.pc_add_offset) {
      pc_jump := io.id_pf_in.jump_val_id_pf(0)
    }
    is(InsJumpSel.pc_cat_instr_index) {
      pc_jump := io.id_pf_in.jump_val_id_pf(1)
    }
    is(InsJumpSel.regfile_read1) {
      pc_jump := io.id_pf_in.jump_val_id_pf(2)
    }

  }
  // 已经执行完成延迟槽指令 跳转至目标处
  val jump_now_target: Bool = io.id_pf_in.bus_valid && io.id_pf_in.jump_taken
  val no_jump        : Bool = !io.id_pf_in.bus_valid || (io.id_pf_in.bus_valid && !io.id_pf_in.jump_taken)
  // 直到当前指令可以被decode接收时才发送新的请求
  val ready_go       : Bool = io.next_allowin && (io.fetch_state === RamState.waiting_for_request)
  val pc_out         : UInt = MuxCase(seq_pc_8, Seq(
    io.to_epc_en_ex_pf -> (io.cp0_pf_epc - 4.U),
    io.to_exception_service_en_ex_pf -> ExceptionConst.EXCEPTION_PROGRAM_ADDR,
    // 仅当当前指令已经准备完毕并且可以被接收时才读下一条指令，否则值一直是当前的指令的pc
    (io.id_pf_in.stall_id_pf || !io.next_allowin || io.fetch_state =/= RamState.waiting_for_request) -> io.pc,
    // 根据decode阶段的回馈来发送新的指令请求
    (ready_go && io.id_pf_in.bus_valid && no_jump) -> seq_pc_8,
    // 否则跳转至目标处
    (ready_go && jump_now_target) -> pc_jump
  ))
  io.next_pc := pc_out
  // 无暂停，恒1
  // 当需要暂停的时候，需要同步ram保持上一个周期的读出内容，使能0
  io.ins_ram_en := req
  io.ins_ram_addr := pc_out
  // 永远可以写入pc，直接通过控制io.next_pc的值来实现暂停等操作来简化控制模型
  io.pc_wen := req
  io.this_allowin := !reset.asBool() && io.next_allowin
  io.pc_debug_pf_if := pc_out
  io.inst_num_request := io.id_pf_in.inst_num_request

}

class InsSufFetchBundle extends WithAllowin {
  val ins_ram_data: UInt = Input(UInt(64.W))

  val if_id_out         : FetchDecodeBundle = Output(new FetchDecodeBundle)
  val pc_debug_pf_if    : UInt              = Input(UInt(32.W))
  val flush             : Bool              = Input(Bool())
  val ins_ram_data_ok   : Bool              = Input(Bool())
  val ins_ram_data_valid: UInt              = Input(UInt(2.W))
  val fetch_state       : RamState.Type     = Input(RamState())
  val inst_num_request  : UInt              = Input(UInt(2.W))
}

// 获取同步RAM的数据
class InsSufFetch extends Module {
  val io                 : InsSufFetchBundle = IO(new InsSufFetchBundle())
  // 暂存区拓宽到两个，用于存储两条指令
  val if_buffer_reg      : UInt              = Reg(UInt(64.W))
  val if_buffer_valid_reg: UInt              = Reg(UInt(2.W))
  when(io.ins_ram_data_ok) {
    if_buffer_reg := io.ins_ram_data
    if_buffer_valid_reg := io.ins_ram_data_valid
  }.elsewhen(io.next_allowin) {
    if_buffer_valid_reg := 0.U
  }

  io.if_id_out.ins_if_id := if_buffer_reg
  io.if_id_out.ins_valid_if_id := if_buffer_valid_reg
  io.if_id_out.bus_valid := !reset.asBool() && !io.flush &&
    // 由缓冲寄存器读出
    (io.fetch_state === RamState.waiting_for_read)
  io.if_id_out.pc_debug_if_id := io.pc_debug_pf_if
  io.if_id_out.req_count := io.inst_num_request
  io.this_allowin := !reset.asBool() && io.next_allowin
}

object InsFetch extends App {
  (new ChiselStage).emitVerilog(new InsPreFetch)
  (new ChiselStage).emitVerilog(new InsSufFetch)
}