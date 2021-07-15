package com.github.hectormips.tomasulo.ex_component.operation

import chisel3.experimental.ChiselEnum
import chisel3._

object MemoryOp extends ChiselEnum {
  val op_byte_signed   : Type = Value(0x1.U)
  val op_byte_unsigned : Type = Value(0x2.U)
  val op_hword_signed  : Type = Value(0x4.U)
  val op_hword_unsigned: Type = Value(0x8.U)
  val op_word          : Type = Value(0x10.U)

}
