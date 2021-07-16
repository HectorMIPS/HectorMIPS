package com.github.hectormips.tomasulo.io

import chisel3._

class InstFetcherIn extends Bundle {
  val jumpDest: UInt = Input(UInt(32.W))
  val jumpEn  : Bool = Input(Bool())
}
