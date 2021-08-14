package com.github.hectormips.cache.utils

import chisel3._
import chisel3.util._

class Wstrb extends Module {
  val io = IO(new Bundle{
    val size = Input(UInt(2.W))
    val offset = Input(UInt(2.W))
    val mask =  Output(UInt(4.W))
  })
  io.mask := "b0000".U
  when(io.size === 0.U){
    switch(io.offset){
      is(0.U){io.mask := "b0001".U(4.W)}
      is(1.U){io.mask := "b0010".U(4.W)}
      is(2.U){io.mask := "b0100".U(4.W)}
      is(3.U){io.mask := "b1000".U(4.W)}
    }
  }.elsewhen(io.size === 1.U){
    when(io.offset=== 0.U){
      io.mask := "b0011".U(4.W)
    }.elsewhen(io.offset===2.U){
      io.mask := "b1100".U(4.W)
    }
  }.elsewhen(io.size === 2.U){
    when(io.offset===0.U){io.mask := "b1111".U(4.W)}
  }
}

class SWL_SWR_Wstrb extends Module{
  val io = IO(new Bundle{
    val size = Input(UInt(3.W))
    val offset = Input(UInt(2.W))
    val wdata_old = Input(UInt(32.W))
    val is_small_endian = Input(Bool())
    val mask = Output(UInt(4.W))
    val wdata_new = Output(UInt(32.W))
  })
  //一共16种情况 可以合并成8种
  val is_swl = Wire(Bool())
  is_swl := io.size === 3.U
  when(is_swl && !io.is_small_endian || !is_swl &&io.is_small_endian){
    io.mask := MuxLookup(io.offset,"b0000".U(4.W),
      Array(
        0.U -> "b1111".U(4.W),
        1.U -> "b0111".U(4.W),
        2.U -> "b0011".U(4.W),
        3.U -> "b0001".U(4.W)
      ))
  }.otherwise{
    io.mask := MuxLookup(io.offset,"b0000".U(4.W),
      Array(
        0.U -> "b0001".U(4.W),
        1.U -> "b0011".U(4.W),
        2.U -> "b0111".U(4.W),
        3.U -> "b1111".U(4.W)
      ))
  }
  when(is_swl && !io.is_small_endian || !is_swl &&io.is_small_endian){
    io.wdata_new := MuxLookup(io.offset,"b0000".U(4.W),
      Array(
        0.U -> io.wdata_old,
        1.U -> Cat(0.U(8.W),io.wdata_old(31,8)),
        2.U -> Cat(0.U(16.W),io.wdata_old(31,16)),
        3.U -> Cat(0.U(24.W),io.wdata_old(31,24)),
      ))
  }.otherwise{
    io.wdata_new := MuxLookup(io.offset,"b0000".U(4.W),
      Array(
        0.U -> Cat(0.U(24.W),io.wdata_old(31,24)),
        1.U -> Cat(0.U(16.W),io.wdata_old(31,16)),
        2.U -> Cat(0.U(8.W),io.wdata_old(31,8)),
        3.U -> io.wdata_old,
      ))
  }



}