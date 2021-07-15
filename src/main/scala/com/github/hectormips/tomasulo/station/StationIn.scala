package com.github.hectormips.tomasulo.station

import chisel3._
import com.github.hectormips.tomasulo.Config

class StationIn(config:Config) extends Bundle{
  val ins: UInt = UInt(32.W)
  val vj: UInt = UInt(32.W)
  val vk: UInt = UInt(32.W)
  val qj: UInt = UInt(config.rob_width.W)
  val qk: UInt = UInt(config.rob_width.W)
  val dest: UInt = UInt(config.rob_width.W)
  val A: UInt = UInt(config.rob_width.W)
}
