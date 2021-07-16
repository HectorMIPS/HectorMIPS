package com.github.hectormips

import chisel3._

class SRamLikeIO extends Bundle {
  val clk  : Bool = Output(Bool())
  // master -> slave
  val req  : Bool = Output(Bool())
  val wr   : Bool = Output(Bool())
  val size : UInt = Output(UInt(2.W))
  val addr : UInt = Output(UInt(32.W))
  val wdata: UInt = Output(UInt(32.W))

  // slave -> master
  val addr_ok: Bool = Input(Bool())
  val data_ok: Bool = Input(Bool())
  val rdata  : UInt = Input(UInt(32.W))
}
