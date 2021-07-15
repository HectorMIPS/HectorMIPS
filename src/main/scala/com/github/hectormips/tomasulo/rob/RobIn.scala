package com.github.hectormips.tomasulo.rob

import chisel3._
import chisel3.util._
import com.github.hectormips.tomasulo.Config

class RobInsIn extends Bundle {
  val ins: UInt = UInt(32.W)
  val pc: UInt = UInt(32.W)
  val target: UInt = UInt(5.W)
}

class RobResultIn(config: Config) extends Bundle {
  val rob_target: UInt = UInt(config.rob_width.W)
  val value: UInt = UInt(32.W)
}
