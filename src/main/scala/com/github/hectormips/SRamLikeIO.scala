package com.github.hectormips

import chisel3._

class SRamLikeIO(rdata_width: Int = 32) extends Bundle {
  // master -> slave
  val req  : Bool = Output(Bool())
  val wr   : Bool = Output(Bool())
  val size : UInt = Output(UInt(3.W))
  val addr : UInt = Output(UInt(32.W))
  val wdata: UInt = Output(UInt(32.W))

  // slave -> master
  val addr_ok: Bool = Input(Bool())
  val data_ok: Bool = Input(Bool())
  val rdata  : UInt = Input(UInt(rdata_width.W))
}

class SRamLikeInstIO extends SRamLikeIO(64) {
  // 读指令有效mask
  val rdata_valid_mask: UInt = Input(UInt(2.W))
}

class SRamLikeDataIO extends SRamLikeIO {

}