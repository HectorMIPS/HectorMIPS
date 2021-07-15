package com.github.hectormips.pipeline

import chisel3._
import chisel3.stage.ChiselStage

class RegFileBundle extends Bundle {
  val raddr1: UInt = Input(UInt(5.W))
  val rdata1: UInt = Output(UInt(32.W))

  val raddr2: UInt = Input(UInt(5.W))
  val rdata2: UInt = Output(UInt(32.W))

  val we   : Bool = Input(Bool())
  val waddr: UInt = Input(UInt(5.W))
  val wdata: UInt = Input(UInt(32.W))

//  val regs_debug: Vec[UInt] = Output(Vec(32, UInt(32.W)))
}

// 寄存器堆文件
class RegFile(reg_init: Int) extends Module {
  val io  : RegFileBundle = IO(new RegFileBundle)
  val regs: Mem[UInt]      = Mem(32, UInt(32.W))

  regs(0) := reg_init.S.asUInt()
  when(io.we.asBool()) {
    when(io.waddr > 0.U) {
      regs(io.waddr) := io.wdata
    }
  }
  io.rdata1 := regs(io.raddr1)
  io.rdata2 := regs(io.raddr2)
}

object RegFile extends App {
  (new ChiselStage).emitVerilog(new RegFile(0))
}
