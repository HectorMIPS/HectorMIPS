package com.github.hectormips.tomasulo

import Chisel.{Decoupled, DecoupledIO, MuxCase}
import chisel3._
import chisel3.util._
import chisel3.util.Mux1H
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.operation.{AluOp, DividerOp, JumpOp, MemoryOp, MultiplierOp}
import com.github.hectormips.tomasulo.io.{CoreIn, DecoderIn}

class Decoder(config: Config) extends Module {


  class DecoderIO extends Bundle {
    val in : DecoupledIO[DecoderIn] = Flipped(DecoupledIO(new DecoderIn))
    val out: DecoupledIO[CoreIn]    = DecoupledIO(new CoreIn(config))
  }

  val io         : DecoderIO = IO(new DecoderIO)
  val inst       : UInt      = io.in.bits.inst
  val opcode     : UInt      = inst(31, 26)
  val sa         : UInt      = inst(10, 6)
  val imm        : UInt      = inst(15, 0)
  val func       : UInt      = inst(5, 0)
  val rs         : UInt      = inst(25, 21)
  val rt         : UInt      = inst(20, 16)
  val rd         : UInt      = inst(15, 11)
  val ins_addu   : Bool      = opcode === 0.U && sa === 0.U && func === "b100001".U
  val ins_add    : Bool      = opcode === 0x0.U && sa === 0x0.U && func === 0x20.U
  val ins_addiu  : Bool      = opcode === "b001001".U
  val ins_addi   : Bool      = opcode === 0x8.U
  val ins_subu   : Bool      = opcode === 0.U && sa === 0.U && func === "b100011".U
  val ins_sub    : Bool      = opcode === 0.U && sa === 0.U && func === 0x22.U
  val ins_lw     : Bool      = opcode === "b100011".U
  val ins_sw     : Bool      = opcode === "b101011".U
  val ins_beq    : Bool      = opcode === "b000100".U
  val ins_bne    : Bool      = opcode === "b000101".U
  val ins_jal    : Bool      = opcode === "b000011".U
  val ins_jr     : Bool      = opcode === "b000000".U && rt === "b00000".U && rd === "b00000".U &&
    sa === "b00000".U && func === "b001000".U
  val ins_slt    : Bool      = opcode === "b000000".U && sa === "b00000".U && func === "b101010".U
  val ins_sltu   : Bool      = opcode === "b000000".U && sa === "b00000".U && func === "b101011".U
  val ins_sll    : Bool      = opcode === "b000000".U && rs === "b00000".U && func === "b000000".U
  val ins_srl    : Bool      = opcode === "b000000".U && rs === "b00000".U && func === "b000010".U
  val ins_sra    : Bool      = opcode === "b000000".U && rs === "b00000".U && func === "b000011".U
  val ins_lui    : Bool      = opcode === "b001111".U && rs === "b00000".U
  val ins_and    : Bool      = opcode === "b000000".U && sa === "b00000".U && func === "b100100".U
  val ins_or     : Bool      = opcode === "b000000".U && sa === "b00000".U && func === "b100101".U
  val ins_xor    : Bool      = opcode === "b000000".U && sa === "b00000".U && func === "b100110".U
  val ins_nor    : Bool      = opcode === "b000000".U && sa === "b00000".U && func === "b100111".U
  val ins_slti   : Bool      = opcode === 0xa.U
  val ins_sltiu  : Bool      = opcode === 0xb.U
  val ins_andi   : Bool      = opcode === 0xc.U
  val ins_ori    : Bool      = opcode === 0xd.U
  val ins_xori   : Bool      = opcode === 0xe.U
  val ins_sllv   : Bool      = opcode === 0.U && sa === 0.U && func === 0x04.U
  val ins_srlv   : Bool      = opcode === 0.U && sa === 0.U && func === 0x6.U
  val ins_srav   : Bool      = opcode === 0.U && sa === 0.U && func === 0x7.U
  val ins_mult   : Bool      = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x18.U
  val ins_multu  : Bool      = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x19.U
  val ins_div    : Bool      = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x1a.U
  val ins_divu   : Bool      = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x1b.U
  val ins_mfhi   : Bool      = opcode === 0.U && rs === 0.U && rt === 0.U && sa === 0.U && func === 0x10.U
  val ins_mflo   : Bool      = opcode === 0.U && rs === 0.U && rt === 0.U && sa === 0.U && func === 0x12.U
  val ins_mthi   : Bool      = opcode === 0.U && rt === 0.U && rd === 0.U && sa === 0.U && func === 0x11.U
  val ins_mtlo   : Bool      = opcode === 0.U && rt === 0.U && rd === 0.U && sa === 0.U && func === 0x13.U
  val ins_bgez   : Bool      = opcode === 1.U && rt === 1.U
  val ins_bgtz   : Bool      = opcode === 7.U && rt === 0.U
  val ins_blez   : Bool      = opcode === 6.U && rt === 0.U
  val ins_bltz   : Bool      = opcode === 1.U && rt === 0.U
  val ins_j      : Bool      = opcode === 2.U
  val ins_bltzal : Bool      = opcode === 1.U && rt === 0x10.U
  val ins_bgezal : Bool      = opcode === 1.U && rt === 0x11.U
  val ins_jalr   : Bool      = opcode === 0.U && rt === 0.U && sa === 0.U && func === 0x9.U
  val ins_lb     : Bool      = opcode === 0x20.U
  val ins_lbu    : Bool      = opcode === 0x24.U
  val ins_lh     : Bool      = opcode === 0x21.U
  val ins_lhu    : Bool      = opcode === 0x25.U
  val ins_sb     : Bool      = opcode === 0x28.U
  val ins_sh     : Bool      = opcode === 0x29.U
  val ins_mfc0   : Bool      = opcode === 0x10.U && rs === 0.U && inst(10, 3) === 0.U
  val ins_mtc0   : Bool      = opcode === 0x10.U && rs === 0x04.U && inst(10, 3) === 0.U
  val ins_nop    : Bool      = inst === 0.U
  val ins_syscall: Bool      = opcode === 0.U && func === 0x0c.U
  val ins_break  : Bool      = opcode === 0.U && func === 0x0d.U
  val ins_eret   : Bool      = inst === 0x42000018.U

  val ins_valid: Bool = ins_addu ||
    ins_add ||
    ins_addiu ||
    ins_addi ||
    ins_subu ||
    ins_sub ||
    ins_lw ||
    ins_sw ||
    ins_beq ||
    ins_bne ||
    ins_jal ||
    ins_jr ||
    ins_slt ||
    ins_sltu ||
    ins_sll ||
    ins_srl ||
    ins_sra ||
    ins_lui ||
    ins_and ||
    ins_or ||
    ins_xor ||
    ins_nor ||
    ins_slti ||
    ins_sltiu ||
    ins_andi ||
    ins_ori ||
    ins_xori ||
    ins_sllv ||
    ins_srlv ||
    ins_srav ||
    ins_mult ||
    ins_multu ||
    ins_div ||
    ins_divu ||
    ins_mfhi ||
    ins_mflo ||
    ins_mthi ||
    ins_mtlo ||
    ins_bgez ||
    ins_bgtz ||
    ins_blez ||
    ins_bltz ||
    ins_j ||
    ins_bltzal ||
    ins_bgezal ||
    ins_jalr ||
    ins_lb ||
    ins_lbu ||
    ins_lh ||
    ins_lhu ||
    ins_sb ||
    ins_sh ||
    ins_mfc0 ||
    ins_mtc0 ||
    ins_nop ||
    ins_syscall ||
    ins_break ||
    ins_eret

  val operation: UInt = Wire(UInt(List(AluOp.getWidth, MemoryOp.getWidth, JumpOp.getWidth,
    MultiplierOp.getWidth, DividerOp.getWidth).max.W))
  operation := Mux1H(Seq(
    (ins_addu | ins_add | ins_addiu | ins_addi |
      ins_jal | ins_bltzal | ins_bgezal | ins_jalr | ins_mtc0) -> AluOp.op_add.asUInt(),
    (ins_subu | ins_sub) -> AluOp.op_sub.asUInt(),
    (ins_slt | ins_slti) -> AluOp.op_slt.asUInt(),
    (ins_sltu | ins_sltiu) -> AluOp.op_sltu.asUInt(),
    (ins_and | ins_andi) -> AluOp.op_and.asUInt(),
    ins_nor -> AluOp.op_nor.asUInt(),
    (ins_or | ins_ori) -> AluOp.op_or.asUInt(),
    (ins_xor | ins_xori) -> AluOp.op_xor.asUInt(),
    (ins_sll | ins_sllv) -> AluOp.op_sll.asUInt(),
    (ins_srl | ins_srlv) -> AluOp.op_srl.asUInt(),
    (ins_sra | ins_srav) -> AluOp.op_sra.asUInt(),
    ins_lui -> AluOp.op_lui.asUInt(),
    ins_mult -> MultiplierOp.mult.asUInt(),
    ins_multu -> MultiplierOp.multu.asUInt(),
    ins_div -> DividerOp.div.asUInt(),
    ins_divu -> DividerOp.divu.asUInt(),
    ins_lw -> MemoryOp.op_word.asUInt(),
    ins_lh -> MemoryOp.op_hword_signed.asUInt(),
    ins_lhu -> MemoryOp.op_hword_unsigned.asUInt(),
    ins_lb -> MemoryOp.op_byte_signed.asUInt(),
    ins_lbu -> MemoryOp.op_byte_unsigned.asUInt(),
    ins_sw -> MemoryOp.op_sw.asUInt(),
    ins_sh -> MemoryOp.op_sh.asUInt(),
    ins_sb -> MemoryOp.op_sb.asUInt()
  ))
  // TODO: station target



  io.out.bits.operation := operation
  io.out.bits.exception_flag := Mux(io.in.bits.pc(1, 0) === 0.U, 0.U, ExceptionConst.EXCEPTION_FETCH_ADDR) |
    Mux(ins_valid, 0.U, ExceptionConst.EXCEPTION_RESERVE_INST)
  io.out.bits.pc := io.in.bits.pc
  io.out.bits.srcA := rs
  io.out.bits.srcB := rt
  io.out.bits.need_valA := ins_addu | ins_add | ins_addiu | ins_addi | ins_subu | ins_sub | ins_lw | ins_lb |
    ins_lbu | ins_lh | ins_lhu | ins_sw | ins_sh | ins_sb |
    ins_slt | ins_sltu | ins_and | ins_or | ins_xor | ins_nor | ins_sltu | ins_slti | ins_sltiu |
    ins_andi | ins_ori | ins_xori | ins_sllv | ins_srlv | ins_srav | ins_multu |
    ins_mult | ins_div | ins_divu | ins_mthi | ins_mtlo
  io.out.bits.need_valB := ins_addu | ins_add | ins_subu | ins_sub | ins_slt | ins_sltu | ins_sll | ins_srl |
    ins_sra | ins_and | ins_or | ins_xor | ins_nor | ins_sllv | ins_srlv | ins_srav |
    ins_mult | ins_multu | ins_div | ins_divu | ins_mtc0
  io.out.bits.valA := MuxCase(0.U, Seq(
    (ins_jal | ins_bgezal | ins_bltzal | ins_jalr) -> io.in.bits.pc,
    (ins_sll | ins_srl | ins_sra | ins_mtc0) -> sa
  ))
  io.out.bits.valB := MuxCase(0.U, Seq(
    (ins_addiu | ins_addi | ins_lw | ins_lb |
      ins_lbu | ins_lh | ins_lhu | ins_sw | ins_sh | ins_sb | ins_lui | ins_slti |
      ins_sltiu) -> Cat(VecInit(Seq.fill(16)(imm(15))).asUInt(), imm),
    (ins_andi | ins_ori | ins_xori) -> imm,
    (ins_jal | ins_bgezal | ins_bltzal | ins_jalr) -> 8.U
  ))
  val offset_shifted: UInt = (imm << 2).asUInt()
  val b_target      : UInt = io.in.bits.pc + Cat(VecInit(Seq.fill(14)(offset_shifted(17))).asUInt(), offset_shifted)
  io.out.bits.target_pc := MuxCase(io.in.bits.pc + 4.U, Seq(
    (ins_beq | ins_bne | ins_bgez | ins_blez | ins_bgezal | ins_bltzal) -> b_target,
    (ins_j | ins_jal) -> Cat(io.in.bits.pc(31, 28), inst(25, 0))
  ))
  io.out.bits.dest := MuxCase(0.U, Seq(
    (ins_addu | ins_add | ins_subu | ins_sub | ins_slt | ins_sltu | ins_sll | ins_srl |
      ins_sra | ins_and | ins_or | ins_xor | ins_nor | ins_sllv | ins_srlv |
      ins_srav | ins_mfhi | ins_mflo | ins_jalr) -> rd,
    (ins_addiu | ins_addi | ins_lw | ins_lb |
      ins_lbu | ins_lh | ins_lhu | ins_lui | ins_slti | ins_sltiu | ins_andi | ins_ori |
      ins_xori | ins_mfc0) -> rt,
    (ins_jal | ins_bgezal | ins_bltzal) -> 31.U
  ))

  io.out.bits.writeHI := ins_multu | ins_mult | ins_div | ins_divu | ins_mthi
  io.out.bits.writeLO := ins_multu | ins_mult | ins_div | ins_divu | ins_mtlo
  io.out.bits.readHI := ins_mfhi
  io.out.bits.readLO := ins_mflo
}
