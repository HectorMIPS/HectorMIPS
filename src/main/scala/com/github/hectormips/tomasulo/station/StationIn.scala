package com.github.hectormips.tomasulo.station

import chisel3._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.ex_component.ComponentInOperationWidth

class StationIn(config:Config) extends Bundle{
  val operation: UInt = UInt(ComponentInOperationWidth.Width.W)
  val vj: UInt = UInt(32.W)
  val vk: UInt = UInt(32.W)
  val qj: UInt = UInt(config.rob_width.W)
  val qk: UInt = UInt(config.rob_width.W)
  val wait_qj: Bool = Bool()
  val wait_qk: Bool = Bool()
  val dest: UInt = UInt(config.rob_width.W)
  val A: UInt = UInt(config.rob_width.W)
}
