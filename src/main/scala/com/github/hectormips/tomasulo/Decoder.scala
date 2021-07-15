package com.github.hectormips.tomasulo

import Chisel.{Decoupled, DecoupledIO}
import chisel3._

class Decoder(config: Config) extends Module {
  class DecoderIn extends Bundle {
    val inst: UInt = UInt(32.W)
    val pc  : UInt = UInt(32.W)
  }

  class DecoderIO extends Bundle {
    val in : DecoupledIO[DecoderIn] = Flipped(DecoupledIO(new DecoderIn))
    val out: DecoupledIO[CoreIO]    = Decoupled(new CoreIO(config))
  }

  val io: DecoderIO = IO(new DecoderIO)
  val opcode      : UInt            = io.if_id_in.ins_if_id(31, 26)
  val sa          : UInt            = io.if_id_in.ins_if_id(10, 6)
  val imm_signed  : SInt            = Wire(SInt(32.W))
  val imm_unsigned: UInt            = io.if_id_in.ins_if_id(15, 0)
  val func        : UInt            = io.if_id_in.ins_if_id(5, 0)
  val rs          : UInt            = io.if_id_in.ins_if_id(25, 21)
  val rt          : UInt            = io.if_id_in.ins_if_id(20, 16)
  val rd          : UInt            = io.if_id_in.ins_if_id(15, 11)
  val ins_addu    : Bool            = opcode === 0.U && sa === 0.U && func === "b100001".U
  val ins_add     : Bool            = opcode === 0x0.U && sa === 0x0.U && func === 0x20.U
  val ins_addiu   : Bool            = opcode === "b001001".U
  val ins_addi    : Bool            = opcode === 0x8.U
  val ins_subu    : Bool            = opcode === 0.U && sa === 0.U && func === "b100011".U
  val ins_sub     : Bool            = opcode === 0.U && sa === 0.U && func === 0x22.U
  val ins_lw      : Bool            = opcode === "b100011".U
  val ins_sw      : Bool            = opcode === "b101011".U
  val ins_beq     : Bool            = opcode === "b000100".U
  val ins_bne     : Bool            = opcode === "b000101".U
  val ins_jal     : Bool            = opcode === "b000011".U
  val ins_jr      : Bool            = opcode === "b000000".U && rt === "b00000".U && rd === "b00000".U &&
    sa === "b00000".U && func === "b001000".U
  val ins_slt     : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b101010".U
  val ins_sltu    : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b101011".U
  val ins_sll     : Bool            = opcode === "b000000".U && rs === "b00000".U && func === "b000000".U
  val ins_srl     : Bool            = opcode === "b000000".U && rs === "b00000".U && func === "b000010".U
  val ins_sra     : Bool            = opcode === "b000000".U && rs === "b00000".U && func === "b000011".U
  val ins_lui     : Bool            = opcode === "b001111".U && rs === "b00000".U
  val ins_and     : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100100".U
  val ins_or      : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100101".U
  val ins_xor     : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100110".U
  val ins_nor     : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100111".U
  val ins_slti    : Bool            = opcode === 0xa.U
  val ins_sltiu   : Bool            = opcode === 0xb.U
  val ins_andi    : Bool            = opcode === 0xc.U
  val ins_ori     : Bool            = opcode === 0xd.U
  val ins_xori    : Bool            = opcode === 0xe.U
  val ins_sllv    : Bool            = opcode === 0.U && sa === 0.U && func === 0x04.U
  val ins_srlv    : Bool            = opcode === 0.U && sa === 0.U && func === 0x6.U
  val ins_srav    : Bool            = opcode === 0.U && sa === 0.U && func === 0x7.U
  val ins_mult    : Bool            = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x18.U
  val ins_multu   : Bool            = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x19.U
  val ins_div     : Bool            = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x1a.U
  val ins_divu    : Bool            = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x1b.U
  val ins_mfhi    : Bool            = opcode === 0.U && rs === 0.U && rt === 0.U && sa === 0.U && func === 0x10.U
  val ins_mflo    : Bool            = opcode === 0.U && rs === 0.U && rt === 0.U && sa === 0.U && func === 0x12.U
  val ins_mthi    : Bool            = opcode === 0.U && rt === 0.U && rd === 0.U && sa === 0.U && func === 0x11.U
  val ins_mtlo    : Bool            = opcode === 0.U && rt === 0.U && rd === 0.U && sa === 0.U && func === 0x13.U
  val ins_bgez    : Bool            = opcode === 1.U && rt === 1.U
  val ins_bgtz    : Bool            = opcode === 7.U && rt === 0.U
  val ins_blez    : Bool            = opcode === 6.U && rt === 0.U
  val ins_bltz    : Bool            = opcode === 1.U && rt === 0.U
  val ins_j       : Bool            = opcode === 2.U
  val ins_bltzal  : Bool            = opcode === 1.U && rt === 0x10.U
  val ins_bgezal  : Bool            = opcode === 1.U && rt === 0x11.U
  val ins_jalr    : Bool            = opcode === 0.U && rt === 0.U && sa === 0.U && func === 0x9.U
  val ins_lb      : Bool            = opcode === 0x20.U
  val ins_lbu     : Bool            = opcode === 0x24.U
  val ins_lh      : Bool            = opcode === 0x21.U
  val ins_lhu     : Bool            = opcode === 0x25.U
  val ins_sb      : Bool            = opcode === 0x28.U
  val ins_sh      : Bool            = opcode === 0x29.U
  val ins_mfc0    : Bool            = opcode === 0x10.U && rs === 0.U && io.if_id_in.ins_if_id(10, 3) === 0.U
  val ins_mtc0    : Bool            = opcode === 0x10.U && rs === 0x04.U && io.if_id_in.ins_if_id(10, 3) === 0.U
  val ins_nop     : Bool            = io.if_id_in.ins_if_id === 0.U
  val ins_syscall : Bool            = opcode === 0.U && func === 0x0c.U
  val ins_break   : Bool            = opcode === 0.U && func === 0x0d.U
  val ins_eret    : Bool            = io.if_id_in.ins_if_id === 0x42000018.U
}
