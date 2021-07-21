package com.github.hectormips.cache.cache

import chisel3._
import chisel3.util._
import com.github.hectormips.cache.setting._
import com.github.hectormips.{AXIIO, AXIIOWithoutWid, SRamLikeIO, SRamLikeInstIO}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import com.github.hectormips.cache.dcache.DCache
import com.github.hectormips.cache.icache.ICache


class Cache(val config:CacheConfig)  extends Module{
  val io = IO(new Bundle{
    val icache = Flipped(new SRamLikeInstIO)
    val dcache = Flipped(new SRamLikeIO(rdata_width = 32))
    val axi = new AXIIOWithoutWid(2)
  }
  )
  val dcache = Module(new DCache(new CacheConfig()))
  val icache = Module(new ICache(new CacheConfig()))

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
  dcache.io.wstrb := "b1111".U
  io.dcache.rdata :=  dcache.io.rdata
  io.dcache.addr_ok := dcache.io.addr_ok
  io.dcache.data_ok := dcache.io.data_ok



  // 读地址通道
  io.axi.arid := Cat(icache.io.axi.readAddr.bits.id,dcache.io.axi.readAddr.bits.id)
  io.axi.araddr := Cat(icache.io.axi.readAddr.bits.addr,dcache.io.axi.readAddr.bits.addr)
  io.axi.arlen := Cat(icache.io.axi.readAddr.bits.len,dcache.io.axi.readAddr.bits.len)
  io.axi.arsize := Cat(icache.io.axi.readAddr.bits.size,dcache.io.axi.readAddr.bits.size)
  io.axi.arburst := Cat(icache.io.axi.readAddr.bits.burst,dcache.io.axi.readAddr.bits.burst)
  io.axi.arlock := Cat(icache.io.axi.readAddr.bits.lock,dcache.io.axi.readAddr.bits.lock)
  io.axi.arcache := Cat(icache.io.axi.readAddr.bits.cache,dcache.io.axi.readAddr.bits.cache)
  io.axi.arprot := Cat(icache.io.axi.readAddr.bits.prot,dcache.io.axi.readAddr.bits.prot)
  io.axi.arvalid := Cat(icache.io.axi.readAddr.valid,dcache.io.axi.readAddr.valid)
  icache.io.axi.readAddr.ready := io.axi.arready(1)
  dcache.io.axi.readAddr.ready := io.axi.arready(0)

  // 读通道

  icache.io.axi.readData.bits.id := io.axi.rid(7,4)
  dcache.io.axi.readData.bits.id := io.axi.rid(3,0)
  icache.io.axi.readData.bits.data := io.axi.rdata(63,32)
  dcache.io.axi.readData.bits.data := io.axi.rdata(31,0)
  icache.io.axi.readData.bits.resp := io.axi.rresp(3,2)
  dcache.io.axi.readData.bits.resp := io.axi.rresp(1,0)
  icache.io.axi.readData.bits.last := io.axi.rlast(1)
  dcache.io.axi.readData.bits.last := io.axi.rlast(0)
  icache.io.axi.readData.valid :=  io.axi.rvalid(1)
  dcache.io.axi.readData.valid :=  io.axi.rvalid(0)
  io.axi.rready := Cat(icache.io.axi.readData.ready,dcache.io.axi.readData.ready)

  // 写地址通道
  io.axi.awid := Cat(0.U,dcache.io.axi.writeAddr.bits.id)
  io.axi.awaddr := Cat(0.U,dcache.io.axi.writeAddr.bits.addr)
  io.axi.awlen := Cat(0.U,dcache.io.axi.writeAddr.bits.len)
  io.axi.awsize := Cat(0.U,dcache.io.axi.writeAddr.bits.size)
  io.axi.awburst := Cat(0.U,dcache.io.axi.writeAddr.bits.burst)
  io.axi.awlock := Cat(0.U,dcache.io.axi.writeAddr.bits.lock)
  io.axi.awcache := Cat(0.U,dcache.io.axi.writeAddr.bits.cache)
  io.axi.awprot := Cat(0.U,dcache.io.axi.writeAddr.bits.prot)
  io.axi.awvalid := Cat(0.U,dcache.io.axi.writeAddr.valid)
  dcache.io.axi.writeAddr.ready := io.axi.awready(0)

  // 写数据通道
//  io.axi.wid := Cat(0.U,dcache.io.axi.writeAddr.bits.id) // writeData 没有id ，直接用writeAddr的id
  io.axi.wdata := Cat(0.U,dcache.io.axi.writeData.bits.data)
  io.axi.wstrb := Cat(0.U,dcache.io.axi.writeData.bits.strb)
  io.axi.wlast := Cat(0.U,dcache.io.axi.writeData.bits.last)
  io.axi.wvalid := Cat(0.U,dcache.io.axi.writeData.valid)
  dcache.io.axi.writeData.ready := io.axi.wready(0)
  // 写响应通道
  dcache.io.axi.writeResp.bits.id := io.axi.bid(3,0)
  dcache.io.axi.writeResp.bits.resp := io.axi.bresp(1,0)
  dcache.io.axi.writeResp.valid := io.axi.wvalid(0)
  io.axi.bready := Cat(0.U,dcache.io.axi.writeResp.ready)

}

object Cache extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new Cache(new CacheConfig()))))
}
