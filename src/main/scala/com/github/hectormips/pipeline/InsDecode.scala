package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._

// 缓存来自取指阶段的输出
class DecodeBuf extends Module {
  val io: Bundle {
    val ins_in: UInt
    val ins_out: UInt
  }
  = IO(new Bundle {
    val ins_in: UInt = Input(UInt(32.W))
    val ins_out: UInt = Output(UInt(32.W))
  })
}

class InsDecode extends Module {
  val io: Bundle {
    val raw_ins: UInt
    val pc: UInt // 延迟槽对应的pc（进入的指令 + 4）
    val jump_val: Vec[UInt]
    val jump_sel: UInt // 均为回传给fetch模块的参数
    val iram_en: Bool
    val iram_we: Bool
    val alu_src1: UInt
    val alu_src2: UInt
    val alu_op: UInt // 12位的alu指令独热码
    val dram_en: Bool
    val dram_we: Bool
    val regfile_we: Bool
    val regfile_addr: UInt // 3位供写地址生成 1:rd 2:rt 3:32
    val regfile_wsrc: UInt // 1位写数据生成 0:alu结果 1:ram读
    val ins_opcode: UInt
    val ins_rs: UInt
    val ins_rt: UInt
    val ins_rd: UInt
    val ins_sa: UInt
    val ins_imm: UInt
  } = IO(new Bundle {
    val raw_ins: UInt = Input(UInt(32.W))
    val pc: UInt = Input(UInt(32.W))
    val jump_val: Vec[UInt] = Output(Vec(4, UInt(32.W)))
    val jump_sel: UInt = Output(UInt(4.W))
    val iram_en: Bool = Output(Bool())
    val iram_we: Bool = Output(Bool())
    val alu_src1: UInt = Output(UInt(3.W))
    val alu_src2: UInt = Output(UInt(3.W))
    val alu_op: UInt = Output(UInt(12.W))
    val dram_en: Bool = Output(Bool())
    val dram_we: Bool = Output(Bool())
    val regfile_we: Bool = Output(Bool())
    val regfile_addr: UInt = Output(UInt(3.W))
    val regfile_wsrc: UInt = Output(UInt(1.W))
    val ins_opcode: UInt = Output(UInt(6.W))
    val ins_rs: UInt = Output(UInt(5.W))
    val ins_rt: UInt = Output(UInt(5.W))
    val ins_rd: UInt = Output(UInt(5.W))
    val ins_sa: UInt = Output(UInt(5.W))
    val ins_imm: UInt = Output(UInt(16.W))
  })
}