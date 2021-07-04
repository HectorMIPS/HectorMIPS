package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.util._
import AluOp._

class InsDecodeBundle extends Bundle {
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
  val alu_op: AluOp.Type = Output(AluOp())
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
  val pc_out: UInt = Output(UInt(32.W))
}

class InsDecode extends Module {
  val io: InsDecodeBundle = IO(new InsDecodeBundle)
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

  io.alu_op := Mux1H(Seq(
    (ins_addiu | ins_addiu | ins_lw | ins_sw | ins_jal) -> AluOp.op_add
  ))
  //  io.alu_op(0) := ins_addu | ins_addiu | ins_lw | ins_sw | ins_jal
  //  io.alu_op(1) := ins_subu
  //  io.alu_op(2) := ins_slt
  //  io.alu_op(3) := ins_sltu
  //  io.alu_op(4) := ins_and
  //  io.alu_op(5) := ins_nor
  //  io.alu_op(6) := ins_or
  //  io.alu_op(7) := ins_xor
  //  io.alu_op(8) := ins_sll
  //  io.alu_op(9) := ins_srl
  //  io.alu_op(10) := ins_sra
  //  io.alu_op(11) := ins_lui
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
  io.pc_out := io.pc
}