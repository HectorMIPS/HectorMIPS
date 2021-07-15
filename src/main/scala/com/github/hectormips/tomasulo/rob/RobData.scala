package com.github.hectormips.tomasulo.rob

import chisel3._

class RobData extends Bundle {
  val busy: Bool = Bool()
  val ins: UInt = UInt(32.W)
  val pc: UInt = UInt(32.W)
  val state: RobState.Type = RobState()
  val target: UInt = UInt(5.W)
  val value: UInt = UInt(64.W)

  // HILO 标志位
  val writeHILO : Bool = Bool()
  val readHI: Bool = Bool()
  val readLO: Bool = Bool()
}

