package com.github.hectormips.cache.dcache

import chisel3._
import chisel3.util._
import org.scalatest._
import chiseltest._
import com.github.hectormips.amba.{AXIAddr, AXIWriteData, AXIWriteResponse}
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation
import com.github.hectormips.cache.setting.CacheConfig

//class SimpleAXIMock extends Module{
//  var io = IO(new Bundle {
//    val writeAddr = Flipped(Decoupled(new AXIAddr(32, 4)))
//    val writeData = Flipped(Decoupled(new AXIWriteData(32, 4)))
//    val writeResp = Decoupled(new AXIWriteResponse(4))
//  })
//
//  io.writeAddr.ready := clock.asBool()
//  io.writeData.ready := clock.asBool()
//  io.writeResp.bits.id := 0.U
//  io.writeResp.bits.resp := 0.U
//  val respReg  = RegInit(false.B)
//  io.writeResp.valid := respReg
//  when(io.writeData.fire()) {
//    respReg := true.B
//  }
//  when(io.writeResp.fire()){
//    respReg := false.B
//  }
//}
//
//class victimTester(val config:CacheConfig) extends  Module{
//  var io = IO(new Bundle {
//    val addr = Input(UInt(32.W))
//    val idata = Input(Vec(config.bankNum, UInt(32.W)))
//
//    val op   = Input(Bool()) //op=0 读，op=1 驱逐写
//    val dirty = Input(Bool())
//    val full   = Output(Bool())
//    val find = Output(Bool()) // 匹配
//    val odata = Output(Vec(config.bankNum, UInt(32.W)))
//
//    val axi = new Bundle{
//      val writeAddr  =  Decoupled(new AXIAddr(32,4))
//      val writeData  = Decoupled(new AXIWriteData(32,4))
//      val writeResp =  Flipped(Decoupled( new AXIWriteResponse(4)))
//    }
//  })
//  val axi = Module(new SimpleAXIMock())
//  val buffer = Module(new Victim(new CacheConfig()))
//
//  axi.io.writeAddr <> buffer.io.axi.writeAddr
//  axi.io.writeData <> buffer.io.axi.writeData
//  axi.io.writeResp <> buffer.io.axi.writeResp
//  io <> buffer.io
//}



class victimTest  extends FlatSpec with ChiselScalatestTester with Matchers{
  behavior of "TestVictim"
  it should "test_4bank" in { //测试每路4个bank
    test(new Victim(new CacheConfig())).withAnnotations(Seq(WriteVcdAnnotation)) { buffer =>
      buffer.io.waddr.poke("h1234".U) //tag = 1 ,index=0x23
      buffer.io.wvalid.poke(true.B)
      buffer.io.dirty.poke(false.B)
      // 默认四个周期传完
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h12345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h22345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h32345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h42345678".U)
      buffer.clock.step(1)
      buffer.io.waddr.poke("h12345".U) //tag = 1 ,index=0x23
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("ha2345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("hb2345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("hc2345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("hd2345678".U)
      buffer.clock.step(1)
      buffer.io.wvalid.poke(false.B)
      buffer.clock.step(1)
      buffer.io.qaddr.poke("h12345".U) //tag = 1 ,index=0x23
      buffer.io.qvalid.poke(true.B)
      //      buffer.clock.step(2)
//      buffer.io.find.expect(true.B)
      buffer.clock.step(2)
      buffer.io.qvalid.poke(false.B)
      buffer.io.qdata(0).expect("ha2345678".U)
      buffer.clock.step(1)
      buffer.io.qdata(0).expect("hb2345678".U)
      buffer.clock.step(1)
      buffer.io.qdata(0).expect("hc2345678".U)
      buffer.clock.step(1)
      buffer.io.qdata(0).expect("hd2345678".U)
      buffer.io.wvalid.poke(true.B)
      buffer.io.dirty.poke(true.B)
      buffer.clock.step(1)

      buffer.io.waddr.poke("h12345".U) //tag = 1 ,index=0x23
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("ha2345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("hb2345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("hc2345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("hd2345678".U)
      buffer.io.axi.writeAddr.ready.poke(true.B)
      buffer.clock.step(1)
      buffer.io.wvalid.poke(false.B)
      buffer.clock.step(1)
      buffer.io.axi.writeAddr.ready.poke(false.B)
      buffer.io.axi.writeData.ready.poke(true.B)
      buffer.clock.step(1)
      buffer.io.axi.writeData.ready.poke(false.B)
      buffer.io.axi.writeResp.valid.poke(true.B) // 1结束
      buffer.clock.step(1)
      buffer.io.axi.writeData.ready.poke(true.B)
      buffer.io.axi.writeResp.valid.poke(false.B)
      buffer.clock.step(1)
      buffer.io.axi.writeData.ready.poke(false.B)
      buffer.io.axi.writeResp.valid.poke(true.B)// 2结束
      buffer.clock.step(1)
      buffer.io.axi.writeData.ready.poke(true.B)
      buffer.io.axi.writeResp.valid.poke(false.B)
      buffer.clock.step(1)
      buffer.io.axi.writeData.ready.poke(false.B)
      buffer.io.axi.writeResp.valid.poke(true.B)// 3结束
      buffer.clock.step(1)
      buffer.io.axi.writeData.ready.poke(true.B)
      buffer.io.axi.writeResp.valid.poke(false.B)
      buffer.clock.step(1)
      buffer.io.axi.writeData.ready.poke(false.B)
      buffer.io.axi.writeResp.bits.id.poke(1.U)// 4结束
      buffer.io.axi.writeResp.valid.poke(true.B)// 4结束
      buffer.clock.step(2)

      //测试同时驱逐和加入
      buffer.io.waddr.poke("h12345".U) //tag = 1 ,index=0x23
      buffer.io.qvalid.poke(true.B)
      buffer.io.dirty.poke(false.B)
      buffer.clock.step(1)
      buffer.io.qvalid.poke(false.B)
      buffer.io.wvalid.poke(true.B)
      buffer.clock.step(1)
      buffer.io.wvalid.poke(false.B)
      buffer.io.wdata(0).poke("h12345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h22345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h32345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h42345678".U)
      buffer.clock.step(1)

      buffer.io.qaddr.poke("h12345".U) //tag = 1 ,index=0x23
      buffer.io.qvalid.poke(true.B)
      buffer.clock.step(1)
      buffer.io.qvalid.poke(false.B)
      buffer.io.qdata(0).expect("h12345678".U)
      buffer.clock.step(1)
      buffer.io.qdata(0).expect("h22345678".U)
      buffer.clock.step(1)
      buffer.io.qdata(0).expect("h32345678".U)
      buffer.clock.step(1)
      buffer.io.qdata(0).expect("h42345678".U)

    }
  }

  it should "test_8bank" in { //测试每路4个bank
    test(new Victim(new CacheConfig(DataWidthByByte=32))).withAnnotations(Seq(WriteVcdAnnotation)) { buffer =>
      buffer.io.waddr.poke("h12345".U) //tag = 1 ,index=0x23
      buffer.io.wvalid.poke(true.B)
      buffer.io.dirty.poke(false.B)
      // 默认四个周期传完
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h12345678".U)
      buffer.io.wdata(1).poke("h22345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h32345678".U)
      buffer.io.wdata(1).poke("h42345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h52345678".U)
      buffer.io.wdata(1).poke("h62345678".U)
      buffer.clock.step(1)
      buffer.io.wdata(0).poke("h72345678".U)
      buffer.io.wdata(1).poke("h82345678".U)
      buffer.clock.step(1)
      buffer.io.wvalid.poke(false.B)
      buffer.clock.step(1)
      buffer.io.qvalid.poke(true.B)
      buffer.io.qaddr.poke("h12345".U) //tag = 1 ,index=0x23
      //      buffer.clock.step(2)
      //      buffer.io.find.expect(true.B)
      buffer.clock.step(2)
      buffer.io.qvalid.poke(false.B)
      buffer.io.qdata(0).expect("h12345678".U)
      buffer.io.qdata(1).expect("h22345678".U)
      buffer.clock.step(1)
      buffer.io.qdata(0).expect("h32345678".U)
      buffer.io.qdata(1).expect("h42345678".U)
      buffer.clock.step(5)

    }
  }
}
