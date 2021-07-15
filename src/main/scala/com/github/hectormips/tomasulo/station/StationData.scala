package com.github.hectormips.tomasulo.station

import chisel3._
import com.github.hectormips.tomasulo.Config

class StationData(config:Config) extends Bundle {
  val busy: Bool = Bool()
  val ins: UInt = UInt(32.W)
  // 第一个操作数
  val vj: UInt = UInt(32.W)
  // 第二个操作数
  val vk: UInt = UInt(32.W)
  // 第一个操作数等待的ROB
  val qj: UInt = UInt(config.rob_width.W)
  // 第二个操作数等待的ROB
  val qk: UInt = UInt(config.rob_width.W)
  // 要写入的ROB地址
  val dest: UInt = UInt(config.rob_width.W)
  // 要访问存储器的地址（只有Load和Store有用
  val A: UInt = UInt(32.W)
}
