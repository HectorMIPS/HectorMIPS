//package com.github.hectormips.cache.icache
//
//import chisel3._
//import org.scalatest._
//import chiseltest._
//
//import chiseltest.experimental.TestOptionBuilder._
//import chiseltest.internal.WriteVcdAnnotation
//import com.github.hectormips.cache.setting.CacheConfig
//class icacheTest  extends FlatSpec with ChiselScalatestTester with Matchers {
//  behavior of "TestICache"
//  it should "test2port" in {
//    test(new ICache(new CacheConfig())).withAnnotations(Seq(WriteVcdAnnotation)) { icache =>
//      // 重置
////      icache.reset.poke(true.B)
////      icache.clock.step(257)
////      icache.reset.poke(false.B)
//      icache.io.valid.poke(true.B)
//      icache.io.addr.poke(0x1234.U) //  TAG:00001 INDEX:23 Offset:4/4=
//      icache.clock.step(1)
//      icache.io.addr_ok.expect(true.B)
//      icache.io.valid.poke(false.B)
//      icache.io.axi.readAddr.ready.poke(false.B)
//      icache.clock.step(1)
//      icache.io.axi.readAddr.ready.poke(true.B)
//      icache.clock.step(1)
//      icache.io.axi.readData.valid.poke(true.B)
//      icache.io.axi.readData.bits.data.poke(0x12345678.U)
//      while (!icache.io.axi.readData.ready.peek().litToBoolean) {
//        icache.clock.step(1)
//      }
//      icache.clock.step(1)
//      icache.io.axi.readData.bits.data.poke("h81234567".U)
//      while (!icache.io.axi.readData.ready.peek().litToBoolean){
//        icache.clock.step(1)
//      }
//      icache.clock.step(1)
//      icache.io.axi.readData.bits.data.poke("h78123456".U)
//      while (!icache.io.axi.readData.ready.peek().litToBoolean){
//        icache.clock.step(1)
//      }
//      icache.clock.step(1)
//      icache.io.axi.readData.bits.data.poke("h67812345".U)
//      icache.io.axi.readData.bits.last.poke(true.B)
//      while (!icache.io.axi.readData.ready.peek().litToBoolean){
//        icache.clock.step(1)
//      }
//      icache.clock.step(1)
//      icache.io.axi.readData.bits.last.poke(false.B)
//      icache.io.axi.readData.valid.poke(false.B)
//      icache.io.axi.readData.bits.data.poke(0.U)
//      icache.io.inst.expect("h8123456712345678".U) // 高位是后面的地址
////      icache.io.inst2.expect("h".U)
//      icache.clock.step(1)
//      icache.io.valid.poke(true.B)
//      icache.io.addr.poke(0x1234.U) //  TAG:00001 INDEX:23 Offset:4/4=
//      icache.clock.step(1)
//      icache.io.valid.poke(false.B)
//      icache.io.addr_ok.expect(true.B)
//      icache.io.inst.expect("h8123456712345678".U) // 高位是后面的地址
//      icache.clock.step(1)
//      icache.io.valid.poke(true.B)
//      icache.io.addr.poke(0x1238.U) //  TAG:00001 INDEX:23 Offset:8/4=2
//      icache.clock.step(1)
//      icache.io.valid.poke(false.B)
//      icache.io.addr_ok.expect(true.B)
//      icache.io.inst.expect("h7812345681234567".U)
////      icache.io.inst2.expect("h".U)
//      icache.clock.step(1)
//      icache.io.valid.poke(true.B)
//      icache.io.addr.poke(0x123c.U) //  TAG:00001 INDEX:23 Offset:c/4=3
//      icache.clock.step(1)
//      icache.io.valid.poke(false.B)
//      icache.io.addr_ok.expect(true.B)
////      val v = icache.io.inst.peek()
////      assert(v.asUInt().equals("h78123456".U))
//      icache.clock.step(1)
//      icache.io.valid.poke(true.B)
//      icache.io.addr.poke(0x2234.U) //  TAG:00002 INDEX:23 Offset:4/4=
//      icache.clock.step(1)
//      icache.io.valid.poke(false.B)
//      icache.io.addr_ok.expect(true.B)
//      icache.clock.step(1)
//      icache.io.axi.readAddr.ready.poke(true.B)
//      icache.clock.step(1)
//      icache.io.axi.readData.valid.poke(true.B)
//      icache.io.axi.readData.bits.data.poke("h87654321".U)
//      while (!icache.io.axi.readData.ready.peek().litToBoolean) {
//        icache.clock.step(1)
//      }
//      icache.clock.step(1)
//      icache.io.axi.readData.bits.data.poke("h18765432".U)
//      while (!icache.io.axi.readData.ready.peek().litToBoolean){
//        icache.clock.step(1)
//      }
//      icache.clock.step(1)
//      icache.io.axi.readData.bits.data.poke("h21876543".U)
//      while (!icache.io.axi.readData.ready.peek().litToBoolean){
//        icache.clock.step(1)
//      }
//      icache.clock.step(1)
//      icache.io.axi.readData.bits.data.poke("h32187654".U)
//      icache.io.axi.readData.bits.last.poke(true.B)
//      while (!icache.io.axi.readData.ready.peek().litToBoolean){
//        icache.clock.step(1)
//      }
//      icache.clock.step(1)
//      icache.io.axi.readData.bits.last.poke(false.B)
//      icache.io.axi.readData.valid.poke(false.B)
//      icache.io.axi.readData.bits.data.poke(0.U)
//      icache.io.inst.expect("h1876543287654321".U)
////      icache.io.inst2.expect("h".U)
//      icache.clock.step(1)
//
//      icache.io.valid.poke(true.B)
//      icache.io.addr.poke(0x1234.U) //  TAG:00001 INDEX:23 Offset:4/4=
//      icache.clock.step(1)
//      icache.io.valid.poke(false.B)
//      icache.io.addr_ok.expect(true.B)
//      icache.io.inst.expect("h8123456712345678".U)
////      icache.io.inst2.expect("h".U)
//      icache.clock.step(1)
//    }
//  }
//}
//
