package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.Mux1H
import chisel3.util._

object InsJumpSel extends ChiselEnum {
  val delay_slot_pc     : Type = Value(1.U)
  val pc_add_offset     : Type = Value(2.U)
  val pc_cat_instr_index: Type = Value(4.U)
  val regfile_read1     : Type = Value(8.U)
}

class DecodePreFetchBundle extends Bundle {
  val jump_sel_id_pf: InsJumpSel.Type = Output(InsJumpSel())
  val jump_val_id_pf: Vec[UInt]       = Output(Vec(3, UInt(32.W)))
  val bus_valid     : Bool            = Output(Bool())
  val jump_taken    : Bool            = Output(Bool())
  val stall_id_pf   : Bool            = Output(Bool())
}

class InsPreFetchBundle extends WithAllowin {
  val pc                           : UInt                 = Input(UInt(32.W))
  val id_pf_in                     : DecodePreFetchBundle = Input(new DecodePreFetchBundle)
  val ins_ram_addr                 : UInt                 = Output(UInt(32.W))
  val ins_ram_en                   : Bool                 = Output(Bool())
  val next_pc                      : UInt                 = Output(UInt(32.W))
  val pc_wen                       : Bool                 = Output(Bool())
  val delay_slot_pc_pf_if          : UInt                 = Output(UInt(32.W))
  val pc_debug_pf_if               : UInt                 = Output(UInt(32.W))
  val exception_flag_pf_if         : UInt                 = Output(UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W))
  val to_exception_service_en_ex_pf: Bool                 = Input(Bool())
  val to_epc_en_ex_pf              : Bool                 = Input(Bool())
  val flush                        : Bool                 = Input(Bool())
  val cp0_pf_epc                   : UInt                 = Input(UInt(32.W))


  val in_valid   : Bool = Input(Bool()) // 传入预取的输入是否有效
  val pf_if_valid: Bool = Output(Bool())

}

// 预取阶段，向同步RAM发起请求
class InsPreFetch extends Module {
  val io     : InsPreFetchBundle = IO(new InsPreFetchBundle())
  val next_pc: UInt              = Wire(UInt(32.W))
  val seq_pc : UInt              = io.pc + 4.U
  next_pc := seq_pc

  switch(io.id_pf_in.jump_sel_id_pf) {
    is(InsJumpSel.delay_slot_pc) {
      next_pc := seq_pc
    }
    is(InsJumpSel.pc_add_offset) {
      next_pc := io.id_pf_in.jump_val_id_pf(0)
    }
    is(InsJumpSel.pc_cat_instr_index) {
      next_pc := io.id_pf_in.jump_val_id_pf(1)
    }
    is(InsJumpSel.regfile_read1) {
      next_pc := io.id_pf_in.jump_val_id_pf(2)
    }
  }
  val ready_go: Bool = !io.id_pf_in.stall_id_pf
  val pc_out  : UInt = MuxCase(seq_pc, Seq(
    io.to_epc_en_ex_pf -> (io.cp0_pf_epc - 4.U),
    (io.to_exception_service_en_ex_pf) -> ExceptionConst.EXCEPTION_PROGRAM_ADDR,
    (io.id_pf_in.bus_valid && io.id_pf_in.jump_taken && !io.id_pf_in.stall_id_pf && io.next_allowin) -> next_pc,
    (io.id_pf_in.stall_id_pf || !io.next_allowin) -> io.pc
  ))
  io.next_pc := pc_out
  // 无暂停，恒1
  // 当需要暂停的时候，需要同步ram保持上一个周期的读出内容，使能0
  io.ins_ram_en := !io.id_pf_in.stall_id_pf && io.next_allowin
  io.ins_ram_addr := pc_out
  io.delay_slot_pc_pf_if := io.pc + 4.U
  // 永远可以写入pc，直接通过控制io.next_pc的值来实现暂停等操作来简化控制模型
  io.pc_wen := 1.B
  io.pf_if_valid := ready_go && !reset.asBool() && io.in_valid && !io.flush
  io.this_allowin := !reset.asBool() && io.next_allowin
  io.pc_debug_pf_if := io.pc
  io.exception_flag_pf_if := 0.U

}

class InsSufFetchBundle extends WithAllowin {
  val delay_slot_pc_pf_if: UInt = Input(UInt(32.W)) // 延迟槽pc值
  val ins_ram_data       : UInt = Input(UInt(32.W))

  val pf_if_valid         : Bool              = Input(Bool())
  val if_id_out           : FetchDecodeBundle = Output(new FetchDecodeBundle)
  val pc_debug_pf_if      : UInt              = Input(UInt(32.W))
  val exception_flag_pf_if: UInt              = Input(UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W))
  val is_delay_slot_id_if : Bool              = Input(Bool())
  val flush               : Bool              = Input(Bool())
}

// 获取同步RAM的数据
class InsSufFetch extends Module {
  val io: InsSufFetchBundle = IO(new InsSufFetchBundle())

  io.if_id_out.ins_if_id := io.ins_ram_data
  io.if_id_out.pc_if_id := io.delay_slot_pc_pf_if
  io.if_id_out.bus_valid := !reset.asBool() && io.pf_if_valid && !io.flush
  io.if_id_out.pc_debug_if_id := io.pc_debug_pf_if
  io.if_id_out.exception_flags := io.exception_flag_pf_if
  io.if_id_out.is_delay_slot := io.is_delay_slot_id_if
  io.this_allowin := !reset.asBool() && io.next_allowin
}
