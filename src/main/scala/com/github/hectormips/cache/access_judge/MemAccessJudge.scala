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

  val sIDLE::sNop::sReqCache::sWaitCache::sReqAXI::sWaitAXI::sWaitInst::Nil = Enum(7)
  val state = RegInit(VecInit(Seq.fill(2)(0.U(3.W))))


  //  val inst_state = RegInit(0.U(3.W))
//  val data_state = RegInit(0.U(3.W))


  val should_cache_data = Wire(Vec(2,Bool()))

  val should_cache_data_r = RegInit(VecInit(Seq.fill(2)(false.B)))
  for(i<- 0 until 2) {
    when(io.data(i).addr >= "h1faf_0000".U && io.data(i).data_ok <= "h1aff_ffff".U) {
      should_cache_data(i) := false.B
    }.elsewhen(io.data(i).addr >= "h8000_0000".U && io.data(i).addr <= "hbfff_ffff".U) {
      should_cache_data(i) := false.B
    }.otherwise {
      should_cache_data(i) := true.B
    }
  }
//  should_cache_data := false.B

  for(i <- 0 until 2) {
    io.data(i).data_ok := false.B
    io.data(i).addr_ok := state(i) === sIDLE
    io.uncached_data(i).addr := 0.U
    io.cached_data(i).size := 2.U
    io.uncached_data(i).wr := 0.U
    io.cached_data(i).req := 0.U
    io.data(i).rdata := 0.U
    io.cached_data(i).wdata := 0.U
    io.uncached_data(i).wdata := 0.U
    io.uncached_data(i).size := 2.U
    io.uncached_data(i).req := 0.U
    io.cached_data(i).addr := 0.U
    io.cached_data(i).wr := 0.U
  }
  val wr_r = Reg(Vec(2,Bool()))
  val size_r =Reg(Vec(2,UInt(3.W)))
  val addr_r = Reg(Vec(2,UInt(32.W)))
  val wdata_r  =   RegInit(VecInit(Seq.fill(2)(0.U(32.W))))

  for(i <- 0 to 1) {
    when(should_cache_data_r(i)) {
      io.cached_data(i).wr := wr_r(i)
      io.cached_data(i).size := size_r(i)
      io.cached_data(i).addr := addr_r(i)
      io.cached_data(i).wdata := wdata_r(i)
    }.otherwise {
      io.uncached_data(i).wr := wr_r(i)
      io.uncached_data(i).size := size_r(i)
      io.uncached_data(i).addr := addr_r(i)
      io.uncached_data(i).wdata := wdata_r(i)
    }
  }


  for(i<- 0 to 1) {
    switch(state(i)) {
      is(sIDLE) {
        when(io.data(i).req) {
          should_cache_data_r(i) := should_cache_data(i)
          wdata_r(i) := io.data(i).wdata
          addr_r(i) := io.data(i).addr
          size_r(i) := io.data(i).size
          wr_r(i) := io.data(i).wr
          state(i) := sNop
        }
      }
      is(sNop) {
        when(should_cache_data_r(i)) {
          when(io.cached_data(i).addr_ok) {
            state(i) := sWaitCache
            io.cached_data(i).req := true.B
          }
        }.otherwise {
          when(io.uncached_data(i).addr_ok) {
            state(i) := sWaitAXI
            io.uncached_data(i).req := true.B
          }
        }
      }
      is(sWaitCache) {
        when(io.cached_data(i).data_ok) {
          state(i) := sIDLE
          when(!wr_r(i)) {
            //读
            io.data(i).rdata := io.cached_data(i).rdata
          }
          io.data(i).data_ok := true.B
        }.otherwise {
          io.cached_data(i).req := false.B
          state(i) := sWaitCache
        }
      }
      is(sWaitAXI) {
        when(io.uncached_data(i).data_ok) {
          state(i) := sIDLE
          when(!wr_r(i)) {
            //读
            io.data(i).rdata := io.uncached_data(i).rdata
          }
          io.data(i).data_ok := true.B
        }.otherwise {
          io.uncached_data(i).req := false.B
          state(i) := sWaitAXI
        }
      }
    }
  }




}
