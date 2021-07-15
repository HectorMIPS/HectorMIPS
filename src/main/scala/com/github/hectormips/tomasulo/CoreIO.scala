package com.github.hectormips.tomasulo

import chisel3._
import chisel3.util.DecoupledIO
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.ComponentInOperationWidth

class CoreIO(config: Config) extends Bundle {
  val clear : Bool = Output(Bool())
  val new_pc: UInt = Output(UInt(32.W))

  val in: DecoupledIO[CoreIn] = Flipped(DecoupledIO(new CoreIn(config)))

  val debug_wb_pc      : UInt = Output(UInt(32.W))
  val debug_wb_rf_wen  : UInt = Output(UInt(4.W))
  val debug_wb_rf_wnum : UInt = Output(UInt(5.W))
  val debug_wb_rf_wdata: UInt = Output(UInt(32.W))
}