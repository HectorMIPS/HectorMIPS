package com.github.hectormips.tomasulo.ex_component

import Chisel.Cat
import chisel3._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.ex_component.operation.{MemoryOp, MultiplierOp}

class MemoryComponent(config: Config) extends Component(config) {

  val memOp    : MemoryOp.Type = MemoryOp(io.in.bits.operation)



  io.in.ready := io.out.ready
  io.out.valid := io.in.ready
}
