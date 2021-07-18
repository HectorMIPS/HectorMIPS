package com.github.hectormips.tomasulo.rob

import chisel3._

class RobData extends Bundle {
  val ins: UInt = UInt(32.W)
  val pc: UInt = UInt(32.W)
  val state: RobState.Type = RobState()
  val target: UInt = UInt(5.W)
  val value: UInt = UInt(64.W)

  // HILO 标志位
  val writeHILO : Bool = Bool()
  val readHI: Bool = Bool()
  val readLO: Bool = Bool()
  val writeLO: Bool = Bool()
  val writeHI: Bool = Bool()

  // 跳转相关
  val is_jump: Bool = Bool()
  val jump_success: Bool = Bool()
  val pred_success: Bool = Bool()
  val next_pc : UInt = UInt(32.W)
}

