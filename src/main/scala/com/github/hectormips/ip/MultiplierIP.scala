package com.github.hectormips.ip

import chisel3._
import chisel3.util.experimental.forceName

class MultiplierIP extends BlackBox {
  override val desiredName = s"mult_gen_0"

  val pipeline_stages = 6

  class MultiplierIPIO extends Bundle {
    val a  : UInt = Input(UInt(33.W))
    val b  : UInt = Input(UInt(33.W))
    val clk: Bool = Input(Bool())
    val p  : UInt = Output(UInt(66.W))

    forceName(a, "A")
    forceName(b, "B")
    forceName(clk, "CLK")
    forceName(p, "P")
  }

  val io: MultiplierIPIO = IO(new MultiplierIPIO)


}
