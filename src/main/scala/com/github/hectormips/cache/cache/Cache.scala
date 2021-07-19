package com.github.hectormips.cache.cache

import chisel3._
import com.github.hectormips.cache.setting._
//import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}



//class Cache(val settings:CacheSetting)  extends Module{
//  val io = IO(new Bundle{
//    // 与CPU接口
//    val op = Input(Bool())
//    val index  = Input(UInt(settings.indexWidth.W))
//    val tag  = Input(UInt(settings.tagWidth.W))
//    val offset = Input(UInt(settings.offsetWidth.W))
//    val wstrb = Input(UInt(4.W)) //写字节使能
//    val wdata  = Input(UInt(32.W))
//    val addr_ok = Input(Bool())
//    val data_ok = Input(Bool())
//    val rdata = Input(UInt(32.W))
//  })
//
//}
