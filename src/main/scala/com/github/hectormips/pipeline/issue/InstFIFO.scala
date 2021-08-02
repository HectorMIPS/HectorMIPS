package com.github.hectormips.pipeline.issue

import Chisel.Decoupled
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util.{DecoupledIO, Queue}

class InstFIFOIn extends Bundle {
  val inst_bundle: InstBundle = new InstBundle
  val flush      : Bool       = Bool()
}

class InstFIFOOut extends Bundle {
  val inst_bundle: InstBundle = new InstBundle
  val flush      : Bool       = Bool()
}

// 指令队列
// 用于解决指令供应问题
class InstFIFO(len: Int = 6) extends Module {
  class InstFIFOIO extends Bundle {
    val in : DecoupledIO[InstFIFOIn]  = Flipped(Decoupled(new InstFIFOIn))
    val out: DecoupledIO[InstFIFOOut] = Decoupled(new InstFIFOOut)
  }

  val io: InstFIFOIO = IO(new InstFIFOIO)
  withReset(reset.asBool() || io.in.bits.flush) {
    val queue: DecoupledIO[InstFIFOIn] = Queue(io.in, len)
    io.out <> queue
  }
  io.out.bits.flush := DontCare
}

object InstFIFO extends App {
  (new ChiselStage).emitVerilog(new InstFIFO())
}