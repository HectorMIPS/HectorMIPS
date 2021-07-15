package com.github.hectormips.tomasulo.ex_component

import chisel3._
import com.github.hectormips.tomasulo.Config

class ComponentIn(config:Config) extends Bundle {
  val ins: UInt = UInt(32.W)
  val valA: UInt = UInt(32.W)
  val valB: UInt = UInt(32.W)

  val dest: UInt = UInt(config.rob_width.W)
  val A: UInt = UInt(32.W)
}