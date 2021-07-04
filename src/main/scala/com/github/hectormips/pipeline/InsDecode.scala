package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.util._
import AluOp._
import chisel3.experimental.ChiselEnum
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import com.github.hectormips.pipeline

object RegFileWAddrSel extends ChiselEnum {
  val inst_rd : Type = Value(1.U)
  val inst_rt : Type = Value(2.U)
  val const_31: Type = Value(4.U)
}


class InsDecodeBundle extends Bundle {
  val ins_if_id              : UInt                 = Input(UInt(32.W))
  val pc_if_id               : UInt                 = Input(UInt(32.W))
  // 1: pc=pc+4
  // 2: pc=pc+(signed)(offset<<2)
  // 4: pc=pc[31:28]|instr_index<<2
  // 8: pc=regfile[1]
  val jump_sel               : Vec[Bool]            = Output(Vec(4, Bool()))
  val iram_en                : Bool                 = Output(Bool())
  val iram_we                : Bool                 = Output(Bool())
  val alu_src1_sel_id_ex     : AluSrc1Sel.Type      = Output(AluSrc1Sel())
  val alu_src2_sel_id_ex     : AluSrc2Sel.Type      = Output(AluSrc2Sel())
  val alu_op_id_ex           : AluOp.Type           = Output(AluOp())
  val dram_en_id_ex          : Bool                 = Output(Bool())
  val dram_we_id_ex          : Bool                 = Output(Bool())
  val regfile_we_id_ex       : Bool                 = Output(Bool())
  val regfile_waddr_sel_id_ex: RegFileWAddrSel.Type = Output(RegFileWAddrSel())
  val regfile_wsrc_id_ex     : Bool                 = Output(Bool())
  val ins_opcode             : UInt                 = Output(UInt(6.W))
  val ins_rs_id_regfile      : UInt                 = Output(UInt(5.W))
  val ins_rt_id_regfile      : UInt                 = Output(UInt(5.W))
  val ins_rd_id_regfile      : UInt                 = Output(UInt(5.W))
  val ins_sa_32_id_ex        : UInt                 = Output(UInt(32.W))
  val ins_imm_32_id_ex       : UInt                 = Output(UInt(32.W))
  val pc_id_ex               : UInt                 = Output(UInt(32.W))

  val decode_to_fetch_next_pc: Vec[UInt] = Output(Vec(2, UInt(32.W))) // 回馈给取值的pc通路
}

class InsDecode extends Module {
  val io       : InsDecodeBundle = IO(new InsDecodeBundle)
  val opcode   : UInt            = io.ins_if_id(31, 26)
  val sa       : UInt            = io.ins_if_id(10, 6)
  val imm      : SInt            = Wire(SInt(32.W))
  val func     : UInt            = io.ins_if_id(5, 0)
  val ins_addu : Bool            = opcode === 0.U && sa === 0.U && func === "b100001".U
  val ins_addiu: Bool            = opcode === "b001001".U
  val ins_subu : Bool            = opcode === 0.U && sa === 0.U && func === "b100011".U
  val ins_lw   : Bool            = opcode === "b100011".U
  val ins_sw   : Bool            = opcode === "b101011".U
  val ins_beq  : Bool            = opcode === "b000100".U
  val ins_bne  : Bool            = opcode === "b000101".U
  val ins_jal  : Bool            = opcode === "b000011".U
  val ins_jr   : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b001000".U
  val ins_slt  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b101010".U
  val ins_sltu : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b101011".U
  val ins_sll  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b000000".U
  val ins_srl  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b000010".U
  val ins_sra  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b000011".U
  val ins_lui  : Bool            = opcode === "b001111".U && sa === "b00000".U
  val ins_and  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100100".U
  val ins_or   : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100101".U
  val ins_xor  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100110".U
  val ins_nor  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100111".U

  imm := io.ins_if_id(15, 0).asSInt()
  io.jump_sel(0) := ins_addu | ins_addiu | ins_subu | ins_lw | ins_sw |
    ins_slt | ins_sltu | ins_sll | ins_srl | ins_sra | ins_lui | ins_and | ins_or |
    ins_xor | ins_nor
  io.jump_sel(1) := ins_beq | ins_bne
  io.jump_sel(2) := ins_jal
  io.jump_sel(3) := ins_jr

  io.iram_en := 1.B
  io.iram_we := 0.B

  io.alu_src1_sel_id_ex := Mux1H(Seq(
    (ins_addu | ins_addiu | ins_subu | ins_lw | ins_sw |
      ins_slt | ins_sltu | ins_and | ins_or | ins_xor | ins_nor) -> AluSrc1Sel.regfile_read1,
    ins_jal -> AluSrc1Sel.pc,
    (ins_sll | ins_srl | ins_sra) -> AluSrc1Sel.sa_32
  ))

  io.alu_src2_sel_id_ex := Mux1H(Seq(
    (ins_addu | ins_subu | ins_slt | ins_sltu | ins_sll | ins_srl |
      ins_sra | ins_and | ins_or | ins_xor | ins_nor) -> AluSrc2Sel.regfile_read2,
    (ins_addiu | ins_lw | ins_sw | ins_lui) -> AluSrc2Sel.imm_32,
    ins_jal -> AluSrc2Sel.const_31
  ))
  io.alu_op_id_ex := Mux1H(Seq(
    (ins_addu | ins_addiu | ins_lw | ins_sw | ins_jal) -> AluOp.op_add,
    ins_subu -> AluOp.op_sub,
    ins_slt -> AluOp.op_slt,
    ins_sltu -> AluOp.op_sltu,
    ins_and -> AluOp.op_and,
    ins_nor -> AluOp.op_nor,
    ins_or -> AluOp.op_or,
    ins_xor -> AluOp.op_xor,
    ins_sll -> AluOp.op_sll,
    ins_srl -> AluOp.op_srl,
    ins_sra -> AluOp.op_sra,
    ins_lui -> AluOp.op_lui
  ))
  io.regfile_we_id_ex := ins_addu | ins_addiu | ins_subu | ins_lw | ins_jal | ins_slt |
    ins_sltu | ins_sll | ins_srl | ins_sra | ins_lui | ins_and | ins_or | ins_xor | ins_nor
  io.regfile_waddr_sel_id_ex := Mux1H(Seq(
    (ins_addu | ins_subu | ins_slt | ins_sltu | ins_sll | ins_srl |
      ins_sra | ins_and | ins_or | ins_xor | ins_nor) -> RegFileWAddrSel.inst_rd,
    (ins_addiu | ins_lw | ins_lui) -> RegFileWAddrSel.inst_rt,
    ins_jal -> RegFileWAddrSel.const_31
  ))

  io.ins_opcode := opcode
  io.ins_rs_id_regfile := io.ins_if_id(25, 21)
  io.ins_rt_id_regfile := io.ins_if_id(20, 16)
  io.ins_rd_id_regfile := io.ins_if_id(15, 11)
  io.ins_sa_32_id_ex := sa
  io.ins_imm_32_id_ex := imm.asUInt()
  val instr_index: UInt = io.ins_if_id(25, 0)


  io.dram_en_id_ex := ins_lw | ins_sw
  io.dram_we_id_ex := ins_sw
  io.regfile_wsrc_id_ex := ins_lw

  // 使用sint进行有符号拓展
  val offset: SInt = Wire(SInt(32.W))
  offset := (io.ins_if_id(15, 0) << 2).asSInt()
  io.pc_id_ex := io.pc_if_id
  io.decode_to_fetch_next_pc(0) := io.pc_if_id + offset.asUInt()
  io.decode_to_fetch_next_pc(1) := Cat(Cat(io.pc_if_id(31, 28), instr_index), "b00".U)

}

object InsDecode extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new InsDecode())))
}