package com.github.hectormips.pipeline

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile

class SyncRamBundle extends Bundle {
  val ram_wen  : Bool = Input(Bool())
  val ram_en   : Bool = Input(Bool())
  val ram_addr : UInt = Input(UInt(32.W))
  val ram_wdata: UInt = Input(UInt(32.W))
  val ram_rdata: UInt = Output(UInt(32.W))
}

// 数据大小为_depth_字节
class SyncRam(depth: Long) extends Module {
  val io : SyncRamBundle     = IO(new SyncRamBundle)
  val ram: SyncReadMem[UInt] = SyncReadMem(depth, UInt(8.W)) // 使用寄存器组来模拟ram
  io.ram_rdata := DontCare
  when(io.ram_en) {
    when(io.ram_wen) {
      for (i <- 0 until 4)
        ram(io.ram_addr + i.U) := io.ram_wdata(i * 8 + 7, i * 8)
    }.otherwise {
      io.ram_rdata := Cat(Seq(
        ram(io.ram_addr + 3.U),
        ram(io.ram_addr + 2.U),
        ram(io.ram_addr + 1.U),
        ram(io.ram_addr + 0.U)
      ))
    }
  }
}
