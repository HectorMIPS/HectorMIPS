package com.github.hectormips.amba

import scala.util._
import chisel3._
import chisel3.iotesters._
import org.scalatest._
import chiseltest._

import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation

class TestSRAMLike2AXI extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "TestSRAMLike2AXI"

  //测试普通传输
  it should "test2port" in {
    test(new ICache2AXI()).withAnnotations(Seq(WriteVcdAnnotation)) { bridge =>
//      bridge.rename()
      add_request()
      give_single_data(0.U, 0.U, 0x123456.U, false.B)
      println("recv 1 data")
      bridge.clock.step(1)
      give_single_data(1.U, 0.U, 0x12345.U, true.B)
      print(bridge.io.debug_timer.peek())
      println("recv 2 data")
      bridge.io.inst(0).data_ok.expect(true.B)
      bridge.io.inst(0).rdata.expect(0x123456.U)
      bridge.io.inst(1).data_ok.expect(true.B)
      bridge.io.inst(1).rdata.expect(0x12345.U)
      bridge.io.valid.poke(false.B)
      bridge.clock.step(1)
      bridge.clock.step(1)
      bridge.clock.step(1)
      add_request()
      give_single_data(0.U, 0.U, 0x123.U, false.B)
      println("recv 1 data")
      give_single_data(1.U, 0.U, 0x12.U, true.B)
      print(bridge.io.debug_timer.peek())
      println("recv 2 data")
//      bridge.clock.step(1)
      bridge.io.inst(0).data_ok.expect(true.B)
      bridge.io.inst(0).rdata.expect(0x123.U)
      bridge.io.inst(1).data_ok.expect(true.B)
      bridge.io.inst(1).rdata.expect(0x12.U)
      def add_request(): UInt = {
        bridge.io.valid.poke(true.B)
        bridge.io.addr.poke(12345678.U)
        bridge.io.addr_ok.expect(true.B)
        val id = bridge.io.axi.readAddr.bits.id.peek()
        bridge.clock.step(1)
        bridge.io.axi.readAddr.ready.poke(true.B)
        bridge.clock.step(1)
        bridge.io.axi.readAddr.ready.poke(false.B)
        id
      }

      def give_single_data(iter: UInt, id: UInt, value: UInt, last: Bool): Unit = {
        bridge.io.axi.readData.bits.resp.poke(0.U)
        bridge.io.axi.readData.bits.id.poke(id)
        bridge.io.axi.readData.bits.data.poke(value)
        bridge.io.axi.readData.bits.last.poke(last)
        bridge.io.axi.readData.valid.poke(true.B)
        while (!bridge.io.axi.readData.ready.peek().litToBoolean) {
          bridge.clock.step(1)
        }
        bridge.io.axi.readData.valid.poke(false.B)
        if(!last.litToBoolean)bridge.clock.step(1)
      }
    }
  }
  //测试随机延迟
  it should "test random delay success" in {
    test(new ICache2AXI()).withAnnotations(Seq(WriteVcdAnnotation)) { bridge =>
      for(i<-0 until 100){
        check_random_delay(0.U)
      }
//      check_random_delay(0.U)

      def check_random_delay(id: UInt):Unit={
      add_request()

      give_random_delay_data(id,scala.util.Random.nextInt(100000).U(32.W),scala.util.Random.nextInt(100000).U(32.W))
    }
    def add_request(): UInt = {
      bridge.io.valid.poke(true.B)
      bridge.io.addr.poke(12345678.U)
      bridge.io.addr_ok.expect(true.B)
      val id = bridge.io.axi.readAddr.bits.id.peek()
      bridge.clock.step(1)
      bridge.io.axi.readAddr.ready.poke(true.B)
      bridge.clock.step(1)
      bridge.io.axi.readAddr.ready.poke(false.B)
      id
    }
    def give_random_delay_data(id: UInt, value1: UInt,value2:UInt):Unit={
      val delay1 = scala.util.Random.nextInt(10)+1
//      printf(")
      println(delay1)
      bridge.clock.step(delay1)
      println("begin to send data")
      bridge.io.axi.readData.bits.resp.poke(0.U)
      bridge.io.axi.readData.bits.id.poke(id)
      bridge.io.axi.readData.bits.data.poke(value1)
      bridge.io.axi.readData.bits.last.poke(false.B)
      bridge.io.axi.readData.valid.poke(true.B)
      bridge.clock.step(1)
      while (!bridge.io.axi.readData.ready.peek().litToBoolean) {
        bridge.clock.step(1)
      }
      bridge.io.axi.readData.valid.poke(false.B)
      bridge.clock.step(1)
      val delay2 = scala.util.Random.nextInt(10)+1
      println(delay2)
      bridge.clock.step(delay2)
      bridge.io.axi.readData.bits.resp.poke(0.U)
      bridge.io.axi.readData.bits.id.poke(id)
      bridge.io.axi.readData.bits.data.poke(value2)
      bridge.io.axi.readData.bits.last.poke(true.B)
      bridge.io.axi.readData.valid.poke(true.B)
      bridge.clock.step(1)
      while (!bridge.io.axi.readData.ready.peek().litToBoolean) {
        bridge.clock.step(1)
      }
      bridge.io.axi.readData.valid.poke(false.B)
      bridge.io.inst(0).data_ok.expect(true.B)
      bridge.io.inst(0).rdata.expect(value1)
      bridge.io.inst(1).data_ok.expect(true.B)
      bridge.io.inst(1).rdata.expect(value2)
      bridge.clock.step(1)
    }

  }
  }
}


//object AdderTestGen extends App {
//  chisel3.iotesters.Driver.execute(args, () => new ICache2AXI())(c => new TestSRAMLike2AXI(c))
//}

