package com.github.hectormips

import chisel3._

class SRamLikeIO(data_width: Int = 32) extends Bundle {
  // master -> slave
  val req  : Bool = Output(Bool())
  val wr   : Bool = Output(Bool())
  val size : UInt = Output(UInt(2.W))
  val addr : UInt = Output(UInt(32.W))
  val wdata: UInt = Output(UInt(data_width.W))

  // slave -> master
  val addr_ok: Bool = Input(Bool())
  val data_ok: Bool = Input(Bool())
  val rdata  : UInt = Input(UInt(32.W))
}

class SRamLikeInstIO extends SRamLikeIO(64) {
  val inst_valid: UInt = UInt(2.W)
}

class SRamLikeDataIO extends SRamLikeIO(32) {
}