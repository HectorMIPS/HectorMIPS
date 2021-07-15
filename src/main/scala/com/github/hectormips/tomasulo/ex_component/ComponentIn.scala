package com.github.hectormips.tomasulo.ex_component

import chisel3._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.operation.{AluOp, DividerOp, MemoryOp, MultiplierOp}


class ComponentIn(config: Config) extends Bundle {
  val operation    : UInt = UInt(List(AluOp.getWidth, DividerOp.getWidth,
    MultiplierOp.getWidth, MemoryOp.getWidth).max.W)
  val valA         : UInt = UInt(32.W)
  val valB         : UInt = UInt(32.W)
  val exceptionFlag: UInt = UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W)

  val dest: UInt = UInt(config.rob_width.W)
  val A   : UInt = UInt(32.W)
}