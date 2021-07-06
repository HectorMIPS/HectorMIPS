package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.Mux1H
import chisel3.util._

object AluSrc1Sel extends ChiselEnum {
  val regfile_read1: Type = Value(1.U)
  val pc           : Type = Value(2.U)
  val sa_32        : Type = Value(4.U) // sa零扩展
}

object AluSrc2Sel extends ChiselEnum {
  val regfile_read2: Type = Value(1.U)
  val imm_32       : Type = Value(2.U) // 立即数域符号扩展
  val const_31     : Type = Value(4.U)
}

// 通过译码阶段传入的参数
class DecodeExecuteBundle extends WithValid {
  val alu_op_id_ex           : AluOp.Type           = AluOp()
  val alu_src1_sel_id_ex     : AluSrc1Sel.Type      = AluSrc1Sel()
  val alu_src2_sel_id_ex     : AluSrc2Sel.Type      = AluSrc2Sel()
  // 寄存器堆读端口1 2
  val pc_id_ex               : UInt                 = UInt(32.W)
  val sa_32_id_ex            : UInt                 = UInt(32.W)
  val imm_32_id_ex           : UInt                 = UInt(32.W)
  // 直传id_ex_ms
  val mem_en_id_ex           : Bool                 = Bool()
  val mem_wen_id_ex          : Bool                 = Bool()
  val regfile_wsrc_sel_id_ex : Bool                 = Bool()
  val regfile_waddr_sel_id_ex: RegFileWAddrSel.Type = RegFileWAddrSel()
  val inst_rs_id_ex          : UInt                 = UInt(5.W)
  val inst_rd_id_ex          : UInt                 = UInt(5.W)
  val inst_rt_id_ex          : UInt                 = UInt(5.W)
  val regfile_we_id_ex       : Bool                 = Bool()


}

class InsExecuteBundle extends WithAllowin {
  val id_ex_in     : DecodeExecuteBundle = Input(new DecodeExecuteBundle)
  val regfile_read1: UInt                = Input(UInt(32.W))
  val regfile_read2: UInt                = Input(UInt(32.W))

  // 传递给访存的输出
  val ex_ms_out: ExecuteMemoryBundle = Output(new ExecuteMemoryBundle)

  // 传给data ram的使能信号和数据信号
  val mem_en   : Bool = Output(Bool())
  val mem_wen  : Bool = Output(Bool())
  val mem_addr : UInt = Output(UInt(32.W))
  val mem_wdata: UInt = Output(UInt(32.W))
}

class InsExecute extends Module {
  val io     : InsExecuteBundle = IO(new InsExecuteBundle)
  val alu_out: UInt             = Wire(UInt(32.W))
  val src1   : UInt             = Wire(UInt(32.W))
  val src2   : UInt             = Wire(UInt(32.W))
  src1 := 0.U
  src2 := 0.U
  switch(io.id_ex_in.alu_src1_sel_id_ex) {
    is(AluSrc1Sel.pc) {
      src1 := io.id_ex_in.pc_id_ex
    }
    is(AluSrc1Sel.sa_32) {
      src1 := io.id_ex_in.sa_32_id_ex
    }
    is(AluSrc1Sel.regfile_read1) {
      src1 := io.regfile_read1
    }
  }
  switch(io.id_ex_in.alu_src2_sel_id_ex) {
    is(AluSrc2Sel.imm_32) {
      src2 := io.id_ex_in.imm_32_id_ex
    }
    is(AluSrc2Sel.const_31) {
      src2 := 31.U
    }
    is(AluSrc2Sel.regfile_read2) {
      src2 := io.regfile_read2
    }
  }
  alu_out := 0.U
  switch(io.id_ex_in.alu_op_id_ex) {
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
  io.ex_ms_out.alu_val_ex_ms := alu_out
  io.mem_addr := src1 + src2 // 直出内存地址，连接到sram上
  io.mem_wdata := src2
  io.mem_en := io.id_ex_in.mem_en_id_ex
  io.mem_wen := io.id_ex_in.mem_wen_id_ex

  io.ex_ms_out.regfile_wsrc_sel_ex_ms := io.id_ex_in.regfile_wsrc_sel_id_ex
  io.ex_ms_out.regfile_waddr_sel_ex_ms := io.id_ex_in.regfile_waddr_sel_id_ex
  io.ex_ms_out.inst_rd_ex_ms := io.id_ex_in.inst_rd_id_ex
  io.ex_ms_out.inst_rt_ex_ms := io.id_ex_in.inst_rt_id_ex
  io.ex_ms_out.regfile_we_ex_ms := io.id_ex_in.regfile_we_id_ex

  io.this_allowin := io.next_allowin && !reset.asBool()
  io.ex_ms_out.bus_valid := io.id_ex_in.bus_valid && !reset.asBool()
}