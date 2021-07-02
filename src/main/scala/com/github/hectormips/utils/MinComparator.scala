package com.github.hectormips.utils

import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

class MinComparator(width: Int, size: Int) extends Module {
  val io: Bundle {
    val in: Vec[UInt]
    val out: UInt
  } = IO(new Bundle {
    val in: Vec[UInt] = Input(Vec(size, UInt(width.W)))
    val out: UInt = Output(UInt(size.W))
  })

  val win: Vec[Vec[Bool]] = Wire(Vec(width, Vec(size, Bool())))

  for (i <- 0 until width) {
    for (j <- 0 until size)  {
      win(i)(j) := io.in(j)(i)
    }
  }

  val flags: Vec[UInt] = Wire(Vec(width, UInt(size.W)))

  when(win(width-1).asUInt().andR()) {
    flags(width-1) := win(width-1).asUInt()
  }.otherwise{
    flags(width-1) := ~win(width-1).asUInt()
  }

  for (i <- width - 2 to 0 by -1) {
    val orI = (~flags(i+1)).asUInt() | win(i).asUInt()
    when(orI.andR()) {
      flags(i) := flags(i + 1)
    }.otherwise{
      flags(i) := ~orI
    }
  }

  io.out := flags(0)
}

object MinComparator extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new MinComparator(8, 8))))
}
