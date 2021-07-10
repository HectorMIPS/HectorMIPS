package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.util._
import AluOp._
import chisel3.experimental.ChiselEnum
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import com.github.hectormips.pipeline

object RegFileWAddrSel extends ChiselEnum {
  val inst_rd : Type = Value(1.U)
  val inst_rt : Type = Value(2.U)
  val const_31: Type = Value(4.U)
}

object AluSrc1Sel extends ChiselEnum {
  val regfile_read1: Type = Value(1.U)
  val pc_delay     : Type = Value(2.U)
  val sa_32        : Type = Value(4.U) // sa零扩展
}

object AluSrc2Sel extends ChiselEnum {
  val regfile_read2: Type = Value(1.U)
  val imm_32       : Type = Value(2.U) // 立即数域符号扩展
  val const_4      : Type = Value(4.U) // 立即数4，用于jal
}

class FetchDecodeBundle extends WithValid {
  val ins_if_id     : UInt = UInt(32.W)
  val pc_if_id      : UInt = UInt(32.W) // 延迟槽pc
  val pc_debug_if_id: UInt = UInt(32.W) // 转移pc

  override def defaults(): Unit = {
    super.defaults()
    ins_if_id := 0.U
    pc_if_id := 0xbfbffffc.S(32.W).asUInt()
    pc_debug_if_id := 0xbfbffffcL.U
  }
}

class BypassMsgBundle extends Bundle {
  val reg_addr : UInt = UInt(5.W)
  val reg_data : UInt = UInt(32.W)
  val reg_valid: Bool = Bool()
}

class DecodeBypassBundle extends Bundle {
  val bp_ex_id      : BypassMsgBundle = new BypassMsgBundle
  // 执行阶段处理的指令是否是lw类指令（同时判断此时ex阶段的lw指令是否有效）
  val valid_lw_ex_id: Bool            = new Bool()
  val bp_ms_id      : BypassMsgBundle = new BypassMsgBundle
  val bp_wb_id      : BypassMsgBundle = new BypassMsgBundle
}

class InsDecodeBundle extends WithAllowin {

  val bypass_bus   : DecodeBypassBundle   = Input(new DecodeBypassBundle)
  val if_id_in     : FetchDecodeBundle    = Input(new FetchDecodeBundle)
  val regfile_read1: UInt                 = Input(UInt(32.W))
  val regfile_read2: UInt                 = Input(UInt(32.W))
  val id_pf_out    : DecodePreFetchBundle = Output(new DecodePreFetchBundle)

  val iram_en     : Bool                = Output(Bool())
  val iram_we     : Bool                = Output(Bool())
  val id_ex_out   : DecodeExecuteBundle = Output(new DecodeExecuteBundle)
  val ins_opcode  : UInt                = Output(UInt(6.W))
  val ex_out_valid: Bool                = Input(Bool())


  val decode_to_fetch_next_pc: Vec[UInt] = Output(Vec(2, UInt(32.W))) // 回馈给取值的pc通路
}

class InsDecode extends Module {
  val io                       : InsDecodeBundle = IO(new InsDecodeBundle)
  val opcode                   : UInt            = io.if_id_in.ins_if_id(31, 26)
  val sa                       : UInt            = io.if_id_in.ins_if_id(10, 6)
  val imm                      : SInt            = Wire(SInt(32.W))
  val func                     : UInt            = io.if_id_in.ins_if_id(5, 0)
  val rs                       : UInt            = io.if_id_in.ins_if_id(25, 21)
  val rt                       : UInt            = io.if_id_in.ins_if_id(20, 16)
  val rd                       : UInt            = io.if_id_in.ins_if_id(15, 11)
  val ins_addu                 : Bool            = opcode === 0.U && sa === 0.U && func === "b100001".U
  val ins_addiu                : Bool            = opcode === "b001001".U
  val ins_subu                 : Bool            = opcode === 0.U && sa === 0.U && func === "b100011".U
  val ins_lw                   : Bool            = opcode === "b100011".U
  val ins_sw                   : Bool            = opcode === "b101011".U
  val ins_beq                  : Bool            = opcode === "b000100".U
  val ins_bne                  : Bool            = opcode === "b000101".U
  val ins_jal                  : Bool            = opcode === "b000011".U
  val ins_jr                   : Bool            = opcode === "b000000".U && rt === "b00000".U && rd === "b00000".U &&
    sa === "b00000".U && func === "b001000".U
  val ins_slt                  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b101010".U
  val ins_sltu                 : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b101011".U
  val ins_sll                  : Bool            = opcode === "b000000".U && rs === "b00000".U && func === "b000000".U
  val ins_srl                  : Bool            = opcode === "b000000".U && rs === "b00000".U && func === "b000010".U
  val ins_sra                  : Bool            = opcode === "b000000".U && rs === "b00000".U && func === "b000011".U
  val ins_lui                  : Bool            = opcode === "b001111".U && rs === "b00000".U
  val ins_and                  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100100".U
  val ins_or                   : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100101".U
  val ins_xor                  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100110".U
  val ins_nor                  : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100111".U
  // 使用sint进行有符号拓展
  val offset                   : SInt            = Wire(SInt(32.W))
  val instr_index              : UInt            = io.if_id_in.ins_if_id(25, 0)
  val ready_go                 : Bool            = Wire(Bool())
  val regfile_read1_with_bypass: UInt            = Wire(UInt(32.W))
  val regfile_read2_with_bypass: UInt            = Wire(UInt(32.W))

  def byPassData(rf_addr: UInt, bypass: BypassMsgBundle): (Bool, UInt) = {
    (bypass.reg_valid && bypass.reg_addr === rf_addr) -> bypass.reg_data
  }

  regfile_read1_with_bypass := Mux(rs === 0.U, 0.U, MuxCase(io.regfile_read1, Seq(
    byPassData(rs, io.bypass_bus.bp_ex_id),
    byPassData(rs, io.bypass_bus.bp_ms_id),
    byPassData(rs, io.bypass_bus.bp_wb_id),
  )))
  regfile_read2_with_bypass := Mux(rt === 0.U, 0.U, MuxCase(io.regfile_read2, Seq(
    byPassData(rt, io.bypass_bus.bp_ex_id),
    byPassData(rt, io.bypass_bus.bp_ms_id),
    byPassData(rt, io.bypass_bus.bp_wb_id),
  )))
  val regfile1_eq_regfile2: Bool = regfile_read1_with_bypass === regfile_read2_with_bypass
  offset := (io.if_id_in.ins_if_id(15, 0) << 2).asSInt()
  imm := io.if_id_in.ins_if_id(15, 0).asSInt()
  io.id_pf_out.jump_sel_id_pf := Mux1H(Seq(
    (ins_addu | ins_addiu | ins_subu | ins_lw | ins_sw |
      ins_slt | ins_sltu | ins_sll | ins_srl | ins_sra | ins_lui | ins_and | ins_or |
      ins_xor | ins_nor) -> InsJumpSel.delay_slot_pc,
    ((ins_beq && regfile_read1_with_bypass === regfile_read2_with_bypass) |
      (ins_bne && regfile_read1_with_bypass =/= regfile_read2_with_bypass)) -> InsJumpSel.pc_add_offset,
    ((ins_beq && regfile_read1_with_bypass =/= regfile_read2_with_bypass) |
      (ins_bne && regfile_read1_with_bypass === regfile_read2_with_bypass)) -> InsJumpSel.delay_slot_pc,
    ins_jal -> InsJumpSel.pc_cat_instr_index,
    ins_jr -> InsJumpSel.regfile_read1
  ))
  // 0: pc=pc+(signed)(offset<<2)
  // 1: pc=pc[31:28]|instr_index<<2
  // 2: regfile_read1(bypass)
  io.id_pf_out.jump_val_id_pf(0) := io.if_id_in.pc_if_id + offset.asUInt()
  io.id_pf_out.jump_val_id_pf(1) := Cat(Seq(io.if_id_in.pc_if_id(31, 28), instr_index, "b00".U(2.W)))
  io.id_pf_out.jump_val_id_pf(2) := regfile_read1_with_bypass
  io.id_pf_out.bus_valid := io.if_id_in.bus_valid

  io.id_pf_out.jump_taken := (ins_beq && regfile1_eq_regfile2) ||
    (ins_bne && !regfile1_eq_regfile2) ||
    ins_jr || ins_jal

  io.iram_en := 1.B
  io.iram_we := 0.B

  val src1_sel: AluSrc1Sel.Type = Wire(AluSrc1Sel())
  val src2_sel: AluSrc2Sel.Type = Wire(AluSrc2Sel())
  val alu_src1: UInt            = Wire(UInt(32.W))
  val alu_src2: UInt            = Wire(UInt(32.W))

  val bp_ex_id_reg_addr   : UInt = io.bypass_bus.bp_ex_id.reg_addr
  val branch_inst_conflict: Bool = io.bypass_bus.valid_lw_ex_id && bp_ex_id_reg_addr =/= 0.U &&
    ((ins_jr && rs === bp_ex_id_reg_addr) ||
      ((ins_beq || ins_bne) && (rs === bp_ex_id_reg_addr || rt === bp_ex_id_reg_addr)))
  val normal_inst_conflict: Bool = io.bypass_bus.valid_lw_ex_id && io.bypass_bus.bp_ex_id.reg_addr =/= 0.U &&
    ((src1_sel === AluSrc1Sel.regfile_read1 && rs === io.bypass_bus.bp_ex_id.reg_addr) ||
      (src2_sel === AluSrc2Sel.regfile_read2 && rt === io.bypass_bus.bp_ex_id.reg_addr)) &&
    // 当ex阶段的输出有效的时候说明lw指令还停留在ex阶段
    io.ex_out_valid

  // 译码输出准备完毕条件：
  //  上一条lw的目的操作数为0
  //  上一条lw不与本指令的操作寄存器相关
  //  如果不满足以上条件，则译码阶段没有准备好
  ready_go := !normal_inst_conflict && !branch_inst_conflict


  src1_sel := Mux1H(Seq(
    (ins_addu | ins_addiu | ins_subu | ins_lw | ins_sw |
      ins_slt | ins_sltu | ins_and | ins_or | ins_xor | ins_nor) -> AluSrc1Sel.regfile_read1,
    ins_jal -> AluSrc1Sel.pc_delay,
    (ins_sll | ins_srl | ins_sra) -> AluSrc1Sel.sa_32
  ))

  src2_sel := Mux1H(Seq(
    (ins_addu | ins_subu | ins_slt | ins_sltu | ins_sll | ins_srl |
      ins_sra | ins_and | ins_or | ins_xor | ins_nor) -> AluSrc2Sel.regfile_read2,
    (ins_addiu | ins_lw | ins_sw | ins_lui) -> AluSrc2Sel.imm_32,
    ins_jal -> AluSrc2Sel.const_4
  ))
  alu_src1 := 0.U
  alu_src2 := 0.U


  switch(src1_sel) {
    is(AluSrc1Sel.pc_delay) {
      alu_src1 := io.if_id_in.pc_if_id
    }
    is(AluSrc1Sel.sa_32) {
      alu_src1 := sa
    }
    is(AluSrc1Sel.regfile_read1) {
      alu_src1 := regfile_read1_with_bypass
    }
  }
  switch(src2_sel) {
    is(AluSrc2Sel.imm_32) {
      alu_src2 := imm.asUInt()
    }
    is(AluSrc2Sel.const_4) {
      alu_src2 := 4.U
    }
    is(AluSrc2Sel.regfile_read2) {
      alu_src2 := regfile_read2_with_bypass
    }
  }
  io.id_ex_out.alu_src1_id_ex := alu_src1
  io.id_ex_out.alu_src2_id_ex := alu_src2
  io.id_ex_out.alu_op_id_ex := Mux1H(Seq(
    (ins_addu | ins_addiu | ins_lw | ins_sw | ins_jal) -> AluOp.op_add,
    ins_subu -> AluOp.op_sub,
    ins_slt -> AluOp.op_slt,
    ins_sltu -> AluOp.op_sltu,
    ins_and -> AluOp.op_and,
    ins_nor -> AluOp.op_nor,
    ins_or -> AluOp.op_or,
    ins_xor -> AluOp.op_xor,
    ins_sll -> AluOp.op_sll,
    ins_srl -> AluOp.op_srl,
    ins_sra -> AluOp.op_sra,
    ins_lui -> AluOp.op_lui
  ))
  io.id_ex_out.regfile_we_id_ex := ins_addu | ins_addiu | ins_subu | ins_lw | ins_jal | ins_slt |
    ins_sltu | ins_sll | ins_srl | ins_sra | ins_lui | ins_and | ins_or | ins_xor | ins_nor
  io.id_ex_out.regfile_waddr_sel_id_ex := Mux1H(Seq(
    (ins_addu | ins_subu | ins_slt | ins_sltu | ins_sll | ins_srl |
      ins_sra | ins_and | ins_or | ins_xor | ins_nor) -> RegFileWAddrSel.inst_rd,
    (ins_addiu | ins_lw | ins_lui) -> RegFileWAddrSel.inst_rt,
    ins_jal -> RegFileWAddrSel.const_31
  ))

  io.ins_opcode := opcode
  io.id_ex_out.inst_rd_id_ex := rd
  io.id_ex_out.inst_rt_id_ex := rt
  io.id_ex_out.inst_rs_id_ex := rs
  io.id_ex_out.sa_32_id_ex := sa
  io.id_ex_out.imm_32_id_ex := imm.asUInt()


  io.id_ex_out.mem_en_id_ex := ins_lw | ins_sw
  io.id_ex_out.mem_wen_id_ex := ins_sw
  io.id_ex_out.regfile_wsrc_sel_id_ex := ins_lw


  io.id_ex_out.pc_id_ex := io.if_id_in.pc_if_id
  io.id_ex_out.pc_id_ex_debug := io.if_id_in.pc_debug_if_id
  io.decode_to_fetch_next_pc(0) := io.if_id_in.pc_if_id + offset.asUInt()
  io.decode_to_fetch_next_pc(1) := Cat(Seq(io.if_id_in.pc_if_id(31, 28), instr_index, "b00".U(2.W)))
  // sw会使用寄存器堆读端口2的数据写入内存
  io.id_ex_out.mem_wdata_id_ex := regfile_read2_with_bypass


  io.this_allowin := io.next_allowin && !reset.asBool() && ready_go
  io.id_ex_out.bus_valid := io.if_id_in.bus_valid && !reset.asBool() && ready_go

  // 只有在分支命令产生冲突时才向预取阶段发送stall
  io.id_pf_out.stall_id_pf := branch_inst_conflict
}

object InsDecode extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new InsDecode())))
}