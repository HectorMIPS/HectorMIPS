package com.github.hectormips.predict

import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

class BHT extends Module{
  class BHT_IO extends Bundle {
    // visit使能
    val en_visit: Bool = Input(Bool())

    // visit是否命中
    val is_visited: Bool = Input(Bool())

    // 下一次是否命中
    val predict: Bool = Output(Bool())

  }

  val io: BHT_IO = IO(new BHT_IO)

  val record: UInt = RegInit(UInt(2.W), "b01".U)

  when(io.en_visit){
    when(io.is_visited) {
      when(!record.andR()){
        record := record + 1.U
      }
    }.otherwise{
      when(record.orR()){
        record := record - 1.U
      }
    }
  }

  io.predict := record(1) === 1.B
}

object BHT extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new BHT())))
}
