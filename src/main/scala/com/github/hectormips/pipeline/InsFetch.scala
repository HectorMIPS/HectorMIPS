package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.Mux1H
import chisel3.util._

object InsJumpSel extends ChiselEnum {
  val delay_slot_pc: Type = Value(1.U)
  val pc_add_offset: Type = Value(2.U)
  val pc_cat_instr_index: Type = Value(4.U)
  val regfile_read1: Type = Value(8.U)
}

class InsPreFetchBundle extends Bundle {
  val pc: UInt = Input(UInt(32.W))
  val jump_val: Vec[UInt] = Input(Vec(3, UInt(32.W)))
  val jump_sel: InsJumpSel.Type = Input(InsJumpSel())
  val dram_addr: UInt = Output(UInt(32.W))
  val dram_en: Bool = Output(Bool())
  val next_pc: UInt = Output(UInt(32.W))
}

// 预取阶段，向同步RAM发起请求
class InsPreFetch extends Module {
  val io: InsPreFetchBundle = IO(new InsPreFetchBundle())
  val next_pc: UInt = Wire(UInt(32.W))
  next_pc := 0.U
  switch(io.jump_sel) {
    is(InsJumpSel.delay_slot_pc) {
      next_pc := next_pc + 4.U
    }
    is(InsJumpSel.pc_add_offset) {
      next_pc := io.jump_val(0)
    }
    is(InsJumpSel.pc_cat_instr_index) {
      next_pc := io.jump_val(1)
    }
    is(InsJumpSel.regfile_read1) {
      next_pc := io.jump_val(2)
    }
  }

  io.next_pc := next_pc
  // 无暂停，恒1
  io.dram_en := true.B
  io.dram_addr := next_pc
}

// 获取同步RAM的数据
class InsFetch extends Module {
  val io: Bundle {
    val next_pc: UInt
    val dram_data: UInt // dram的输出
    val ins: UInt // 传给缓存的指令
  } = IO(new Bundle {
    val next_pc: UInt = Input(UInt(32.W))
    val dram_data: UInt = Input(UInt(32.W))
    val ins: UInt = Output(UInt(32.W))
  })
  io.ins := io.dram_data
}
