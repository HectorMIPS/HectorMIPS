package com.github.hectormips.cache.dcache

import chisel3._
import chisel3.util._
import com.github.hectormips.amba.{AXIAddr, AXIWriteData, AXIWriteResponse}
import com.github.hectormips.cache.setting.CacheConfig


class InvalidateQueue(config:CacheConfig) extends Module {
  /**
   * 暂时使用的接口
   */
  val wdata = Input(Vec(config.bankNum,UInt(32.W)))
  val addr  = Input(UInt(32.W))
  val req   = Input(Bool())
  val addr_ok = Output(Bool())
  val data_ok    = Output(Bool())
  val io = IO(new Bundle{
    val writeAddr  =  Decoupled(new AXIAddr(32,4))
    val writeData  = Decoupled(new AXIWriteData(32,4))
    val writeResp =  Flipped(Decoupled( new AXIWriteResponse(4)))
  })
  io.writeAddr := DontCare
  io.writeData := DontCare
  io.writeResp := DontCare
}
