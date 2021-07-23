package com.github.hectormips.cache.dcache
//
import chisel3._
import chisel3.util._
import org.scalatest._
import chiseltest._
import com.github.hectormips.amba.{AXIAddr, AXIWriteData, AXIWriteResponse}
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation
import com.github.hectormips.cache.setting.CacheConfig
  class TestByteSelection extends FlatSpec with ChiselScalatestTester with Matchers{
    behavior of "TestByteSelection"
    it should "ByteSelection test good" in {
      test(new ByteSelection()).withAnnotations(Seq(WriteVcdAnnotation)) { select =>
        select.io.data.poke("h12345678".U)
        select.io.size.poke(0.U)
        select.io.offset.poke(0.U)
        select.io.select_data.expect("h78".U)
        select.io.offset.poke(1.U)
        select.io.select_data.expect("h56".U)
        select.io.offset.poke(2.U)
        select.io.select_data.expect("h34".U)
        select.io.offset.poke(3.U)
        select.io.select_data.expect("h12".U)
        select.io.size.poke(1.U)
        select.io.offset.poke(0.U)
        select.io.select_data.expect("h5678".U)
        select.io.offset.poke(1.U)
        select.io.select_data.expect("h3456".U)
        select.io.offset.poke(2.U)
        select.io.select_data.expect("h1234".U)
        select.io.offset.poke(3.U)
        select.io.select_data.expect("h1234".U)
        select.io.size.poke(2.U)
        select.io.select_data.expect("h12345678".U)
      }
    }
  }

//class dcacheTester extends FlatSpec with ChiselScalatestTester with Matchers{
//  behavior of "TestDCache"
//  def give_data(dcache:DCache,data:Seq[UInt]):Unit={
//    while(dcache.io.axi.readAddr.valid.peek().litToBoolean){
//      dcache.clock.step(1)
//    }
//    dcache.io.axi.readAddr.ready.poke(true.B)
//    dcache.clock.step(2)
//    dcache.io.axi.readData.valid.poke(true.B)
//    data.indices.foreach( id =>{
//      dcache.io.axi.readData.bits.data.poke(data(id))
//      if(id===3){
//        dcache.io.axi.readData.bits.last.poke(true.B)
//      }
//      dcache.clock.step(1)
//    })
//    dcache.io.axi.readAddr.ready.poke(false.B)
//    dcache.io.axi.readData.bits.data.poke(0.U)
//    dcache.io.axi.readData.bits.last.poke(false.B)
//  }
//  def handle_write(dcache:DCache):Unit={
//
//  }
//  it should "read good" in {
//    /**
//     * 读测试
//     */
//    test(new DCache(new CacheConfig())).withAnnotations(Seq(WriteVcdAnnotation)) { dcache =>
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("h1234".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.valid.poke(false.B)
//      give_data(dcache,Seq("h12345678".U,"h22345678".U,"h32345678".U,"h42345678".U))
//      dcache.clock.step(1)
//      // 测试取一个没有的数据
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("h1235".U)//
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(1.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.rdata.expect("h3456".U)
//
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("h1236".U)//
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(1.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.rdata.expect("h1234".U)
//
//      //插入第二条组相连
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("ha1234".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.valid.poke(false.B)
//      give_data(dcache,Seq("ha2345678".U,"hb2345678".U,"hc2345678".U,"hd2345678".U))
//      // 0xd2345678 0xa2345678 0xb2345678 0xc2345678
//      dcache.io.rdata.expect("ha2345678".U)
//      dcache.clock.step(1)
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("ha1236".U)//
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(1.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.rdata.expect("ha234".U)
//      // 第三条组相连，第一条应该会被清掉
//      dcache.io.addr.poke("hb1234".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.valid.poke(false.B)
//      give_data(dcache,Seq("hba234567".U,"hbb234567".U,"hbc234567".U,"hbd234567".U))
//      dcache.io.rdata.expect("hba234567".U)
//      dcache.clock.step(1)
//
//      // 重取第一条
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("h1234".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.valid.poke(false.B)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.clock.step(2)
//      //      dcache.io.valid.poke(false.B)
//    }
//  }
//  it should "write good" in {
//    test(new DCache(new CacheConfig())).withAnnotations(Seq(WriteVcdAnnotation)) { dcache =>
//      //读一行
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("hb1234".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.valid.poke(false.B)
//      give_data(dcache,Seq("hba234567".U,"hbb234567".U,"hbc234567".U,"hbd234567".U))
//      dcache.io.rdata.expect("hba234567".U)
//      dcache.clock.step(1)
//
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("hb1238".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.valid.poke(false.B)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.rdata.expect("hbb234567".U,"can't read same line data")
//      dcache.clock.step(1)
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("hb1238".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.valid.poke(false.B)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.rdata.expect("hbb234567".U,"can't read same line data")
//      dcache.clock.step(1)
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("h1234".U)
//      dcache.io.wr.poke(true.B)
//      dcache.io.wdata.poke("h87654321".U)
//      dcache.io.wstrb.poke("b1111".U)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.valid.poke(false.B)
//      give_data(dcache,Seq("h12345678".U,"h22345678".U,"h32345678".U,"h42345678".U))
//      dcache.clock.step(1)
//      // 第一个数据会被改为0x87654321
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("h1234".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.valid.poke(false.B)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.rdata.expect("h87654321".U)
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("h1238".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.valid.poke(false.B)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.rdata.expect("h22345678".U,"can't read same line data")
//      dcache.clock.step(1)
//
//      //测试字节使能写入
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("h1234".U)
//      dcache.io.wr.poke(true.B)
//      dcache.io.wdata.poke("h78000000".U)
//      dcache.io.wstrb.poke("b1000".U)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.valid.poke(false.B)
//      dcache.clock.step(1)
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("h1234".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.valid.poke(false.B)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.rdata.expect("h78654321".U,"Write Mask test failed")
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("hb1238".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.valid.poke(false.B)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.rdata.expect("hbb234567".U,"can't read same line data")
//      dcache.clock.step(1)
//      //测试写驱逐
//      dcache.io.valid.poke(true.B)
//      dcache.io.addr.poke("ha1234".U)
//      dcache.io.wr.poke(false.B)
//      dcache.io.size.poke(2.U) //4KB
//      dcache.clock.step(1)
//      dcache.io.addr_ok.expect(true.B)
//      dcache.io.valid.poke(false.B)
//      give_data(dcache,Seq("ha2345678".U,"hb2345678".U,"hc2345678".U,"hd2345678".U))
//      // 0xd2345678 0xa2345678 0xb2345678 0xc2345678
//      dcache.io.rdata.expect("ha2345678".U)
//
//
//    }
//  }
//}
