package com.github.hectormips.tomasulo.station

import chisel3._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.ex_component.ComponentInOperationWidth

class StationIn(config: Config) extends Bundle {
  val operation: UInt = UInt(ComponentInOperationWidth.Width.W)
  val vj: UInt = UInt(32.W)
  val vk: UInt = UInt(32.W)
  val qj: UInt = UInt(config.rob_width.W)
  val qk: UInt = UInt(config.rob_width.W)
  val wait_qj: Bool = Bool()
  val wait_qk: Bool = Bool()
  val dest: UInt = UInt(config.rob_width.W)

  // 当前pc值
  val pc: UInt = UInt(32.W)
  // 分支指令目的pc值
  val target_pc: UInt = UInt(32.W)

  // HILO 标志位
  val writeHI: Bool = Bool()
  val writeLO: Bool = Bool()
  val readHI: Bool = Bool()
  val readLO: Bool = Bool()

  val predictJump   : Bool = Bool()
}
