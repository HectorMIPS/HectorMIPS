package com.github.hectormips.cache.utils

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.ChiselScalatestTester
import chiseltest.internal.WriteVcdAnnotation
import org.scalatest.{FlatSpec, Matchers}
import chiseltest.experimental.TestOptionBuilder._

class TestWstrb extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "TestWstrb"
  it should "TestWstrb test good" in {
    test(new Wstrb()).withAnnotations(Seq(WriteVcdAnnotation)) { wstrb =>
      wstrb.io.offset.poke(0.U)
      wstrb.io.size.poke(0.U)
      wstrb.io.mask.expect("b0001".U)

      wstrb.io.offset.poke(1.U)
      wstrb.io.size.poke(0.U)
      wstrb.io.mask.expect("b0010".U)

      wstrb.io.offset.poke(2.U)
      wstrb.io.size.poke(0.U)
      wstrb.io.mask.expect("b0100".U)

      wstrb.io.offset.poke(3.U)
      wstrb.io.size.poke(0.U)
      wstrb.io.mask.expect("b1000".U)

      wstrb.io.offset.poke(0.U)
      wstrb.io.size.poke(1.U)
      wstrb.io.mask.expect("b0011".U)

      wstrb.io.offset.poke(2.U)
      wstrb.io.size.poke(1.U)
      wstrb.io.mask.expect("b1100".U)

      wstrb.io.offset.poke(0.U)
      wstrb.io.size.poke(2.U)
      wstrb.io.mask.expect("b1111".U)

      wstrb.io.offset.poke(1.U)
      wstrb.io.size.poke(2.U)
      wstrb.io.mask.expect("b0000".U)
    }
  }
}


//class aaaa extends Module{
//  val io = IO(new Bundle {
//    val in       =  Input(Vec(2,UInt(8.W)))
//    val in_valid =  Input(Vec(2,Bool()))
//    val in_ready =  Output(Vec(2,Bool()))
//    val chosen   =  Output(UInt(1.W))
//    val out      =  Output(UInt(8.W))
//  })
//  val arbiter = Module(new Arbiter(UInt(8.W), 2))
//  arbiter.io.in(0).bits := io.in(0)
//  arbiter.io.in(0).valid := io.in_valid(0)
//  io.in_ready(0) := arbiter.io.in(0).ready
//  arbiter.io.in(1).bits := io.in(1)
//  arbiter.io.in(1).valid := io.in_valid(1)
//  io.in_ready(1) := arbiter.io.in(0).ready
//  arbiter.io.out.ready := true.B
//  io.out := arbiter.io.out.bits
//  io.chosen := arbiter.io.chosen
//}
//
//class Testaaaa extends FlatSpec with ChiselScalatestTester with Matchers {
//  behavior of "aaaa"
//  it should "aaaa test good" in {
//    test(new aaaa()).withAnnotations(Seq(WriteVcdAnnotation)) { a =>
//      a.io.in(0).poke(0.U)
//      a.io.in(1).poke(0xf.U)
////      a.io.in_valid(0).poke(true.B)
//      a.io.in_valid(1).poke(true.B)
//      a.clock.step(1)
//
//    }
//  }
//}
