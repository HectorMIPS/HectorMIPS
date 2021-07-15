package com.github.hectormips.tomasulo.ex_component

import chisel3.util._
import chisel3._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.ex_component.operation.{JumpOp, MultiplierOp}

class JumpComponent(config: Config) extends Component(config) {

  val jumpOp: JumpOp.Type = JumpOp(io.in.bits.operation(MultiplierOp.getWidth - 1, 0))

  val jumpRes: Bool = 1.B
  val valA: SInt = io.in.bits.valA.asSInt()
  val valB: SInt = io.in.bits.valA.asSInt()

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

  val is_jr: Bool = jumpOp === JumpOp.always && !valA.asUInt() === 0.U

  io.in.ready := io.out.ready
  io.out.valid := io.in.valid
  io.out.bits.exceptionFlag := io.in.bits.exceptionFlag

  io.out.bits.is_jump := jumpRes
  io.out.bits.next_pc := Mux(is_jr, io.in.bits.target_pc, valA)

  io.out.bits.pred_success := MuxCase(jumpRes === io.in.bits.predictJump,
    Seq(
      (is_jr, valA.asUInt() === io.in.bits.target_pc)
    )
  )

  io.out.bits.writeLO := 0.B
  io.out.bits.writeHI := 0.B
  io.out.bits.readHI := 0.B
  io.out.bits.readLO := 0.B
  io.out.bits.writeHILO := 0.B
}
