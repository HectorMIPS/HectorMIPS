package com.github.hectormips.tomasulo.ex_component.operation

import chisel3._
import chisel3.experimental.ChiselEnum

object DividerOp extends ChiselEnum {
  val divu: Type = Value(1.U)
  val div : Type = Value(2.U)
}
