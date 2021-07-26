package com.github.hectormips.pipeline.issue

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{Cat, MuxCase, RegEnable, is, switch}
import com.github.hectormips.pipeline.{AluOp, CommonDivider, CommonMultiplier, DividerState, MultiplierState}

class AluIn extends Bundle {
  val alu_op    : AluOp.Type = AluOp()
  val src1      : UInt       = UInt(32.W)
  val src2      : UInt       = UInt(32.W)
  val en        : Bool       = Bool()
  val flush     : Bool       = Bool()
  val lo        : UInt       = UInt(32.W)
  val ex_allowin: Bool       = Bool()
}

class AluOut extends Bundle {
  val alu_res      : UInt = UInt(64.W)
  val alu_sum      : UInt = UInt(32.W)
  val overflow_flag: Bool = Bool()
  val out_valid    : Bool = Bool()
}

class AluIO extends Bundle {
  val in : AluIn  = Input(new AluIn)
  val out: AluOut = Output(new AluOut)
}


// 简单alu，不包含乘除法运算功能
class Alu extends Module {


  val io: AluIO = IO(new AluIO)

  val src1             : UInt = io.in.src1
  val src2             : UInt = io.in.src2
  val src_1_e          : UInt = Wire(UInt(33.W))
  val src_2_e          : UInt = Wire(UInt(33.W))
  val alu_out          : UInt = Wire(UInt(64.W))
  val overflow_occurred: Bool = Wire(Bool())
  val div_mult_buffer  : UInt = RegInit(init = 0.U(64.W))
  overflow_occurred := 0.B
  alu_out := 0.U

  src_1_e := Cat(src1(31), src1)
  src_2_e := Cat(src2(31), src2)


  switch(io.in.alu_op) {
    is(AluOp.op_add) {
      val alu_out_e = src_1_e + src_2_e
      overflow_occurred := alu_out_e(32) ^ alu_out_e(31)
      alu_out := alu_out_e(31, 0)
    }
    is(AluOp.op_sub) {
      val src2_neg: UInt = -src2
      src_2_e := Cat(src2_neg(31), src2_neg)
      val alu_out_e = src_1_e + src_2_e
      overflow_occurred := (alu_out_e(32) ^ alu_out_e(31))
      alu_out := alu_out_e(31, 0)
    }
    is(AluOp.op_slt) {
      alu_out := src1.asSInt() < src2.asSInt()
    }
    is(AluOp.op_sltu) {
      alu_out := src1 < src2
    }
    is(AluOp.op_and) {
      alu_out := src1 & src2
    }
    is(AluOp.op_nor) {
      alu_out := ~(src1 | src2)
    }
    is(AluOp.op_or) {
      alu_out := src1 | src2
    }
    is(AluOp.op_xor) {
      alu_out := src1 ^ src2
    }
    is(AluOp.op_sll) {
      alu_out := src2 << src1(4, 0) // 由sa指定位移数（src1）
    }
    is(AluOp.op_srl) {
      alu_out := src2 >> src1(4, 0) // 由sa指定位移位数（src1）
    }
    is(AluOp.op_sra) {
      alu_out := (src2.asSInt() >> src1(4, 0)).asUInt()
    }
    is(AluOp.op_lui) {
      alu_out := src2 << 16.U
    }
  }


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
  val mult_out: UInt = Mux(multiplier.io.res_valid, Cat(multiplier.io.mult_res_63_32, multiplier.io.mult_res_31_0),
    div_mult_buffer)
  val div_out : UInt = Mux(divider.io.out_valid, Cat(divider.io.remainder, divider.io.quotient),
    div_mult_buffer)
  switch(io.in.alu_op) {
    is(AluOp.op_mult) {
      alu_out := mult_out
    }
    is(AluOp.op_multu) {
      alu_out := mult_out
    }
    is(AluOp.op_div) {
      alu_out := div_out
    }
    is(AluOp.op_divu) {
      alu_out := div_out
    }
  }
  when(divider.io.out_valid) {
    div_mult_buffer := Cat(divider.io.remainder, divider.io.quotient)
  }.elsewhen(multiplier.io.res_valid) {
    div_mult_buffer := Cat(multiplier.io.mult_res_63_32, multiplier.io.mult_res_31_0)
  }

  io.out.alu_res := alu_out
  io.out.alu_sum := src1 + src2
  io.out.overflow_flag := overflow_occurred
  io.out.out_valid := MuxCase(1.B, Seq(
    divider_required -> (divider_out_valid || calc_done),
    multiplier_required -> (multiplier.io.res_valid || calc_done)
  ))
}
