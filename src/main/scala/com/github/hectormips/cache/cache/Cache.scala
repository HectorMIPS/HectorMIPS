package com.github.hectormips.cache.cache

import chisel3._
import chisel3.util._
import com.github.hectormips.cache.setting._
import com.github.hectormips.{AXIIO, AXIIOWithoutWid, SRamLikeDataIO, SRamLikeIO, SRamLikeInstIO}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import com.github.hectormips.cache.dcache.DCache
import com.github.hectormips.cache.icache.ICache
import com.github.hectormips.cache.uncache.Uncache
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
    val dcache = Flipped(new SRamLikeDataIO())
    val uncached = Flipped(new SRamLikeDataIO())

    val axi = new AXIIO(3)
  }
  )
  val dcache = Module(new DCache(new CacheConfig()))
//  val icache = Module(new ICache(new CacheConfig(WayWidth=8*1024,DataWidthByByte=32)))
  // 2路组相连，每页8KB 每行32B
  val icache = Module(new ICache(new CacheConfig()))
  val uncached = Module(new Uncache())

  //icache 与CPU接口
  icache.io.valid := io.icache.req
  icache.io.addr := io.icache.addr

  io.icache.addr_ok := icache.io.addr_ok
  io.icache.rdata :=  icache.io.inst
  io.icache.data_ok := icache.io.instOK
  io.icache.rdata_valid_mask := icache.io.instValid

  //dcache
  dcache.io.valid := io.dcache.req
  dcache.io.addr := io.dcache.addr
  dcache.io.size := io.dcache.size
  dcache.io.wr := io.dcache.wr
  dcache.io.wdata := io.dcache.wdata
  io.dcache.rdata :=  dcache.io.rdata
  io.dcache.addr_ok := dcache.io.addr_ok
  io.dcache.data_ok := dcache.io.data_ok

  //uncached
  uncached.io.input <> io.uncached

  // 读地址通道
  io.axi.arid := Cat(uncached.io.axi.araddr,icache.io.axi.readAddr.bits.id,dcache.io.axi.readAddr.bits.id)
  io.axi.araddr := Cat(uncached.io.axi.araddr,icache.io.axi.readAddr.bits.addr,dcache.io.axi.readAddr.bits.addr)
  io.axi.arlen := Cat(uncached.io.axi.arlen,icache.io.axi.readAddr.bits.len,dcache.io.axi.readAddr.bits.len)
  io.axi.arsize := Cat(uncached.io.axi.arsize,icache.io.axi.readAddr.bits.size,dcache.io.axi.readAddr.bits.size)
  io.axi.arburst := Cat(uncached.io.axi.arburst,icache.io.axi.readAddr.bits.burst,dcache.io.axi.readAddr.bits.burst)
  io.axi.arlock := Cat(uncached.io.axi.arlock,icache.io.axi.readAddr.bits.lock,dcache.io.axi.readAddr.bits.lock)
  io.axi.arcache := Cat(uncached.io.axi.arcache,icache.io.axi.readAddr.bits.cache,dcache.io.axi.readAddr.bits.cache)
  io.axi.arprot := Cat(uncached.io.axi.arprot,icache.io.axi.readAddr.bits.prot,dcache.io.axi.readAddr.bits.prot)
  io.axi.arvalid := Cat(uncached.io.axi.arvalid,icache.io.axi.readAddr.valid,dcache.io.axi.readAddr.valid)
  uncached.io.axi.arready := io.axi.arready(2)
  icache.io.axi.readAddr.ready := io.axi.arready(1)
  dcache.io.axi.readAddr.ready := io.axi.arready(0)

  // 读通道
  uncached.io.axi.rid := io.axi.rid(11,8)
  icache.io.axi.readData.bits.id := io.axi.rid(7,4)
  dcache.io.axi.readData.bits.id := io.axi.rid(3,0)
  uncached.io.axi.rdata := io.axi.rdata(95,64)
  icache.io.axi.readData.bits.data := io.axi.rdata(63,32)
  dcache.io.axi.readData.bits.data := io.axi.rdata(31,0)
  uncached.io.axi.rresp := io.axi.rresp(5,4)
  icache.io.axi.readData.bits.resp := io.axi.rresp(3,2)
  dcache.io.axi.readData.bits.resp := io.axi.rresp(1,0)
  uncached.io.axi.rlast := io.axi.rlast(2)
  icache.io.axi.readData.bits.last := io.axi.rlast(1)
  dcache.io.axi.readData.bits.last := io.axi.rlast(0)
  uncached.io.axi.rvalid :=  io.axi.rvalid(2)
  icache.io.axi.readData.valid :=  io.axi.rvalid(1)
  dcache.io.axi.readData.valid :=  io.axi.rvalid(0)
  io.axi.rready := Cat(uncached.io.axi.rready,icache.io.axi.readData.ready,dcache.io.axi.readData.ready)

  // 写地址通道
  io.axi.awid := Cat(uncached.io.axi.awid,0.U(4.W),dcache.io.axi.writeAddr.bits.id)
  io.axi.awaddr := Cat(uncached.io.axi.awaddr,0.U(32.W),dcache.io.axi.writeAddr.bits.addr)
  io.axi.awlen := Cat(uncached.io.axi.awlen,0.U(4.W),dcache.io.axi.writeAddr.bits.len)
  io.axi.awsize := Cat(uncached.io.axi.awsize,0.U(3.W),dcache.io.axi.writeAddr.bits.size)
  io.axi.awburst := Cat(uncached.io.axi.awburst,0.U(2.W),dcache.io.axi.writeAddr.bits.burst)
  io.axi.awlock := Cat(uncached.io.axi.awlock,0.U(2.W),dcache.io.axi.writeAddr.bits.lock)
  io.axi.awcache := Cat(uncached.io.axi.awcache,0.U(4.W),dcache.io.axi.writeAddr.bits.cache)
  io.axi.awprot := Cat(uncached.io.axi.awprot,0.U(3.W),dcache.io.axi.writeAddr.bits.prot)
  io.axi.awvalid := Cat(uncached.io.axi.awvalid,0.U(1.W),dcache.io.axi.writeAddr.valid)
  uncached.io.axi.awready := io.axi.awready(2)
  dcache.io.axi.writeAddr.ready := io.axi.awready(0)

  // 写数据通道
  io.axi.wid := Cat(uncached.io.axi.awid,0.U,dcache.io.axi.writeAddr.bits.id) // writeData 没有id ，直接用writeAddr的id
  io.axi.wdata := Cat(uncached.io.axi.wdata,0.U(32.W),dcache.io.axi.writeData.bits.data)
  io.axi.wstrb := Cat(uncached.io.axi.wstrb,0.U(4.W),dcache.io.axi.writeData.bits.strb)
  io.axi.wlast := Cat(uncached.io.axi.wlast,0.U(1.W),dcache.io.axi.writeData.bits.last)
  io.axi.wvalid := Cat(uncached.io.axi.wvalid,0.U(1.W),dcache.io.axi.writeData.valid)
  uncached.io.axi.wready := io.axi.wready(2)
  dcache.io.axi.writeData.ready := io.axi.wready(0)

  // 写响应通道
  uncached.io.axi.bid := io.axi.bid(11,8)
  dcache.io.axi.writeResp.bits.id := io.axi.bid(3,0)

  uncached.io.axi.bresp := io.axi.bresp(5,4)
  dcache.io.axi.writeResp.bits.resp := io.axi.bresp(1,0)

  uncached.io.axi.bvalid := io.axi.bvalid(2)
  dcache.io.axi.writeResp.valid := io.axi.bvalid(0)

  io.axi.bready := Cat(uncached.io.axi.bready,0.U(1.W),dcache.io.axi.writeResp.ready)

}

object Cache extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new Cache(new CacheConfig()))))
}
