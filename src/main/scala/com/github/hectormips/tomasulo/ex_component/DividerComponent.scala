package com.github.hectormips.tomasulo.ex_component

import Chisel.Cat
import chisel3._
import chisel3.util.{MuxCase, RegEnable}
import com.github.hectormips.pipeline.DividerState
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.ex_component.operation.{DividerOp, MultiplierOp}
import com.github.hectormips.tomasulo.ex_component.util.CommonDivider


class DividerComponent(config: Config) extends Component(config) {

  val divOp    : DividerOp.Type = DividerOp(io.in.bits.operation)
  val is_signed: Bool           = divOp === DividerOp.div
  val divider  : CommonDivider  = Module(new CommonDivider)

  divider.io.is_signed := is_signed
  divider.io.dividend := io.in.bits.valA
  divider.io.divisor := io.in.bits.valB

  val divider_state_next: DividerState.Type = Wire(DividerState())
  val divider_state_reg : DividerState.Type = RegEnable(next = divider_state_next, init = DividerState.waiting,
    enable = io.in.ready)
  val allowin           : Bool              = io.out.ready && divider.io.out_valid

  val divider_buffer    : UInt = RegInit(init = 0.U(64.W))
  val divider_buffer_wen: Bool = RegInit(init = 1.B)

  divider_state_next := MuxCase(divider_state_reg, Seq(
    (divider_state_reg === DividerState.waiting && divider_buffer_wen) -> DividerState.inputting,
    (divider_state_reg === DividerState.inputting && divider.io.tready) -> DividerState.handshaking,
    (divider_state_reg === DividerState.handshaking && !divider.io.tready) -> DividerState.calculating,
    divider.io.out_valid -> DividerState.waiting,
  ))
  divider.io.tvalid := divider_state_next === DividerState.inputting ||
    divider_state_next === DividerState.handshaking


  val divRes: UInt = Cat(divider.io.remainder, divider.io.quotient)

  when(divider_buffer_wen && divider.io.out_valid) {
    divider_buffer := divRes
    divider_buffer_wen := 0.B
  }
  when(io.out.ready) {
    divider_buffer_wen := 1.B
  }

  io.out.bits.exceptionFlag := io.in.bits.exceptionFlag
  io.out.bits.rob_target := io.in.bits.dest
  io.out.bits.value := divider_buffer

  io.out.valid := !divider_buffer_wen
  io.in.ready := divider_state_reg === DividerState.waiting && divider_buffer_wen
}
