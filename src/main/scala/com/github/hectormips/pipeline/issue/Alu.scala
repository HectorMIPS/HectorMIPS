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
  val alu_out          : UInt = Wire(UInt(64.W))
  val div_mult_buffer  : UInt = RegInit(init = 0.U(64.W))
  alu_out := 0.U


  switch(io.in.alu_op) {
    is(AluOp.op_add) {
      alu_out := src1 + src2
    }
    is(AluOp.op_sub) {
      alu_out := src1 - src2
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


  io.out.alu_res := alu_out
  io.out.out_valid := 1.B
}
