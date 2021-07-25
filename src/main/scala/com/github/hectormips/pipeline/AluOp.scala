package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum

object AluOp extends OneHotEnum {
  val op_add   : Type = Value(1.U)
  val op_sub   : Type = Value(2.U)
  val op_slt   : Type = Value(4.U)
  val op_sltu  : Type = Value(8.U)
  val op_and   : Type = Value(16.U)
  val op_nor   : Type = Value(32.U)
  val op_or    : Type = Value(64.U)
  val op_xor   : Type = Value(128.U)
  val op_sll   : Type = Value(256.U)
  val op_srl   : Type = Value(512.U)
  val op_sra   : Type = Value(1024.U)
  val op_lui   : Type = Value(2048.U)
  val op_mult  : Type = Value(4096.U)
  val op_multu : Type = Value(8192.U)
  val op_div   : Type = Value(16384.U)
  val op_divu  : Type = Value(32768.U)
}
