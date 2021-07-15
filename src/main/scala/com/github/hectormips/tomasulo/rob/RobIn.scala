package com.github.hectormips.tomasulo.rob

import chisel3._
import chisel3.util._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.ComponentInOperationWidth

class RobInsIn extends Bundle {
  val operation   : UInt = UInt(ComponentInOperationWidth.Width.W)
  val pc    : UInt = UInt(32.W)
  val target: UInt = UInt(5.W)
}

class RobResultIn(config: Config) extends Bundle {
  val rob_target   : UInt = UInt(config.rob_width.W)
  val exceptionFlag: UInt = UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W)
  val value        : UInt = UInt(64.W)

  // HILO 标志位
  val writeHILO : Bool = Bool()
  val readHI: Bool = Bool()
  val readLO: Bool = Bool()

  // 跳转相关
  val is_jump: Bool = Bool()
  val pred_success: Bool = Bool()
}
