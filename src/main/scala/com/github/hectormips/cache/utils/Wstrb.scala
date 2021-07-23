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