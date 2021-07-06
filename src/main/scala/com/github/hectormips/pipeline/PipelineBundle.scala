package com.github.hectormips.pipeline

import chisel3._

class WithAllowin extends Bundle {
  val this_allowin: Bool = Output(Bool())
  val next_allowin: Bool = Input(Bool())
}

class WithValid extends Bundle {
  val bus_valid: Bool = Bool()
}
