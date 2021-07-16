package com.github.hectormips.tomasulo.io

import chisel3._

class DecoderIn extends Bundle {
  val inst: UInt = UInt(32.W)
  val pc  : UInt = UInt(32.W)
}