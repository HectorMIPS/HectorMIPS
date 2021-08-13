package com.github.hectormips.pipeline

import Chisel.Cat
import chisel3._
import chisel3.stage.ChiselStage

// 寄存器堆改造成可变口数
class RegFileBundle(read_port_num: Int = 2, write_port_num: Int = 2) extends Bundle {
  val raddr1: Vec[UInt] = Input(Vec(read_port_num, UInt(5.W)))
  val rdata1: Vec[UInt] = Output(Vec(read_port_num, UInt(32.W)))

  val raddr2: Vec[UInt] = Input(Vec(read_port_num, UInt(5.W)))
  val rdata2: Vec[UInt] = Output(Vec(read_port_num, UInt(32.W)))

  val we   : Vec[UInt] = Input(Vec(write_port_num, UInt()))
  val waddr: Vec[UInt] = Input(Vec(write_port_num, UInt(5.W)))
  val wdata: Vec[UInt] = Input(Vec(write_port_num, UInt(32.W)))

}

// 寄存器堆文件
class RegFile(reg_init: Int = 0, read_port_num: Int = 2, write_port_num: Int = 2) extends Module {
  val io  : RegFileBundle = IO(new RegFileBundle)
  val regs: Vec[UInt]     = RegInit(
    VecInit(Seq.fill(32)(reg_init.U(32.W)))
  )
  regs(0) := reg_init.S.asUInt()

  def bitEnToByteEn(en: UInt): UInt = {
    def byteEnable(index: Int): UInt = {
      VecInit(Seq.fill(8)(en(index))).asUInt()
    }

    Cat(byteEnable(3), byteEnable(2),
      byteEnable(1), byteEnable(0))
  }
  // 因为访存指令都是单发射，不需要考虑同时有对齐和非对齐写入的情况
  when(io.we(0) =/= 0.U && io.we(1) =/= 0.U && io.waddr(0) === io.waddr(1)) {
    when(io.waddr(0) > 0.U) {
      regs(io.waddr(1)) := (io.wdata(1) & bitEnToByteEn(io.we(1))) |
        (regs(io.waddr(1)) & (~bitEnToByteEn(io.we(1))).asUInt())
    }
  }.otherwise {
    for (i <- 0 until write_port_num) {
      when(io.we(i) =/= 0.U) {
        when(io.waddr(i) > 0.U) {
          regs(io.waddr(i)) := (io.wdata(i) & bitEnToByteEn(io.we(i))) |
            (regs(io.waddr(i)) & (~bitEnToByteEn(io.we(i))).asUInt())
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
