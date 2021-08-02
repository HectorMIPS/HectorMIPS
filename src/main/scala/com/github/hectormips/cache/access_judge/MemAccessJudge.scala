package com.github.hectormips.cache.access_judge

import com.github.hectormips.{AXIIOWithoutWid, SRamLikeDataIO, SRamLikeInstIO}
import chisel3._
import chisel3.util._
import com.github.hectormips.cache.uncache.UncacheInst




class QueueItem extends Bundle{
  val should_cache = Bool()
  val addr         = UInt(32.W)
  val wdata         = UInt(32.W)
  val size         = UInt(3.W)
  val wr           = Bool()
}

/**
 * 访存判断逻辑
 *
 * 分为3路
 * 一路作uncache 以后再写
 * 一路访问指令
 * 一路访问cached数据
 */
class MemAccessJudge(cache_all_inst:Bool=false.B) extends Module{
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
  val data_physical_addr = Wire(Vec(2,UInt(32.W)))
  val inst_physical_addr = Wire(UInt(32.W))




//  def addr_mapping(vaddr: UInt): UInt = {
//    val physical_addr: UInt = Wire(UInt(32.W))
//    physical_addr := Mux((vaddr >= 0x80000000L.U && vaddr <= 0x9fffffffL.U) ||
//      (vaddr >= 0xa0000000L.U && vaddr <= 0xbfffffffL.U),
//      vaddr & 0x1fffffff.U, vaddr)
//    physical_addr
//  }


  val should_cache_data_r = RegInit(VecInit(Seq.fill(2)(false.B)))
  val should_cache_data_c = Wire(Vec(2,Bool()))
  val should_cache_data   = Wire(Vec(2,Bool()))
  // uncache的地址范围： 0xa000_0000 -- 0xbfff_ffff
  //

  for(i<- 0 until 2) {
    /**
     * cache 属性确认
     */
    should_cache_data(i) := Mux(io.data(i).req,should_cache_data_c(i),should_cache_data_r(i))
    when(io.data(i).req){
      should_cache_data_r(i) := should_cache_data_c(i)
    }
    when(io.data(i).addr >= "ha000_0000".U && io.data(i).addr <= "hbfff_ffff".U){
      should_cache_data_c(i) := false.B
    }.otherwise {
      should_cache_data_c(i) := true.B
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

//  val should_cache_inst_r = RegInit(false.B)
  val should_cache_inst_c = Wire(Bool())
//  val should_cache_inst   = Wire(Bool())

  val queue = Module(new Queue(new QueueItem, 3))
  /**
   * 如果需要快速测试，cache_all_inst 设为true即可
   */
  when(cache_all_inst) {
    should_cache_inst_c := true.B
  }.otherwise{
      when(io.inst.addr >= "ha000_0000".U && io.inst.addr <= "hbfff_ffff".U){
        should_cache_inst_c := false.B
      }.otherwise {
        should_cache_inst_c := true.B
      }
  }


  when(io.inst.addr >= "h8000_0000".U && io.inst.addr <= "hbfff_ffff".U){
    inst_physical_addr := queue.io.deq.bits.addr  & "h1fff_ffff".U
  }.otherwise{
    inst_physical_addr := queue.io.deq.bits.addr
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
  io.inst.addr_ok := queue.io.enq.ready

  queue.io.enq.bits.addr := io.inst.addr
  queue.io.enq.bits.wr := io.inst.wr
  queue.io.enq.bits.size := io.inst.size
  queue.io.enq.bits.wdata := io.inst.wdata
  queue.io.enq.bits.should_cache := should_cache_inst_c
  queue.io.enq.valid := io.inst.req
  val handshake = RegInit(false.B)
  when(io.inst.req){
    handshake := false.B
  }
  when(io.cached_inst.req && io.cached_inst.addr_ok || io.uncached_inst.req && io.uncached_inst.addr_ok){
    handshake :=true.B
  }
  io.cached_inst.req := queue.io.deq.valid && !handshake && queue.io.deq.bits.should_cache
  io.cached_inst.wr := queue.io.deq.bits.wr
  io.cached_inst.size := queue.io.deq.bits.size
  io.cached_inst.addr := inst_physical_addr
  io.cached_inst.wdata := queue.io.deq.bits.wdata

  io.uncached_inst.req := queue.io.deq.valid && !queue.io.deq.bits.should_cache && !handshake
  io.uncached_inst.wr  := queue.io.deq.bits.wr
  io.uncached_inst.size := queue.io.deq.bits.size
  io.uncached_inst.addr := inst_physical_addr
  io.uncached_inst.wdata := queue.io.deq.bits.wdata

  io.inst.data_ok := Mux(queue.io.deq.bits.should_cache,io.cached_inst.data_ok,io.uncached_inst.data_ok)
  io.inst.rdata := Mux(queue.io.deq.bits.should_cache,io.cached_inst.rdata,io.uncached_inst.rdata)
  io.inst.inst_valid := Mux(queue.io.deq.bits.should_cache,io.cached_inst.inst_valid,io.uncached_inst.inst_valid)
  io.inst.inst_pc := queue.io.deq.bits.addr
  queue.io.deq.ready := io.inst.data_ok
}
