package com.github.hectormips.tomasulo.station

import chisel3._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.{ComponentIn, ComponentInOperationWidth}

class StationData(config: Config) extends Bundle {
  val busy: Bool = Bool()
  val ins: UInt = UInt(ComponentInOperationWidth.Width.W)
  // 第一个操作数
  val vj: UInt = UInt(32.W)
  // 第二个操作数
  val vk: UInt = UInt(32.W)
  // 第一个操作数等待的ROB
  val qj: UInt = UInt(config.rob_width.W)
  // 第二个操作数等待的ROB
  val qk: UInt = UInt(config.rob_width.W)
  // 第一个操作数是否等待ROB
  val wait_qj: Bool = Bool()
  // 第二个操作数是否等待ROB
  val wait_qk: Bool = Bool()
  // 要写入的ROB地址
  val dest: UInt = UInt(config.rob_width.W)
  // 要访问存储器的地址（只有Load和Store有用
  val A: UInt = UInt(32.W)
  // exception_Flag
  val exceptionFlag: UInt = UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W)

  def toComponent: ComponentIn = {
    val component_in = Wire(new ComponentIn(config))
    component_in.operation := ins
    component_in.A := A
    component_in.valA := vj
    component_in.valB := vk
    component_in.dest := dest
    component_in.exceptionFlag := exceptionFlag
    component_in
  }
}
