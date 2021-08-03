package com.github.hectormips.pipeline.issue

import chisel3._

class InstBundle extends Bundle {
  // 两条指令
  val inst            : UInt      = UInt(64.W)
  // 指令合法掩码
  val inst_valid      : UInt      = UInt(2.W)
  // 这对指令起始地址的pc
  val pc              : UInt      = UInt(32.W)
  val pred_jump_taken : Vec[Bool] = Vec(2, Bool())
  val pred_jump_target: Vec[UInt] = Vec(2, UInt(32.W))
}
