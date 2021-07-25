package com.github.hectormips.pipeline.issue

import chisel3._
import chisel3.util._
import com.github.hectormips.pipeline.{AluOp, CommonDivider, CommonMultiplier, DividerState, MultiplierState}

// 包含乘除法功能的完整alu
class AluEx extends Alu {

  val calc_done          : Bool = RegInit(init = 0.B)
  val divider_required   : Bool = io.in.alu_op === AluOp.op_divu || io.in.alu_op === AluOp.op_div
  val multiplier_required: Bool = io.in.alu_op === AluOp.op_multu || io.in.alu_op === AluOp.op_mult

  val multiplier: CommonMultiplier = Module(new CommonMultiplier)
  multiplier.io.mult1 := src1
  multiplier.io.mult2 := src2
  multiplier.io.req := multiplier_required && io.in.en && multiplier.io.state === MultiplierState.waiting_for_input && !calc_done
  multiplier.io.is_signed := io.in.alu_op === AluOp.op_mult
  multiplier.io.flush := io.in.flush

  val ex_divider_state_next: DividerState.Type = Wire(DividerState())
  val ex_divider_state_reg : DividerState.Type = RegEnable(next = ex_divider_state_next, init = DividerState.waiting,
    enable = divider_required)
  val divider_tvalid       : Bool              = Wire(Bool())
  val divider_tready       : Bool              = Wire(Bool())
  val divider_out_valid    : Bool              = Wire(Bool())
  val divider              : CommonDivider     = Module(new CommonDivider)
  divider.io.divisor := src2
  divider.io.dividend := src1
  divider.io.is_signed := io.in.alu_op === AluOp.op_div
  divider.io.tvalid := divider_tvalid && !calc_done
  divider_tready := divider.io.tready && !calc_done
  divider_out_valid := divider.io.out_valid


  ex_divider_state_next := MuxCase(ex_divider_state_reg, Seq(
    (ex_divider_state_reg === DividerState.waiting && divider_required) -> DividerState.inputting,
    (ex_divider_state_reg === DividerState.inputting && divider_tready) -> DividerState.handshaking,
    (ex_divider_state_reg === DividerState.handshaking && divider_tready) -> DividerState.calculating,
    (ex_divider_state_reg === DividerState.calculating && io.in.ex_allowin) -> DividerState.waiting,
    io.in.en -> DividerState.waiting
  ))
  divider_tvalid := ex_divider_state_next === DividerState.inputting ||
    ex_divider_state_next === DividerState.handshaking

  when(io.in.ex_allowin || io.in.flush) {
    calc_done := 0.B
  }.elsewhen(divider.io.out_valid || multiplier.io.res_valid) {
    calc_done := 1.B
  }

  switch(io.in.alu_op) {
    is(AluOp.op_mult) {
      alu_out := Cat(multiplier.io.mult_res_63_32, multiplier.io.mult_res_31_0)
    }
    is(AluOp.op_multu) {
      alu_out := Cat(multiplier.io.mult_res_63_32, multiplier.io.mult_res_31_0)
    }
    is(AluOp.op_div) {
      alu_out := Cat(divider.io.remainder, divider.io.quotient)
    }
    is(AluOp.op_divu) {
      alu_out := Cat(divider.io.remainder, divider.io.quotient)
    }
  }

  override def isOutValid: Bool = {
    MuxCase(1.B, Seq(
      divider_required -> divider_out_valid,
      multiplier_required -> multiplier.io.res_valid
    ))
  }
}
