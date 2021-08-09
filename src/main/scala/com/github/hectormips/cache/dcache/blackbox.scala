package com.github.hectormips.cache.dcache

import chisel3._
import chisel3.util._
import com.github.hectormips.cache.setting.CacheConfig

/**
 * 双端口ram
 */
class dcache_data_bank(config:CacheConfig) extends BlackBox {
  val io = IO(new Bundle {
    val addra = Input(UInt (config.indexWidth.W))
    val addrb = Input(UInt (config.indexWidth.W))
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
class dcache_tagv(config:CacheConfig) extends BlackBox {
  val io = IO(new Bundle {
    val addra = Input(UInt (config.indexWidth.W))
    val addrb = Input(UInt (config.indexWidth.W))
    val clka = Input(Clock())
    val clkb = Input(Clock())
    val dina = Input(UInt ((config.tagWidth+1).W))
    val dinb = Input(UInt ((config.tagWidth+1).W))
    val douta = Output(UInt ((config.tagWidth+1).W))
    val doutb = Output(UInt ((config.tagWidth+1).W))
    val ena = Input(Bool())
    val enb = Input(Bool())
    val wea = Input(Bool()) //默认置1
    val web = Input(Bool()) //默认置1
  })
}

class dcache_dirty(config:CacheConfig) extends BlackBox {
  val io = IO(new Bundle {
    val addra = Input(UInt (config.indexWidth.W))
    val addrb = Input(UInt (config.indexWidth.W))
    val clka = Input(Clock())
    val clkb = Input(Clock())
    val dina = Input(UInt (1.W))
    val dinb = Input(UInt (1.W))
    val douta = Output(UInt (1.W))
    val doutb = Output(UInt (1.W))
    val ena = Input(Bool())
    val enb = Input(Bool())
    val wea = Input(Bool()) //默认置1
    val web = Input(Bool()) //默认置1
  })
}
