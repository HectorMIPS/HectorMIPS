package com.github.hectormips.cache.dcache

import chisel3._
import chisel3.util._

/**
 * 双端口ram
 */
class dcache_data_bank(lineNum:Int) extends BlackBox {
  val io = IO(new Bundle {
    val addra = Input(UInt (log2Ceil(lineNum).W))
    val addrb = Input(UInt (log2Ceil(lineNum).W))
    val clka = Input(Clock())
    val clkb = Input(Clock())
    val dina = Input(UInt (32.W))
    val dinb = Input(UInt (32.W))
    val douta = Output(UInt (32.W))
    val doutb = Output(UInt (32.W))
    val ena = Input(Bool())
    val enb = Input(Bool())
    val wea = Input(UInt(4.W))
    val web = Input(UInt(4.W))
  })
}

/**
 * 双端口ram
 */
class dcache_tag(tagWidth:Int,lineNum:Int) extends BlackBox {
  val io = IO(new Bundle {
    val addra = Input(UInt (log2Ceil(lineNum).W))
    val addrb = Input(UInt (log2Ceil(lineNum).W))
    val clka = Input(Clock())
    val clkb = Input(Clock())
    val dina = Input(UInt (tagWidth.W))
    val dinb = Input(UInt (tagWidth.W))
    val douta = Output(UInt (tagWidth.W))
    val doutb = Output(UInt (tagWidth.W))
    val ena = Input(Bool())
    val enb = Input(Bool())
    val wea = Input(Bool()) //默认置1
    val web = Input(Bool()) //默认置1
  })
}
