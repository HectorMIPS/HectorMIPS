package com.github.hectormips.amba

import chisel3._
import chisel3.iotesters.{PeekPokeTester,ChiselFlatSpec,Driver}
import org.scalatest._
import chiseltest._

//class TestSRAMLike2AXI(bridge:ICache2AXI) extends PeekPokeTester(bridge){

//    expect(.data_ok,true.B)
//    expect(bridge.io.inst(iter).rdata,value.U(32.W))
//  }
//  def check_data(value:Int):Unit={
////    step(1)
//
//
//
//    poke(bridge.io.axi.readData.bits.last,false.B)
//    poke(bridge.io.valid,false.B)
//    step(1)
//  }

//  def check_burst(bridge:SRAMLike2AXI,addr:Int,value:Int):Unit= {
//
//  }
//  check_single(bridge,0x123456,0x654321)
//  check_single(bridge,0x123456,0x65432)
//  check_single(bridge,0x123456,0x6543)
//  check_single(bridge,0x123456,0x654)

//}

class TestSRAMLike2AXI extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Testers2"

  //定义一个测试案例
  it should "test sequential circuits" in {
    //定义被测的模块
    test(new ICache2AXI()) { bridge =>
      add_request()
      give_single_data(0.U, 0.U, 0x123456.U, false.B)
      println("recv 1 data")
      give_single_data(1.U, 0.U, 0x12345.U, true.B)
      println("recv 2 data")
      bridge.io.inst(0).data_ok.expect(true.B)
      bridge.io.inst(0).rdata.expect(0x123456.U)
      bridge.io.inst(1).data_ok.expect(true.B)
      bridge.io.inst(1).rdata.expect(0x12345.U)
      bridge.io.valid.poke(false.B)
      bridge.clock.step(1)
      add_request()
      give_single_data(0.U, 0.U, 0x123.U, false.B)
      println("recv 1 data")
      give_single_data(1.U, 0.U, 0x12.U, true.B)
      println("recv 2 data")
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
        bridge.clock.step(1)
        bridge.io.axi.readData.valid.poke(false.B)

      }
    }
  }
}


//object AdderTestGen extends App {
//  chisel3.iotesters.Driver.execute(args, () => new ICache2AXI())(c => new TestSRAMLike2AXI(c))
//}

