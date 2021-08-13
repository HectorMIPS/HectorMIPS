package com.github.hectormips

import chisel3._

class SRamLikeIO(data_width: Int = 32) extends Bundle {
  // master -> slave
  val req  : Bool = Output(Bool())
  val wr   : Bool = Output(Bool())
  val size : UInt = Output(UInt(3.W))
  val addr : UInt = Output(UInt(32.W))
  val wdata: UInt = Output(UInt(data_width.W))
  val asid  : UInt = Output(UInt(8.W)) //进程号
  // slave -> master
  val addr_ok: Bool = Input(Bool())
  val data_ok: Bool = Input(Bool())
  val rdata  : UInt = Input(UInt(data_width.W))
  val ex   : UInt = Input(UInt(3.W)) //例外

}

class SRamLikeInstIO extends SRamLikeIO(64) {
  val inst_valid: UInt = Input(UInt(2.W))
  val inst_pc   : UInt = Input(UInt(32.W))

  val inst_predict_jump_out       : Vec[Bool] = Output(Vec(2, Bool()))
  val inst_predict_jump_target_out: Vec[UInt] = Output(Vec(2, UInt(32.W)))

  val inst_predict_jump_in       : Vec[Bool] = Input(Vec(2, Bool()))
  val inst_predict_jump_target_in: Vec[UInt] = Input(Vec(2, UInt(32.W)))
}

class SRamLikeDataIO extends SRamLikeIO(32) {
}