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
    val data = Vec(2,Flipped(new SRamLikeDataIO()))

    //输出
    val cached_inst   = new SRamLikeInstIO()
    val uncached_data = Vec(2,new SRamLikeDataIO()) //单口AXI-IO
    val cached_data   = Vec(2,new SRamLikeDataIO())
  })

  //不管inst，直接和inst cache连在一起

  io.inst <> io.cached_inst

//  val sIDLE::sNop::sReqCache::sWaitCache::sReqAXI::sWaitAXI::sWaitInst::Nil = Enum(7)
//  val state = RegInit(VecInit(Seq.fill(2)(0.U(3.W))))


  //  val inst_state = RegInit(0.U(3.W))
  //  val data_state = RegInit(0.U(3.W))
  val should_cache_data_r   = RegInit(VecInit(Seq.fill(2)(false.B)))
  val should_cache_data_c = Wire(Vec(2,Bool()))
  val should_cache_data  = Wire(Vec(2,Bool()))


//  val should_cache_data_r = RegInit(VecInit(Seq.fill(2)(false.B)))
  for(i<- 0 until 2) {
    should_cache_data(i) := Mux(io.data(i).req,should_cache_data_c(i),should_cache_data_r(i))
    when(io.data(i).req){
      should_cache_data_r(i) := should_cache_data_c(i)
    }

    when(io.data(i).addr >= "h1faf_0000".U && io.data(i).addr <= "h1faf_ffff".U) {
      should_cache_data_c(i) := false.B
    }.elsewhen(io.data(i).addr >= "h8000_0000".U && io.data(i).addr <= "hbfff_ffff".U) {
      should_cache_data_c(i) := false.B
    }.otherwise {
      should_cache_data_c(i) := true.B
    }
  }
  //  should_cache_data := false.B

  for(i <- 0 until 2) {
    io.cached_data(i).req := should_cache_data(i) & io.data(i).req
    io.uncached_data(i).req := !should_cache_data(i) & io.data(i).req
    io.cached_data(i).size := io.data(i).size
    io.cached_data(i).addr := io.data(i).addr
    io.cached_data(i).wr := io.data(i).wr
    io.cached_data(i).wdata := io.data(i).wdata

    io.uncached_data(i).size := io.data(i).size
    io.uncached_data(i).addr := io.data(i).addr
    io.uncached_data(i).wr := io.data(i).wr
    io.uncached_data(i).wdata := io.data(i).wdata

    io.data(i).addr_ok := Mux(should_cache_data(i),io.cached_data(i).addr_ok,io.uncached_data(i).addr_ok)
    io.data(i).data_ok := Mux(should_cache_data(i),io.cached_data(i).data_ok,io.uncached_data(i).data_ok)
    io.data(i).rdata := Mux(should_cache_data(i),io.cached_data(i).rdata,io.uncached_data(i).rdata)
  }


}
