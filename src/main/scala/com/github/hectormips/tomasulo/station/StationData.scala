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
  // exception_Flag
  val exceptionFlag: UInt = UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W)

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


  def toComponent: ComponentIn = {
    val component_in = Wire(new ComponentIn(config))
    component_in.operation := ins
    component_in.valA := vj
    component_in.valB := vk
    component_in.exceptionFlag := exceptionFlag
    component_in.predictJump := predictJump

    component_in.pc := pc
    component_in.target_pc := target_pc

    component_in.writeLO  := writeLO
    component_in.writeHI  := writeHI
    component_in.readHI  := readHI
    component_in.readLO  := readLO
    component_in.dest := dest
    component_in
  }
}
