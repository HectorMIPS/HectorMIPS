package com.github.hectormips.pipeline.issue

import chisel3._
import chisel3.util.Queue
import com.github.hectormips.pipeline.{WithAllowin, WithValid}

class InstBundle extends Bundle {
  // 指令自身
  val inst      : UInt = UInt(32.W)
  // 指令pc
  val pc        : UInt = UInt(32.W)
  // 指令有效flag
  val inst_valid: Bool = Bool()
}

class InstFIFO extends Module {
  class InstFIFOIO extends WithAllowin with WithValid {
    // 剩余可用队列容量
    val spare     : UInt       = Output(UInt(2.W))
    // 输入队列的指令
    val inst_in   : InstBundle = Input(new InstBundle)
    // 入指令写使能
    val inst_in_en: Bool       = Input(Bool())

    // 输出的指令
    val inst_out      : InstBundle = Output(new InstBundle)
    // 输出bundle有效指示
    val inst_out_valid: Bool       = Output(Bool())
    // 指令填充数
    val count         : UInt       = Output(UInt(2.W))
  }

  val io   : InstFIFOIO        = IO(new InstFIFOIO)
  val queue: Queue[InstBundle] = Module(new Queue(new InstBundle, 2))
  queue.io.enq.ready := io.inst_in_en

}
