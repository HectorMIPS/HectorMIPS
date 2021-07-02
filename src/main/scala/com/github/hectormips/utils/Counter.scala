package com.github.hectormips.utils


import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import scala.math.pow

class Counter(max_value: Int, desc: Boolean = false) extends Module {
  val len: Int = log2Ceil(max_value)

  val io: Bundle {
    val en: Bool
    val value: UInt
  } = IO(new Bundle {
    val en: Bool = Input(Bool())
    val value: UInt = Output(UInt(len.W))
  })

  var value: UInt = Reg(UInt(len.W))
  if (desc) {
    value = RegInit(UInt(len.W), (pow(2, len) - 1).toInt.U)
  } else {
    value = RegInit(UInt(len.W), 0.U)
  }

  when(io.en) {
    if (desc){
      when(value.orR()) {
        value := value - 1.U
      }
    }else{
      when(!value.andR()) {
        value := value + 1.U
      }
    }
  }

  io.value := value
}


object Counter extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new Counter(128, desc = true))))
}
