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
  val jump_val_id_pf: Vec[UInt]       = Output(Vec(2, UInt(32.W)))
}

class InsPreFetchBundle extends Bundle {
  val pc                 : UInt                 = Input(UInt(32.W))
  val id_pf_in           : DecodePreFetchBundle = Input(new DecodePreFetchBundle)
  val regfile_read1      : UInt                 = Input(UInt(32.W))
  val ins_ram_addr       : UInt                 = Output(UInt(32.W))
  val ins_ram_en         : Bool                 = Output(Bool())
  val next_pc            : UInt                 = Output(UInt(32.W))
  val pc_wen             : Bool                 = Output(Bool())
  val delay_slot_pc_pf_if: UInt                 = Output(UInt(32.W))

}

// 预取阶段，向同步RAM发起请求
class InsPreFetch extends Module {
  val io     : InsPreFetchBundle = IO(new InsPreFetchBundle())
  val next_pc: UInt              = Wire(UInt(32.W))
  next_pc := 0.U
  switch(io.id_pf_in.jump_sel_id_pf) {
    is(InsJumpSel.delay_slot_pc) {
      next_pc := io.pc + 4.U
    }
    is(InsJumpSel.pc_add_offset) {
      next_pc := io.id_pf_in.jump_val_id_pf(0)
    }
    is(InsJumpSel.pc_cat_instr_index) {
      next_pc := io.id_pf_in.jump_val_id_pf(1)
    }
    is(InsJumpSel.regfile_read1) {
      next_pc := io.regfile_read1
    }
  }

  io.next_pc := next_pc
  // 无暂停，恒1
  io.ins_ram_en := true.B
  io.ins_ram_addr := next_pc
  io.delay_slot_pc_pf_if := io.pc + 4.U
  io.pc_wen := 1.B
}

class InsFetchBundle extends Bundle {
  val delay_slot_pc_pf_if: UInt = Input(UInt(32.W)) // 延迟槽pc值
  val ins_ram_data       : UInt = Input(UInt(32.W))

  val if_id_out: FetchDecodeBundle = Output(new FetchDecodeBundle)
}

// 获取同步RAM的数据
class InsFetch extends Module {
  val io: InsFetchBundle = IO(new InsFetchBundle())

  io.if_id_out.ins_if_id := io.ins_ram_data
  io.if_id_out.pc_if_id := io.delay_slot_pc_pf_if
}
