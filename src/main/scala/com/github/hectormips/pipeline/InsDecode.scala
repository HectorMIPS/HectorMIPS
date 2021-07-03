package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.util._

// 缓存来自取指阶段的输出
class DecodeBuf extends Module {
  val io: Bundle {
    val ins_in: UInt
    val en: Bool
    val ins_out: UInt
  }
  = IO(new Bundle {
    val ins_in: UInt = Input(UInt(32.W))
    val en: Bool = Input(Bool())
    val ins_out: UInt = Output(UInt(32.W))
  })
  val reg: UInt = Reg(32.U)
  io.ins_out := reg
  when(io.en) {
    reg := io.ins_in
  }
}

class InsDecode extends Module {
  val io: Bundle {
    val raw_ins: UInt
    val pc: UInt // 延迟槽对应的pc（进入的指令 + 4）
    val jump_sel: Vec[Bool] // 均为回传给fetch模块的参数
    val iram_en: Bool
    val iram_we: Bool
    val alu_src1: Vec[Bool]
    val alu_src2: Vec[Bool]
    val alu_op: Vec[Bool] // 12位的alu指令独热码
    val dram_en: Bool
    val dram_we: Bool
    val regfile_we: Bool
    val regfile_addr: Vec[Bool] // 3位供写地址生成 1:rd 2:rt 3:32
    val regfile_wsrc: Bool // 1位写数据生成 0:alu结果 1:ram读
    val ins_opcode: UInt
    val ins_rs: UInt
    val ins_rt: UInt
    val ins_rd: UInt
    val ins_sa: UInt
    val ins_imm: UInt
  } = IO(new Bundle {
    val raw_ins: UInt = Input(UInt(32.W))
    val pc: UInt = Input(UInt(32.W))
    // 1: pc=pc+4
    // 2: pc=pc+(signed)(offset<<2)
    // 4: pc=pc[31:28]|instr_index<<2
    // 8: pc=regfile[1]
    val jump_sel: Vec[Bool] = Output(Vec(4, Bool()))
    val iram_en: Bool = Output(Bool())
    val iram_we: Bool = Output(Bool())
    val alu_src1: Vec[Bool] = Output(Vec(3, Bool()))
    val alu_src2: Vec[Bool] = Output(Vec(3, Bool()))
    val alu_op: Vec[Bool] = Output(Vec(12, Bool()))
    val dram_en: Bool = Output(Bool())
    val dram_we: Bool = Output(Bool())
    val regfile_we: Bool = Output(Bool())
    val regfile_addr: Vec[Bool] = Output(Vec(3, Bool()))
    val regfile_wsrc: Bool = Output(Bool())
    val ins_opcode: UInt = Output(UInt(6.W))
    val ins_rs: UInt = Output(UInt(5.W))
    val ins_rt: UInt = Output(UInt(5.W))
    val ins_rd: UInt = Output(UInt(5.W))
    val ins_sa: UInt = Output(UInt(5.W))
    val ins_imm: UInt = Output(UInt(16.W))
  })
  val opcode: UInt = io.raw_ins(31, 26)
  val sa: UInt = io.raw_ins(10, 6)
  val func: UInt = io.raw_ins(5, 0)
  val ins_addu: Bool = opcode === 0.U && sa === 0.U && func === "b100001".U
  val ins_addiu: Bool = opcode === "b001001".U
  val ins_subu: Bool = opcode === 0.U && sa === 0.U && func === "b100011".U
  val ins_lw: Bool = opcode === "b100011".U
  val ins_sw: Bool = opcode === "b101011".U
  val ins_beq: Bool = opcode === "b000100".U
  val ins_bne: Bool = opcode === "b000101".U
  val ins_jal: Bool = opcode === "b000011".U
  val ins_jr: Bool = opcode === "b000000".U && sa === "b00000".U && func === "b001000".U
  val ins_slt: Bool = opcode === "b000000".U && sa === "b00000".U && func === "b101010".U
  val ins_sltu: Bool = opcode === "b000000".U && sa === "b00000".U && func === "b101011".U
  val ins_sll: Bool = opcode === "b000000".U && sa === "b00000".U && func === "b000000".U
  val ins_srl: Bool = opcode === "b000000".U && sa === "b00000".U && func === "b000010".U
  val ins_sra: Bool = opcode === "b000000".U && sa === "b00000".U && func === "b000011".U
  val ins_lui: Bool = opcode === "b001111".U && sa === "b00000".U
  val ins_and: Bool = opcode === "b000000".U && sa === "b00000".U && func === "b100100".U
  val ins_or: Bool = opcode === "b000000".U && sa === "b00000".U && func === "b100101".U
  val ins_xor: Bool = opcode === "b000000".U && sa === "b00000".U && func === "b100110".U
  val ins_nor: Bool = opcode === "b000000".U && sa === "b00000".U && func === "b100111".U

  io.jump_sel(0) := ins_addu | ins_addiu | ins_subu | ins_lw | ins_sw |
    ins_slt | ins_sltu | ins_sll | ins_srl | ins_sra | ins_lui | ins_and | ins_or |
    ins_xor | ins_nor
  io.jump_sel(1) := ins_beq | ins_bne
  io.jump_sel(2) := ins_jal
  io.jump_sel(3) := ins_jr

  io.iram_en := 1.B
  io.iram_we := 0.B

  io.alu_src1(0) := ins_addu | ins_addiu | ins_subu | ins_lw | ins_sw |
    ins_slt | ins_sltu | ins_and | ins_or | ins_xor | ins_nor
  io.alu_src1(1) := ins_jal
  io.alu_src1(2) := ins_sll | ins_srl | ins_sra

  io.alu_src2(0) := ins_addu | ins_subu | ins_slt | ins_sltu | ins_sll | ins_srl |
    ins_sra | ins_and | ins_or | ins_xor | ins_nor
  io.alu_src2(1) := ins_addiu | ins_lw | ins_sw | ins_lui
  io.alu_src2(2) := ins_jal

  io.alu_op(0) := ins_addu | ins_addiu | ins_lw | ins_sw | ins_jal
  io.alu_op(1) := ins_subu
  io.alu_op(2) := ins_slt
  io.alu_op(3) := ins_sltu
  io.alu_op(4) := ins_and
  io.alu_op(5) := ins_nor
  io.alu_op(6) := ins_or
  io.alu_op(7) := ins_xor
  io.alu_op(8) := ins_sll
  io.alu_op(9) := ins_srl
  io.alu_op(10) := ins_sra
  io.alu_op(11) := ins_lui
  io.regfile_we := ins_addu | ins_addiu | ins_subu | ins_lw | ins_jal | ins_slt |
    ins_sltu | ins_sll | ins_srl | ins_sra | ins_lui | ins_and | ins_or | ins_xor | ins_nor
  io.regfile_addr(0) := ins_addu | ins_subu | ins_slt | ins_sltu | ins_sll | ins_srl |
    ins_sra | ins_and | ins_or | ins_xor | ins_nor
  io.regfile_addr(1) := ins_addiu | ins_lw | ins_lui
  io.regfile_addr(2) := ins_jal


  io.ins_opcode := opcode
  io.ins_rs := io.raw_ins(25, 21)
  io.ins_rt := io.raw_ins(20, 16)
  io.ins_rd := io.raw_ins(15, 11)
  io.ins_sa := sa
  io.ins_imm := io.raw_ins(15, 0)


  io.dram_en := ins_lw | ins_sw
  io.dram_we := ins_sw
  io.regfile_wsrc := ins_lw

  // 使用sint进行有符号拓展
  val offset: SInt = (io.raw_ins(15, 0) << 2).asSInt()
}