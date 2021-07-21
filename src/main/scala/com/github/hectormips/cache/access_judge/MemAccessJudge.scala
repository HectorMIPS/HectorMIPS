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

  val sIDLE::sNop::sReqCache::sWaitCache::sReqAXI::sWaitAXI::Nil = Enum(6)
  val state = RegInit(0.U(3.W))

  val should_cache_data = Wire(Bool())
  val should_cache_data_r = RegInit(false.B)
//  when(io.data.addr>="h1faf_0000".U && io.data.data_ok <="h1aff_ffff".U){
//    should_cache_data := false.B
//  }.elsewhen(io.data.addr>="h8000_0000".U &&io.data.addr <="hbfff_ffff".U){
//    should_cache_data := false.B
//  }.otherwise{
//    should_cache_data := true.B
//  }
  should_cache_data := false.B


  io.data.data_ok := false.B
  io.data.addr_ok := false.B
  io.uncached_data.addr := 0.U
  io.cached_data.size := 2.U
  io.uncached_data.wr := 0.U
  io.cached_data.req :=0.U
  io.data.rdata := 0.U
  io.cached_data.wdata := 0.U
  io.uncached_data.wdata := 0.U
  io.uncached_data.size := 2.U
  io.uncached_data.req :=0.U
  io.cached_data.addr := 0.U
  io.cached_data.wr := 0.U

  val wr_r = Reg(Bool())
  val size_r =Reg(UInt(3.W))
  val addr_r = Reg(UInt(32.W))
  val wdata_r  = RegInit(0.U(32.W))

  io.data.addr_ok := true.B

  switch(state){
    is(sIDLE){
      when(io.data.req){
        should_cache_data_r := should_cache_data
        wdata_r := io.data.wdata
        addr_r := io.data.addr
        size_r := io.data.size
        wr_r := io.data.wr
        state := sNop
      }
    }
    is(sNop){
      when(should_cache_data_r) {
        when(io.cached_data.addr_ok) {
          state := sWaitCache
          io.cached_data.req :=true.B
          io.data.addr_ok := false.B
        }
        io.cached_data.wr := wr_r
        io.cached_data.size := size_r
        io.cached_data.addr := addr_r
        io.cached_data.wdata := wdata_r
      }.otherwise{
        when(io.uncached_data.addr_ok) {
          state := sWaitAXI
          io.uncached_data.req :=true.B
          io.data.addr_ok := false.B
        }
        io.uncached_data.wr := wr_r
        io.uncached_data.size := size_r
        io.uncached_data.addr := addr_r
        io.uncached_data.wdata := wdata_r
      }
    }
    is(sWaitCache){
      when(io.cached_data.data_ok){
          state := sIDLE
          io.data.data_ok := true.B
      }.otherwise{
        io.cached_data.req :=false.B
        state := sWaitCache
      }
    }
    is(sWaitAXI){
      when(io.uncached_data.data_ok){
        state := sIDLE
        io.data.data_ok := true.B
      }.otherwise{
        io.uncached_data.req :=false.B
        state := sWaitAXI
      }

    }
  }




}
