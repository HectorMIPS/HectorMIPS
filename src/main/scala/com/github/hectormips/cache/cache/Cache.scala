package com.github.hectormips.cache.cache

import chisel3._
import chisel3.util._
import com.github.hectormips.cache.setting._
import com.github.hectormips.{AXIIO, AXIIOWithoutWid, SRamLikeDataIO, SRamLikeIO, SRamLikeInstIO}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import com.github.hectormips.cache.dcache.DCache
import com.github.hectormips.cache.icache.ICache
import com.github.hectormips.cache.uncache.{Uncache, UncacheInst}
import chisel3.util.experimental.forceName

//class cpu_axi_interface extends BlackBox{//TODO:暂用
//  var io = IO(new Bundle{
//    val clk    = Input(new Clock())
//    val resetn = Input(Bool())
//    val data = Flipped(new SRamLikeDataIO())
//    val axi  = new AXIIOWithoutWid(1)
//  })
//  io.axi.force_name()
//}

class Cache(val config:CacheConfig)  extends Module{
  val io = IO(new Bundle{
    val icache = Flipped(new SRamLikeInstIO)
    val uncache_inst = Flipped(new SRamLikeInstIO)
    val dcache = Flipped(Vec(2,new SRamLikeDataIO()))
    val uncached = Flipped(Vec(2,new SRamLikeDataIO()))

    val icache_hit_count = Output(UInt(32.W))
    val icache_total_count = Output(UInt(32.W))
    val dcache_hit_count = Output(UInt(32.W))
    val dcache_total_count = Output(UInt(32.W))
    val axi = new AXIIO(4)
  }
  )
  val dcache = Module(new DCache(new CacheConfig(WayWidth=8*1024,DataWidthByByte=32)))
//  val icache = Module(new ICache(new CacheConfig(WayWidth=8*1024,DataWidthByByte=32)))
  // 2路组相连，每页8KB 每行32B
  val icache = Module(new ICache(new CacheConfig(WayWidth =16*1024,DataWidthByByte=64)))
  val uncached = Module(new Uncache())
  val uncache_inst = Module(new UncacheInst())

  io.dcache_hit_count := dcache.io.debug_hit_count
  io.dcache_total_count := dcache.io.debug_total_count
  io.icache_hit_count := icache.io.debug_hit_count
  io.icache_total_count := icache.io.debug_total_count

  //icache 与CPU接口
  icache.io.valid := io.icache.req
  icache.io.addr := io.icache.addr

  io.icache.addr_ok := icache.io.addr_ok
  io.icache.rdata :=  icache.io.inst
  io.icache.data_ok := icache.io.instOK
  io.icache.inst_valid := icache.io.instValid
  io.icache.inst_pc := icache.io.instPC

  //dcache
  for(i<- 0 to 1) {
    dcache.io.valid(i) := io.dcache(i).req
    dcache.io.addr(i) := io.dcache(i).addr
    dcache.io.size(i) := io.dcache(i).size
    dcache.io.wr(i) := io.dcache(i).wr
    dcache.io.wdata(i) := io.dcache(i).wdata
    io.dcache(i).rdata := dcache.io.rdata(i)
    io.dcache(i).addr_ok := dcache.io.addr_ok(i)
    io.dcache(i).data_ok := dcache.io.data_ok(i)
  }
  //uncached
  uncached.io.input <> io.uncached
  uncache_inst.io.input <> io.uncache_inst
  io.icache.inst_predict_jump_target_in := DontCare
  io.icache.inst_predict_jump_in := DontCare

  // 读地址通道
  io.axi.arid := Cat(uncache_inst.io.axi.arid,uncached.io.axi.arid,icache.io.axi.readAddr.bits.id,dcache.io.axi.readAddr.bits.id)
  io.axi.araddr := Cat(uncache_inst.io.axi.araddr,uncached.io.axi.araddr,icache.io.axi.readAddr.bits.addr,dcache.io.axi.readAddr.bits.addr)
  io.axi.arlen := Cat(uncache_inst.io.axi.arlen,uncached.io.axi.arlen,icache.io.axi.readAddr.bits.len,dcache.io.axi.readAddr.bits.len)
  io.axi.arsize := Cat(uncache_inst.io.axi.arsize,uncached.io.axi.arsize,icache.io.axi.readAddr.bits.size,dcache.io.axi.readAddr.bits.size)
  io.axi.arburst := Cat(uncache_inst.io.axi.arburst,uncached.io.axi.arburst,icache.io.axi.readAddr.bits.burst,dcache.io.axi.readAddr.bits.burst)
  io.axi.arlock := Cat(uncache_inst.io.axi.arlock,uncached.io.axi.arlock,icache.io.axi.readAddr.bits.lock,dcache.io.axi.readAddr.bits.lock)
  io.axi.arcache := Cat(uncache_inst.io.axi.arcache,uncached.io.axi.arcache,icache.io.axi.readAddr.bits.cache,dcache.io.axi.readAddr.bits.cache)
  io.axi.arprot := Cat(uncache_inst.io.axi.arprot,uncached.io.axi.arprot,icache.io.axi.readAddr.bits.prot,dcache.io.axi.readAddr.bits.prot)
  io.axi.arvalid := Cat(uncache_inst.io.axi.arvalid,uncached.io.axi.arvalid,icache.io.axi.readAddr.valid,dcache.io.axi.readAddr.valid)
  uncache_inst.io.axi.arready := io.axi.arready(3)
  uncached.io.axi.arready := io.axi.arready(2)
  icache.io.axi.readAddr.ready := io.axi.arready(1)
  dcache.io.axi.readAddr.ready := io.axi.arready(0)

  // 读通道
  uncache_inst.io.axi.rid := io.axi.rid(15,12)
  uncached.io.axi.rid := io.axi.rid(11,8)
  icache.io.axi.readData.bits.id := io.axi.rid(7,4)
  dcache.io.axi.readData.bits.id := io.axi.rid(3,0)

  uncache_inst.io.axi.rdata := io.axi.rdata(127,96)
  uncached.io.axi.rdata := io.axi.rdata(95,64)
  icache.io.axi.readData.bits.data := io.axi.rdata(63,32)
  dcache.io.axi.readData.bits.data := io.axi.rdata(31,0)

  uncache_inst.io.axi.rresp := io.axi.rresp(7,6)
  uncached.io.axi.rresp := io.axi.rresp(5,4)
  icache.io.axi.readData.bits.resp := io.axi.rresp(3,2)
  dcache.io.axi.readData.bits.resp := io.axi.rresp(1,0)

  uncache_inst.io.axi.rlast := io.axi.rlast(3)
  uncached.io.axi.rlast := io.axi.rlast(2)
  icache.io.axi.readData.bits.last := io.axi.rlast(1)
  dcache.io.axi.readData.bits.last := io.axi.rlast(0)

  uncache_inst.io.axi.rvalid := io.axi.rvalid(3)
  uncached.io.axi.rvalid :=  io.axi.rvalid(2)
  icache.io.axi.readData.valid :=  io.axi.rvalid(1)
  dcache.io.axi.readData.valid :=  io.axi.rvalid(0)

  io.axi.rready := Cat(uncache_inst.io.axi.rready,uncached.io.axi.rready,icache.io.axi.readData.ready,dcache.io.axi.readData.ready)

  // 写地址通道
  io.axi.awid := Cat(0.U(4.W),uncached.io.axi.awid,0.U(4.W),dcache.io.axi.writeAddr.bits.id)
  io.axi.awaddr := Cat(0.U(4.W),uncached.io.axi.awaddr,0.U(32.W),dcache.io.axi.writeAddr.bits.addr)
  io.axi.awlen := Cat(0.U(4.W),uncached.io.axi.awlen,0.U(4.W),dcache.io.axi.writeAddr.bits.len)
  io.axi.awsize := Cat(0.U(4.W),uncached.io.axi.awsize,0.U(3.W),dcache.io.axi.writeAddr.bits.size)
  io.axi.awburst := Cat(0.U(4.W),uncached.io.axi.awburst,0.U(2.W),dcache.io.axi.writeAddr.bits.burst)
  io.axi.awlock := Cat(0.U(4.W),uncached.io.axi.awlock,0.U(2.W),dcache.io.axi.writeAddr.bits.lock)
  io.axi.awcache := Cat(0.U(4.W),uncached.io.axi.awcache,0.U(4.W),dcache.io.axi.writeAddr.bits.cache)
  io.axi.awprot := Cat(0.U(4.W),uncached.io.axi.awprot,0.U(3.W),dcache.io.axi.writeAddr.bits.prot)
  io.axi.awvalid := Cat(0.U(4.W),uncached.io.axi.awvalid,0.U(1.W),dcache.io.axi.writeAddr.valid)
  uncached.io.axi.awready := io.axi.awready(2)
  uncache_inst.io.axi.awready := false.B
  dcache.io.axi.writeAddr.ready := io.axi.awready(0)

  // 写数据通道
  io.axi.wid := Cat(0.U(4.W),uncached.io.axi.awid,0.U(4.W),dcache.io.axi.writeAddr.bits.id) // writeData 没有id ，直接用writeAddr的id
  io.axi.wdata := Cat(0.U(4.W),uncached.io.axi.wdata,0.U(32.W),dcache.io.axi.writeData.bits.data)
  io.axi.wstrb := Cat(0.U(4.W),uncached.io.axi.wstrb,0.U(4.W),dcache.io.axi.writeData.bits.strb)
  io.axi.wlast := Cat(0.U(4.W),uncached.io.axi.wlast,0.U(1.W),dcache.io.axi.writeData.bits.last)
  io.axi.wvalid := Cat(0.U(4.W),uncached.io.axi.wvalid,0.U(1.W),dcache.io.axi.writeData.valid)
  uncached.io.axi.wready := io.axi.wready(2)
  uncache_inst.io.axi.wready := false.B
  dcache.io.axi.writeData.ready := io.axi.wready(0)

  // 写响应通道
  uncached.io.axi.bid := io.axi.bid(11,8)
  dcache.io.axi.writeResp.bits.id := io.axi.bid(3,0)
  uncache_inst.io.axi.bid :=0.U

  uncached.io.axi.bresp := io.axi.bresp(5,4)
  dcache.io.axi.writeResp.bits.resp := io.axi.bresp(1,0)
  uncache_inst.io.axi.bresp :=0.U

  uncached.io.axi.bvalid := io.axi.bvalid(2)
  dcache.io.axi.writeResp.valid := io.axi.bvalid(0)
  uncache_inst.io.axi.bvalid :=false.B

  io.axi.bready := Cat(0.U(1.W),uncached.io.axi.bready,0.U(1.W),dcache.io.axi.writeResp.ready)

}

object Cache extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new Cache(new CacheConfig()))))
}
