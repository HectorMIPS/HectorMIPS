package com.github.hectormips.cache.access_judge

import com.github.hectormips.{AXIIOWithoutWid, SRamLikeDataIO, SRamLikeInstIO}
import chisel3._
import chisel3.util._



/**
 * 访存判断逻辑
 *
 * 分为3路
 * 一路作uncache 以后再写
 * 一路访问指令
 * 一路访问cached数据
 */
class MemAccessJudge extends Module{
  var io = IO(new Bundle{
      //输入
      val inst = Flipped(new SRamLikeInstIO())
      val data = Flipped(new SRamLikeDataIO())

      //输出
      val cached_inst   = new SRamLikeInstIO()
      val uncached_data = new SRamLikeDataIO() //单口AXI-IO
      val cached_data   = new SRamLikeDataIO()
  })

  //不管inst，直接和inst cache连在一起
  io.inst <> io.cached_inst

  val sIDLE::sReq::sWait::Nil = Enum(2)
  val state = RegInit(0.U(1.W))
  val should_cache_data = Wire(Bool())
  val should_cache_data_r = RegInit(false.B)
  when(io.data.addr>="h1faf_0000".U && io.data.data_ok <="h1aff_ffff".U){
    should_cache_data := false.B
  }.elsewhen(io.data.addr>="h8000_0000".U &&io.data.addr <="hbfff_ffff".U){
    should_cache_data := false.B
  }.otherwise{
    should_cache_data := true.B
  }
  switch(state){
    is(sIDLE){
      when(io.data.req){
        state := sReq
        should_cache_data_r := should_cache_data
        when(should_cache_data) {
          io.data <> io.cached_data
        }.otherwise{
          io.data <> io.uncached_data
        }
      }
    }
    is(sReq){
      when(should_cache_data_r) {
        io.data <> io.cached_data
      }.otherwise{
        io.data <> io.uncached_data
      }
      when(io.data.data_ok === true.B){
        state := sIDLE
      }.otherwise{
        state := sReq
      }
    }
  }




}
