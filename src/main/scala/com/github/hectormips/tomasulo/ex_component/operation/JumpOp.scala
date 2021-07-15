package com.github.hectormips.tomasulo.ex_component.operation

import chisel3.experimental.ChiselEnum

object JumpOp extends ChiselEnum {
  val eq, ne, ge, gt, le, lt, always= Value
}
