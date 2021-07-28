package com.github.hectormips.pipeline

import chisel3._
import chisel3.stage.ChiselStage

// 寄存器堆改造成可变口数
class RegFileBundle(read_port_num: Int = 2, write_port_num: Int = 2) extends Bundle {
  val raddr1: Vec[UInt] = Input(Vec(read_port_num, UInt(5.W)))
  val rdata1: Vec[UInt] = Output(Vec(read_port_num, UInt(32.W)))

  val raddr2: Vec[UInt] = Input(Vec(read_port_num, UInt(5.W)))
  val rdata2: Vec[UInt] = Output(Vec(read_port_num, UInt(32.W)))

  val we   : Vec[Bool] = Input(Vec(write_port_num, Bool()))
  val waddr: Vec[UInt] = Input(Vec(write_port_num, UInt(5.W)))
  val wdata: Vec[UInt] = Input(Vec(write_port_num, UInt(32.W)))

}

// 寄存器堆文件
class RegFile(reg_init: Int = 0, read_port_num: Int = 2, write_port_num: Int = 2) extends Module {
  val io  : RegFileBundle = IO(new RegFileBundle)
  val regs: Vec[UInt]     = RegInit(
    VecInit(Seq.fill(32)(5.U(32.W)))
  )
  regs(0) := reg_init.S.asUInt()
  when(io.we(0).asBool() && io.we(1).asBool() && io.waddr(0) === io.waddr(1)) {
    when(io.waddr(0) > 0.U) {
      regs(io.waddr(1)) := io.wdata(1)
    }
  }.otherwise {
    for (i <- 0 until write_port_num) {
      when(io.we(i).asBool()) {
        when(io.waddr(i) > 0.U) {
          regs(io.waddr(i)) := io.wdata(i)
        }
      }
    }
  }
  for (i <- 0 until read_port_num) {
    io.rdata1(i) := regs(io.raddr1(i))
    io.rdata2(i) := regs(io.raddr2(i))
  }
}

object RegFile extends App {
  (new ChiselStage).emitVerilog(new RegFile(0))
}
