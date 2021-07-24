package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum

class OneHotEnum extends ChiselEnum {
  val nop: Type = Value(0.U)
}
