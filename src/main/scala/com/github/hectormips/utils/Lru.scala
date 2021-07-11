package com.github.hectormips.utils

import chisel3._
import scala.math.pow
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

class Lru(size: Int) extends Module {
  require(size > 0)
  require(isPow2(size))
  val LEN: Int = log2Ceil(size)
  val WIDTH: Int = size * (size - 1) / 2

  class LruIO extends Bundle {
    // 访问
    val visitor: UInt = Input(UInt(LEN.W))
    val en_visitor: Bool = Input(Bool())

    // 下一个被替换的cell Index
    val next: UInt = Output(UInt(LEN.W))
  }

  val io: LruIO = IO(new LruIO)

  val current: UInt = RegInit(UInt(WIDTH.W), (pow(2, WIDTH) - 1).toInt.U)

  val comb: LruComb = Module(new LruComb(size))
  comb.io.access := io.visitor
  comb.io.current := current

  when(io.en_visitor) {
    current := comb.io.update
  }

  io.next := Mux1H((0 until size).map(i => (comb.io.lru_pre(i) === 1.B, i.U)))
}


object Lru extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new Lru(8))))
}

