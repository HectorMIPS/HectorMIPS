package com.github.hectormips.tomasulo.ex_component

import chisel3._

class DCacheReadIO extends Bundle {
  val valid  : Bool = Input(Bool())
  val addr   : UInt = Input(UInt(32.W))
  val addr_ok: Bool = Output(Bool())
  val rdata  : UInt = Output(UInt(32.W))
  val data_ok: Bool = Output(Bool())
}
