package com.github.hectormips.tomasulo.ex_component

import chisel3.util._
import chisel3._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.ex_component.operation.{JumpOp, MultiplierOp}

class JumpComponent(config: Config) extends Component(config) {

  val jumpOp: JumpOp.Type = JumpOp(io.in.bits.operation(MultiplierOp.getWidth - 1, 0))

  val jumpRes: Bool = 1.B
  val valA   : SInt = io.in.bits.valA.asSInt()
  val valB   : SInt = io.in.bits.valA.asSInt()
  switch(jumpOp) {
    is(JumpOp.eq) {
      jumpRes := valA === valB
    }
    is(JumpOp.ne) {
      jumpRes := valA =/= valB
    }
    is(JumpOp.ge) {
      jumpRes := valA >= valB
    }
    is(JumpOp.gt) {
      jumpRes := valA > valB
    }
    is(JumpOp.le) {
      jumpRes := valA <= valB
    }
    is(JumpOp.lt) {
      jumpRes := valA < valB
    }
    is(JumpOp.always) {
      jumpRes := 1.B
    }

  }

  io.in.ready := io.out.ready
  io.out.valid := io.in.valid
  io.out.bits.exceptionFlag := io.in.bits.exceptionFlag
  io.out.bits.is_jump := jumpRes
  io.out.bits.pred_success := jumpRes === io.in.bits.predictJump
}
