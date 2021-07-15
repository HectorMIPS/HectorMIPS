package com.github.hectormips.tomasulo.ex_component.operation

import chisel3._
import chisel3.experimental.ChiselEnum

object MultiplierOp extends ChiselEnum {
  val multu: Type = Value(1.U)
  val mult : Type = Value(2.U)
}
