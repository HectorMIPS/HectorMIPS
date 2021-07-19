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
  it should "test2port" in {
    test(new Victim(new CacheConfig())).withAnnotations(Seq(WriteVcdAnnotation)) { buffer =>

      buffer.io.addr.poke("h1234".U) //tag = 1 ,index=0x23
      buffer.io.idata(0).poke("h12345678".U)
      buffer.io.idata(1).poke("h22345678".U)
      buffer.io.idata(2).poke("h32345678".U)
      buffer.io.idata(3).poke("h42345678".U)

      buffer.io.op.poke(true.B)
      buffer.io.dirty.poke(true.B)
      buffer.io.axi.writeAddr.ready.poke(true.B)
      buffer.clock.step(1)
      buffer.io.op.poke(false.B)
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
      buffer.io.axi.writeResp.valid.poke(true.B)// 4结束
      buffer.clock.step(1)
      buffer.io.addr.poke("h1234".U)
      buffer.io.odata(0).expect("h12345678".U)
      buffer.io.odata(1).expect("h22345678".U)
      buffer.io.odata(2).expect("h32345678".U)
      buffer.io.odata(3).expect("h42345678".U)
      buffer.clock.step(1)
      buffer.io.addr.poke("h1238".U)
      buffer.io.odata(0).expect("h12345678".U)
      buffer.io.odata(1).expect("h22345678".U)
      buffer.io.odata(2).expect("h32345678".U)
      buffer.io.odata(3).expect("h42345678".U)
      buffer.clock.step(1)
      buffer.io.addr.poke("h1240".U)
      buffer.io.find.expect(false.B)
      buffer.clock.step(4)
    }
  }
}
