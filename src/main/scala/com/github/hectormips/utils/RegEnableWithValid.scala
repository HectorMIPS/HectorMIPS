package com.github.hectormips.utils

import chisel3._
import com.github.hectormips.pipeline.WithValid


object RegEnableWithValid {

  def apply[T <: WithValid](next: T, init: T, enable: Bool, valid_enable: Bool): T = {
    val r = RegInit(init)
    when(enable) {
      r := next
    }
    when(valid_enable) {
      r.bus_valid := next.bus_valid
    }
    r
  }
}
