package com.github.hectormips

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

// 寄存器堆文件
class RegFile extends Module {
  val io: Bundle {
    val raddr1: UInt
    val rdata1: UInt
    val raddr2: UInt
    val rdata2: UInt
    val we: Bool
    val waddr: UInt
    val wdata: UInt
  } = IO(new Bundle {
    val raddr1: UInt = Input(UInt(5.W))
    val rdata1: UInt = Output(UInt(32.W))

    val raddr2: UInt = Input(UInt(5.W))
    val rdata2: UInt = Output(UInt(32.W))

    val we: Bool = Input(Bool())
    val waddr: UInt = Input(UInt(5.W))
    val wdata: UInt = Input(UInt(32.W))
  })
  val regs: Vec[UInt] = Reg(Vec(32, UInt(32.W)))
  regs(0) := 0.U
  when(io.we.asBool() && io.waddr > 0.U) {
    regs(io.waddr) := io.wdata
  }
  io.rdata1 := regs(io.raddr1)
  io.rdata2 := regs(io.raddr2)

}

object RegFile extends App {
  (new ChiselStage).emitVerilog(new RegFile)
}
