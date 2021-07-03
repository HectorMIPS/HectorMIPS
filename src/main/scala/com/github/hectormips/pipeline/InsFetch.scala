package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.util.Mux1H


// 预取阶段，向同步RAM发起请求
class InsPreFetch extends Module {
  val io: Bundle {
    val pc: UInt
    val jump_val: Vec[UInt] // 由于跳转导致的pc!=pc+4的可能值
    val jump_sel: Vec[Bool] // 选择跳转目标
    val dram_addr: UInt // 数据ram地址，直接使用next_pc
    val dram_en: Bool
    val next_pc: UInt // 下一条pc地址
  } = IO(new Bundle {
    val pc: UInt = Input(UInt(32.W))
    val jump_val: Vec[UInt] = Input(Vec(3, UInt(32.W)))
    val jump_sel: Vec[Bool] = Input(Vec(4, Bool()))
    val dram_addr: UInt = Output(UInt(32.W))
    val dram_en: Bool = Output(Bool())
    val next_pc: UInt = Output(Bool())
  })
  io.next_pc := Mux1H(Seq {
    io.jump_sel(0) -> (io.pc + 4.U)
    io.jump_sel(1) -> io.jump_val(0)
    io.jump_sel(2) -> io.jump_val(1)
    io.jump_sel(4) -> io.jump_val(2)
  })
  // 无暂停，恒1
  io.dram_en := true.B
  io.dram_addr := io.pc + 4.U
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

class InsFetchBuf extends Module {
  val io: Bundle {
    val ins_in: UInt
    val ins_out: UInt
  } = IO(new Bundle {
    val ins_in: UInt = Input(UInt(32.W))
    val ins_out: UInt = Output(UInt(32.W))
  })
  io.ins_out := io.ins_in
}
