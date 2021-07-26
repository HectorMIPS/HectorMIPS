package com.github.hectormips.utils

import chisel3._
import com.github.hectormips.pipeline.{WithVEI, WithValid}


// 双发射准入条件：
// 当使用阶段allowin的时候将两条指令的valid置0
// 当使用阶段allowin并且上个阶段所有有效指令均有效时允许流水线流动
object RegDualAutoFlip {

  def apply[T <: WithVEI](next: Vec[T], init: T, this_allowin: Bool): Vec[T] = {
    val r              : Vec[T] = RegInit(VecInit(Seq.fill(2)(init)))
    val next_both_valid: Bool   = next(0).bus_valid && Mux(next(0).issue_num === 2.U, next(1).bus_valid, 1.B)
    when(this_allowin) {
      when(next_both_valid) {
        r := next
      }.otherwise {
        for (i <- 0 to 1) {
          r(i).bus_valid := 0.B
          r(i).issue_num := 0.B
        }
      }
    }
    r
  }
}
