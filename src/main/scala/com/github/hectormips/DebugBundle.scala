package com.github.hectormips

import chisel3._
import chisel3.util.experimental.forceName

class DebugBundle extends Bundle {
  val debug_wb_pc      : UInt = Output(UInt(32.W))
  val debug_wb_rf_wen  : UInt = Output(UInt(4.W))
  val debug_wb_rf_wnum : UInt = Output(UInt(5.W))
  val debug_wb_rf_wdata: UInt = Output(UInt(32.W))

  forceName(debug_wb_pc, "debug_wb_pc")
  forceName(debug_wb_rf_wen, "debug_wb_rf_wen")
  forceName(debug_wb_rf_wnum, "debug_wb_rf_wnum")
  forceName(debug_wb_rf_wdata, "debug_wb_rf_wdata")
}
