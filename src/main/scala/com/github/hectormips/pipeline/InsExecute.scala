package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.Mux1H
import chisel3.util._

object AluSrc1Sel extends ChiselEnum {
  val regfile_read1: Type = Value(1.U)
  val pc: Type = Value(2.U)
  val sa_32: Type = Value(4.U) // sa零扩展
}

object AluSrc2Sel extends ChiselEnum {
  val regfile_read2: Type = Value(1.U)
  val imm_32: Type = Value(2.U) // 立即数域符号扩展
  val const_31: Type = Value(4.U)
}

class InsExecuteBundle extends Bundle {
  val alu_op: AluOp.Type = Input(AluOp())
  val alu_src1_sel: AluSrc1Sel.Type = Input(AluSrc1Sel())
  val alu_src2_sel: AluSrc2Sel.Type = Input(AluSrc2Sel())

  val regfile_read1: UInt = Input(UInt(32.W))
  val pc: UInt = Input(UInt(32.W))
  val sa_32: UInt = Input(UInt(32.W))

  val regfile_read2: UInt = Input(UInt(32.W))
  val imm_32: UInt = Input(UInt(32.W))

  val alu_out: UInt = Output(UInt(32.W))

  val mem_addr: UInt = Output(UInt(32.W))
  val mem_wdata: UInt = Output(UInt(32.W))
}

class InsExecute extends Module {
  val io: InsExecuteBundle = IO(new InsExecuteBundle)
  val alu_out: UInt = Wire(UInt(32.W))
  val src1: UInt = Wire(UInt(32.W))
  val src2: UInt = Wire(UInt(32.W))
  src1 := 0.U
  src2 := 0.U
  switch(io.alu_src1_sel){
    is(AluSrc1Sel.pc){
      src1 := io.pc
    }
    is(AluSrc1Sel.sa_32){
      src1 := io.sa_32
    }
    is(AluSrc1Sel.regfile_read1){
      src1 := io.regfile_read1
    }
  }
  switch(io.alu_src2_sel){
    is(AluSrc2Sel.imm_32){
      src2 := io.imm_32
    }
    is(AluSrc2Sel.const_31){
      src2 := 31.U
    }
    is(AluSrc2Sel.regfile_read2){
      src2 := io.regfile_read2
    }
  }
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
  io.mem_addr := src1 + src2 // 直出内存地址，连接到sram上
  io.mem_wdata := src2
}
