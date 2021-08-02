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
    val queue: Queue[InstBundle] = Module(new Queue(new InstBundle, len))
    queue.io.enq.valid := io.in.valid
    io.in.ready := queue.io.enq.ready && queue.io.count < (len * 3 / 5).U
    queue.io.enq.bits := io.in.bits.inst_bundle
    io.out.bits.inst_bundle := queue.io.deq.bits
    io.out.valid := queue.io.deq.valid
    queue.io.deq.ready := io.out.ready
  }
  io.out.bits.flush := DontCare
}

object InstFIFO extends App {
  (new ChiselStage).emitVerilog(new InstFIFO())
}