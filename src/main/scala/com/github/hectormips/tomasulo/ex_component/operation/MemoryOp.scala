package com.github.hectormips.tomasulo.ex_component.operation

import chisel3.experimental.ChiselEnum
import chisel3._

object MemoryOp extends ChiselEnum {
  val op_add: Type = Value(1.U)
}
