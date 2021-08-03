package com.github.hectormips

import chisel3._
import chisel3.util.experimental.forceName

class DebugBundle extends Bundle {
  val debug_wb_pc      : UInt = Output(UInt(64.W))
  val debug_wb_rf_wen  : UInt = Output(UInt(8.W))
  val debug_wb_rf_wnum : UInt = Output(UInt(10.W))
  val debug_wb_rf_wdata: UInt = Output(UInt(64.W))
  val debug_flush      : Bool = Output(Bool())

}
