package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.Mux1H
import chisel3.util._


// 通过译码阶段传入的参数
class DecodeExecuteBundle extends WithValid {
  val alu_op_id_ex: AluOp.Type = AluOp()
  // 寄存器堆读端口1 2
  val pc_id_ex    : UInt       = UInt(32.W)
  val sa_32_id_ex : UInt       = UInt(32.W)
  val imm_32_id_ex: UInt       = UInt(32.W)

  val alu_src1_id_ex         : UInt                 = UInt(32.W)
  val alu_src2_id_ex         : UInt                 = UInt(32.W)
  // 直传id_ex_ms
  val mem_en_id_ex           : Bool                 = Bool()
  val mem_wen_id_ex          : Bool                 = Bool()
  val regfile_wsrc_sel_id_ex : Bool                 = Bool()
  val regfile_waddr_sel_id_ex: RegFileWAddrSel.Type = RegFileWAddrSel()
  val inst_rs_id_ex          : UInt                 = UInt(5.W)
  val inst_rd_id_ex          : UInt                 = UInt(5.W)
  val inst_rt_id_ex          : UInt                 = UInt(5.W)
  val regfile_we_id_ex       : Bool                 = Bool()
  val pc_id_ex_debug         : UInt                 = UInt(32.W)
  val mem_wdata_id_ex        : UInt                 = UInt(32.W)

  override def defaults(): Unit = {
    alu_op_id_ex := AluOp.op_add
    pc_id_ex := 0.U
    sa_32_id_ex := 0.U
    imm_32_id_ex := 0.U

    alu_src1_id_ex := 0.U
    alu_src2_id_ex := 0.U
    mem_en_id_ex := 0.B
    mem_wen_id_ex := 0.B
    regfile_wsrc_sel_id_ex := 0.B
    regfile_waddr_sel_id_ex := RegFileWAddrSel.inst_rt

    inst_rs_id_ex := 0.U
    inst_rd_id_ex := 0.U
    inst_rt_id_ex := 0.U
    regfile_we_id_ex := 0.B
    pc_id_ex_debug := 0.U
    mem_wdata_id_ex := 0.U

    super.defaults()
  }
}

class InsExecuteBundle extends WithAllowin {
  val id_ex_in: DecodeExecuteBundle = Input(new DecodeExecuteBundle)

  // 传递给访存的输出
  val ex_ms_out: ExecuteMemoryBundle = Output(new ExecuteMemoryBundle)

  // 传给data ram的使能信号和数据信号
  val mem_en        : Bool            = Output(Bool())
  val mem_wen       : Bool            = Output(Bool())
  val mem_addr      : UInt            = Output(UInt(32.W))
  val mem_wdata     : UInt            = Output(UInt(32.W))
  val valid_lw_ex_id: Bool            = Output(Bool())
  val bypass_ex_id  : BypassMsgBundle = Output(new BypassMsgBundle)

}

class InsExecute extends Module {
  val io     : InsExecuteBundle = IO(new InsExecuteBundle)
  val alu_out: UInt             = Wire(UInt(32.W))
  val src1   : UInt             = Wire(UInt(32.W))
  val src2   : UInt             = Wire(UInt(32.W))
  src1 := io.id_ex_in.alu_src1_id_ex
  src2 := io.id_ex_in.alu_src2_id_ex

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
  io.mem_wdata := io.id_ex_in.mem_wdata_id_ex
  io.mem_en := io.id_ex_in.mem_en_id_ex
  io.mem_wen := io.id_ex_in.mem_wen_id_ex

  val bus_valid: Bool = Wire(Bool())
  bus_valid := io.id_ex_in.bus_valid && !reset.asBool()

  io.ex_ms_out.regfile_wsrc_sel_ex_ms := io.id_ex_in.regfile_wsrc_sel_id_ex
  io.ex_ms_out.regfile_waddr_sel_ex_ms := io.id_ex_in.regfile_waddr_sel_id_ex
  io.ex_ms_out.inst_rd_ex_ms := io.id_ex_in.inst_rd_id_ex
  io.ex_ms_out.inst_rt_ex_ms := io.id_ex_in.inst_rt_id_ex
  io.ex_ms_out.regfile_we_ex_ms := io.id_ex_in.regfile_we_id_ex
  io.ex_ms_out.pc_ex_ms_debug := io.id_ex_in.pc_id_ex_debug
  // 当指令为从内存中取出存放至寄存器堆中时，ex阶段无法得出结果，前递无效
  io.bypass_ex_id.reg_valid := bus_valid && io.id_ex_in.regfile_we_id_ex && !io.id_ex_in.regfile_wsrc_sel_id_ex
  // 写寄存器来源为内存，并且此时ex阶段有效
  io.valid_lw_ex_id := io.id_ex_in.regfile_wsrc_sel_id_ex && io.id_ex_in.regfile_we_id_ex && bus_valid
  io.bypass_ex_id.reg_data := alu_out
  io.bypass_ex_id.reg_addr := Mux1H(Seq(
    (io.id_ex_in.regfile_waddr_sel_id_ex === RegFileWAddrSel.inst_rd) -> io.id_ex_in.inst_rd_id_ex,
    (io.id_ex_in.regfile_waddr_sel_id_ex === RegFileWAddrSel.inst_rt) -> io.id_ex_in.inst_rt_id_ex,
    (io.id_ex_in.regfile_waddr_sel_id_ex === RegFileWAddrSel.const_31) -> 31.U))

  io.this_allowin := io.next_allowin && !reset.asBool()
  io.ex_ms_out.bus_valid := bus_valid
}