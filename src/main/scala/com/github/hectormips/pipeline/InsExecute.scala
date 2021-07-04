package com.github.hectormips.pipeline

import chisel3._
import chisel3.util.Mux1H
import chisel3.util._

class InsExecuteBundle extends Bundle {
  val alu_op: AluOp.Type = Input(AluOp())
  val alu_src1: UInt = Input(UInt(32.W))
  val alu_src2: UInt = Input(UInt(32.W))
  val alu_out: UInt = Output(UInt(32.W))
  val mem_addr: UInt = Output(UInt(32.W))
}

class InsExecute extends Module {
  val io: InsExecuteBundle = IO(new InsExecuteBundle)
  val alu_out: UInt = Wire(UInt(32.W))
  val src1: UInt = Wire(UInt(32.W))
  val src2: UInt = Wire(UInt(32.W))
  src1 := io.alu_src1
  src2 := io.alu_src2
  alu_out := 0.U
  switch(io.alu_op) {
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
  io.alu_out := alu_out
  io.mem_addr := src1 + src2
}
