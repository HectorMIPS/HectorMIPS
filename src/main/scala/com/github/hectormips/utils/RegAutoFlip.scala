package com.github.hectormips.utils

import chisel3._
import com.github.hectormips.pipeline.WithValid


// 双发射准入条件：
// 当使用阶段allowin的时候将两条指令的valid置0
// 当使用阶段allowin并且上个阶段所有有效指令均有效时允许流水线流动
object RegAutoFlip {

  def apply[T <: WithValid](next: T, init: T, this_allowin: Bool): T = {
    val r = RegInit(init)
    when(this_allowin) {
      when(next.bus_valid) {
        r := next
      }.otherwise {
        r.bus_valid := 0.B
      }
    }
    r
  }
}
