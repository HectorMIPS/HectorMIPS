package com.github.hectormips.utils

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

class Lru(size: Int, max_value:Int) extends Module{
  class LruIO(size:Int, len:Int) extends Bundle {
    //整体使能
    val en: Bool = Input(Bool())

    // 是否可用
    val valid: Vec[Bool] = Input(Vec(size, Bool()))

    // 访问
    val visitor: UInt = Input(UInt(len.W))
    val en_visitor: Bool = Input(Bool())

    // 下一个被替换的cell Index
    val next: UInt = Output(UInt(len.W))
  }

  val len: Int = log2Ceil(size)
  val true_size: Int = 2^len

  val counter_len: Int = log2Ceil(max_value)
  val io:LruIO = IO(new LruIO(size, len))

  // size 个计数器
  val counters: Vec[UInt] = Wire(Vec(size, UInt(counter_len.W)))

  for (i <- 0 until size) {
    withReset(reset.asBool() | (io.visitor === i.U & io.en_visitor)){
      val counter = Module(new Counter(max_value, desc = true))
      counter.io.en := io.en
      counters(i) := counter.io.value
    }
  }

  // 比较器
  val comparator: MinComparator = Module(new MinComparator(counter_len, size))
  comparator.io.in := counters

  val mask :UInt = Wire(UInt(size.W))
  mask :=  comparator.io.out | (~io.valid.asUInt()).asUInt()

  io.next := MuxCase(0.U, (0 until size).map( i => (mask(i) === 1.B, i.U)) )
}


object Lru extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new Lru(8, 128))))
}

