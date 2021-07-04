package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._

object MemorySrc extends ChiselEnum {
  val alu_val: Type = Value(1.U)
  val mem_addr: Type = Value(2.U)
}

class InsMemoryBundle extends Bundle {
  val mem_addr_src: Vec[UInt] = Input(Vec(2, UInt(32.W))) // 0: 来自于alu运算结果 1：来自于alu直接计算的mem地址
  val mem_en: Bool = Input(Bool())
  val mem_we: Bool = Input(Bool())
  val mem_addr_sel: MemorySrc.Type = Input(MemorySrc())

  val mem_out: UInt = Output(UInt(32.W))
}

class InsMemoryBack extends Module {
  val io: InsMemoryBundle = IO(new InsMemoryBundle)

}
