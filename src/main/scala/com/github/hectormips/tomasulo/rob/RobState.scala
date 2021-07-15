package com.github.hectormips.tomasulo.rob

import chisel3._
import chisel3.experimental.ChiselEnum

object RobState extends ChiselEnum {
  val confirm: Type = Value(1.U)
  val write: Type = Value(2.U)
  val process: Type = Value(4.U)
}
