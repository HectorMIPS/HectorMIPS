package com.github.hectormips.cache.access_judge

import com.github.hectormips.{AXIIOWithoutWid, SRamLikeDataIO, SRamLikeInstIO}
import chisel3._
import chisel3.util._
import com.github.hectormips.cache.uncache.UncacheInst




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
    val uncached_inst = new SRamLikeInstIO()
    val uncached_data = Vec(2,new SRamLikeDataIO()) //单口AXI-IO
    val cached_data   = Vec(2,new SRamLikeDataIO())
  })
  val should_cache_inst = Wire(Bool())
  val should_cache_data = Wire(Vec(2,Bool()))
  val data_physical_addr = Wire(Vec(2,UInt(32.W)))
  val inst_physical_addr = Wire(UInt(32.W))




//  def addr_mapping(vaddr: UInt): UInt = {
//    val physical_addr: UInt = Wire(UInt(32.W))
//    physical_addr := Mux((vaddr >= 0x80000000L.U && vaddr <= 0x9fffffffL.U) ||
//      (vaddr >= 0xa0000000L.U && vaddr <= 0xbfffffffL.U),
//      vaddr & 0x1fffffff.U, vaddr)
//    physical_addr
//  }


//  val should_cache_data_r = RegInit(VecInit(Seq.fill(2)(false.B)))
  // uncache的地址范围： 0xa000_0000 -- 0xbfff_ffff
  //
  for(i<- 0 until 2) {
    /**
     * cache 属性确认
     */

    when(io.data(i).addr >= "ha000_0000".U && io.data(i).addr <= "hbfff_ffff".U){
      should_cache_data(i) := false.B
    }.otherwise {
      should_cache_data(i) := true.B
    }

    /**
     * 虚地址转换
     */
    when(io.data(i).addr >= "h8000_0000".U && io.data(i).addr <= "hbfff_ffff".U){
      data_physical_addr(i) := io.data(i).addr & "h1fff_ffff".U
    }.otherwise{
      data_physical_addr(i) := io.data(i).addr
    }
  }
  when(io.inst.addr >= "ha000_0000".U && io.inst.addr <= "hbfff_ffff".U){
    should_cache_inst := false.B
  }.otherwise {
    should_cache_inst := true.B
  }
  when(io.inst.addr >= "h8000_0000".U && io.inst.addr <= "hbfff_ffff".U){
    inst_physical_addr := io.inst.addr & "h1fff_ffff".U
  }.otherwise{
    inst_physical_addr := io.inst.addr
  }

//  io.inst.addr :=

  //  should_cache_data := false.B

  for(i <- 0 until 2) {
    io.cached_data(i).req := should_cache_data(i) & io.data(i).req
    io.uncached_data(i).req := !should_cache_data(i) & io.data(i).req
    io.cached_data(i).size := io.data(i).size
    io.cached_data(i).addr := io.data(i).addr
    io.cached_data(i).wr := io.data(i).wr
    io.cached_data(i).wdata := io.data(i).wdata

    io.uncached_data(i).size := io.data(i).size
    io.uncached_data(i).addr := data_physical_addr(i)
    io.uncached_data(i).wr := io.data(i).wr
    io.uncached_data(i).wdata := io.data(i).wdata

    io.data(i).addr_ok := Mux(should_cache_data(i),io.cached_data(i).addr_ok,io.uncached_data(i).addr_ok)
    io.data(i).data_ok := Mux(should_cache_data(i),io.cached_data(i).data_ok,io.uncached_data(i).data_ok)
    io.data(i).rdata := Mux(should_cache_data(i),io.cached_data(i).rdata,io.uncached_data(i).rdata)
  }
  io.cached_inst.req := should_cache_inst && io.inst.req
  io.cached_inst.wr  := io.inst.wr
  io.cached_inst.size := io.inst.size
  io.cached_inst.addr := inst_physical_addr
  io.cached_inst.wdata := io.inst.wdata

  io.uncached_inst.req := !should_cache_inst && io.inst.req
  io.uncached_inst.wr  := io.inst.wr
  io.uncached_inst.size := io.inst.size
  io.uncached_inst.addr := inst_physical_addr
  io.uncached_inst.wdata := io.inst.wdata


  io.inst.addr_ok := Mux(should_cache_inst,io.cached_inst.addr_ok,io.uncached_inst.addr_ok)
  io.inst.data_ok := Mux(should_cache_inst,io.cached_inst.data_ok,io.uncached_inst.data_ok)
  io.inst.rdata := Mux(should_cache_inst,io.cached_inst.rdata,io.uncached_inst.rdata)
  io.inst.inst_valid := Mux(should_cache_inst,io.cached_inst.inst_valid,io.uncached_inst.inst_valid)
}
