package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.util._
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
  val regfile_read2         : Type = Value(1.U)
  val imm_32_signed_extend  : Type = Value(2.U) // 立即数域符号扩展
  val const_4               : Type = Value(4.U) // 立即数4，用于jal
  val imm_32_unsigned_extend: Type = Value(8.U)
}

object HiloSel extends ChiselEnum {
  val hi: Type = Value(1.U)
  val lo: Type = Value(2.U)
}

class FetchDecodeBundle extends WithValidAndException {
  val ins_if_id     : UInt = UInt(32.W)
  val pc_if_id      : UInt = UInt(32.W) // 延迟槽pc
  val pc_debug_if_id: UInt = UInt(32.W) // 转移pc
  val is_delay_slot : Bool = Bool()

  override def defaults(): Unit = {
    super.defaults()
    ins_if_id := 0.U
    pc_if_id := 0xbfbffffc.S(32.W).asUInt()
    pc_debug_if_id := 0xbfbffffcL.U
    is_delay_slot := 0.U
  }
}

class BypassMsgBundle extends Bundle {
  val reg_addr   : UInt = UInt(5.W)
  val reg_data   : UInt = UInt(32.W)
  val bus_valid  : Bool = Bool()
  val data_valid : Bool = Bool()
  // 当需要读的数据来自于cp0时，需要强制强制暂停前面的所有指令
  val force_stall: Bool = Bool()
}

class DecodeBypassBundle extends Bundle {
  val bp_ex_id: BypassMsgBundle = new BypassMsgBundle
  val bp_ms_id: BypassMsgBundle = new BypassMsgBundle
  val bp_wb_id: BypassMsgBundle = new BypassMsgBundle
}

class InsDecodeBundle extends WithAllowin {

  val bypass_bus   : DecodeBypassBundle   = Input(new DecodeBypassBundle)
  val if_id_in     : FetchDecodeBundle    = Input(new FetchDecodeBundle)
  val regfile_read1: UInt                 = Input(UInt(32.W))
  val regfile_read2: UInt                 = Input(UInt(32.W))
  val id_pf_out    : DecodePreFetchBundle = Output(new DecodePreFetchBundle)

  val iram_en            : Bool                = Output(Bool())
  val iram_we            : Bool                = Output(Bool())
  val id_ex_out          : DecodeExecuteBundle = Output(new DecodeExecuteBundle)
  val ins_opcode         : UInt                = Output(UInt(6.W))
  val ex_out_valid       : Bool                = Input(Bool())
  val flush              : Bool                = Input(Bool())
  val is_delay_slot_id_if: Bool                = Output(Bool()) // 处于if阶段的指令是否是延迟槽指令


  val decode_to_fetch_next_pc: Vec[UInt] = Output(Vec(2, UInt(32.W))) // 回馈给取值的pc通路
}

class InsDecode extends Module {
  val io          : InsDecodeBundle = IO(new InsDecodeBundle)
  val opcode      : UInt            = io.if_id_in.ins_if_id(31, 26)
  val sa          : UInt            = io.if_id_in.ins_if_id(10, 6)
  val imm_signed  : SInt            = Wire(SInt(32.W))
  val imm_unsigned: UInt            = io.if_id_in.ins_if_id(15, 0)
  val func        : UInt            = io.if_id_in.ins_if_id(5, 0)
  val rs          : UInt            = io.if_id_in.ins_if_id(25, 21)
  val rt          : UInt            = io.if_id_in.ins_if_id(20, 16)
  val rd          : UInt            = io.if_id_in.ins_if_id(15, 11)
  val ins_addu    : Bool            = opcode === 0.U && sa === 0.U && func === "b100001".U
  val ins_add     : Bool            = opcode === 0x0.U && sa === 0x0.U && func === 0x20.U
  val ins_addiu   : Bool            = opcode === "b001001".U
  val ins_addi    : Bool            = opcode === 0x8.U
  val ins_subu    : Bool            = opcode === 0.U && sa === 0.U && func === "b100011".U
  val ins_sub     : Bool            = opcode === 0.U && sa === 0.U && func === 0x22.U
  val ins_lw      : Bool            = opcode === "b100011".U
  val ins_sw      : Bool            = opcode === "b101011".U
  val ins_beq     : Bool            = opcode === "b000100".U
  val ins_bne     : Bool            = opcode === "b000101".U
  val ins_jal     : Bool            = opcode === "b000011".U
  val ins_jr      : Bool            = opcode === "b000000".U && rt === "b00000".U && rd === "b00000".U &&
    sa === "b00000".U && func === "b001000".U
  val ins_slt     : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b101010".U
  val ins_sltu    : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b101011".U
  val ins_sll     : Bool            = opcode === "b000000".U && rs === "b00000".U && func === "b000000".U
  val ins_srl     : Bool            = opcode === "b000000".U && rs === "b00000".U && func === "b000010".U
  val ins_sra     : Bool            = opcode === "b000000".U && rs === "b00000".U && func === "b000011".U
  val ins_lui     : Bool            = opcode === "b001111".U && rs === "b00000".U
  val ins_and     : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100100".U
  val ins_or      : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100101".U
  val ins_xor     : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100110".U
  val ins_nor     : Bool            = opcode === "b000000".U && sa === "b00000".U && func === "b100111".U
  val ins_slti    : Bool            = opcode === 0xa.U
  val ins_sltiu   : Bool            = opcode === 0xb.U
  val ins_andi    : Bool            = opcode === 0xc.U
  val ins_ori     : Bool            = opcode === 0xd.U
  val ins_xori    : Bool            = opcode === 0xe.U
  val ins_sllv    : Bool            = opcode === 0.U && sa === 0.U && func === 0x04.U
  val ins_srlv    : Bool            = opcode === 0.U && sa === 0.U && func === 0x6.U
  val ins_srav    : Bool            = opcode === 0.U && sa === 0.U && func === 0x7.U
  val ins_mult    : Bool            = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x18.U
  val ins_multu   : Bool            = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x19.U
  val ins_div     : Bool            = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x1a.U
  val ins_divu    : Bool            = opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x1b.U
  val ins_mfhi    : Bool            = opcode === 0.U && rs === 0.U && rt === 0.U && sa === 0.U && func === 0x10.U
  val ins_mflo    : Bool            = opcode === 0.U && rs === 0.U && rt === 0.U && sa === 0.U && func === 0x12.U
  val ins_mthi    : Bool            = opcode === 0.U && rt === 0.U && rd === 0.U && sa === 0.U && func === 0x11.U
  val ins_mtlo    : Bool            = opcode === 0.U && rt === 0.U && rd === 0.U && sa === 0.U && func === 0x13.U
  val ins_bgez    : Bool            = opcode === 1.U && rt === 1.U
  val ins_bgtz    : Bool            = opcode === 7.U && rt === 0.U
  val ins_blez    : Bool            = opcode === 6.U && rt === 0.U
  val ins_bltz    : Bool            = opcode === 1.U && rt === 0.U
  val ins_j       : Bool            = opcode === 2.U
  val ins_bltzal  : Bool            = opcode === 1.U && rt === 0x10.U
  val ins_bgezal  : Bool            = opcode === 1.U && rt === 0x11.U
  val ins_jalr    : Bool            = opcode === 0.U && rt === 0.U && sa === 0.U && func === 0x9.U
  val ins_lb      : Bool            = opcode === 0x20.U
  val ins_lbu     : Bool            = opcode === 0x24.U
  val ins_lh      : Bool            = opcode === 0x21.U
  val ins_lhu     : Bool            = opcode === 0x25.U
  val ins_sb      : Bool            = opcode === 0x28.U
  val ins_sh      : Bool            = opcode === 0x29.U
  val ins_mfc0    : Bool            = opcode === 0x10.U && rs === 0.U && io.if_id_in.ins_if_id(10, 3) === 0.U
  val ins_mtc0    : Bool            = opcode === 0x10.U && rs === 0x04.U && io.if_id_in.ins_if_id(10, 3) === 0.U
  val ins_nop     : Bool            = io.if_id_in.ins_if_id === 0.U
  val ins_syscall : Bool            = opcode === 0.U && func === 0x0c.U
  val ins_break   : Bool            = opcode === 0.U && func === 0x0d.U
  val ins_eret    : Bool            = io.if_id_in.ins_if_id === 0x42000018.U


  // 使用sint进行有符号拓展
  val offset                   : SInt = Wire(SInt(32.W))
  val instr_index              : UInt = io.if_id_in.ins_if_id(25, 0)
  val ready_go                 : Bool = Wire(Bool())
  val regfile_read1_with_bypass: UInt = Wire(UInt(32.W))
  val regfile_read2_with_bypass: UInt = Wire(UInt(32.W))

  def byPassData(rf_addr: UInt, bypass: BypassMsgBundle): (Bool, UInt) = {
    (bypass.bus_valid && bypass.reg_addr === rf_addr) -> bypass.reg_data
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
  val regfile1_gt_0       : Bool = regfile_read1_with_bypass.asSInt() > 0.S
  val regfile1_ge_0       : Bool = regfile_read1_with_bypass.asSInt() >= 0.S
  offset := (io.if_id_in.ins_if_id(15, 0) << 2).asSInt()
  imm_signed := io.if_id_in.ins_if_id(15, 0).asSInt()
  io.id_pf_out.jump_sel_id_pf := Mux1H(Seq(
    (ins_addu | ins_add | ins_addiu | ins_addi | ins_subu | ins_sub | ins_lw | ins_lb |
      ins_lbu | ins_lh | ins_lhu | ins_sw | ins_sh | ins_sb |
      ins_slt | ins_sltu | ins_sll | ins_srl | ins_sra | ins_lui | ins_and | ins_or |
      ins_xor | ins_nor | ins_slti | ins_sltiu | ins_andi | ins_ori | ins_xori | ins_sllv |
      ins_srlv | ins_srav | ins_mult | ins_multu | ins_div | ins_divu | ins_mfhi | ins_mflo |
      ins_mthi | ins_mtlo | ins_mfc0 | ins_mtc0) -> InsJumpSel.delay_slot_pc,
    ((ins_beq && regfile1_eq_regfile2) |
      (ins_bne && !regfile1_eq_regfile2) |
      (ins_bgtz && regfile1_gt_0) |
      ((ins_bgez | ins_bgezal) && regfile1_ge_0) |
      ((ins_bltz | ins_bltzal) && !regfile1_ge_0) |
      (ins_blez && !regfile1_gt_0)) -> InsJumpSel.pc_add_offset,
    (ins_jal | ins_j) -> InsJumpSel.pc_cat_instr_index,
    ((ins_beq && !regfile1_eq_regfile2) |
      (ins_bne && regfile1_eq_regfile2) |
      (ins_bgtz && !regfile1_gt_0) |
      ((ins_bgez | ins_bgezal) && !regfile1_ge_0) |
      ((ins_bltz | ins_bltzal) && regfile1_ge_0) |
      (ins_blez && regfile1_gt_0)) -> InsJumpSel.delay_slot_pc,
    (ins_jr | ins_jalr) -> InsJumpSel.regfile_read1
  ))

  // 0: pc=pc+(signed)(offset<<2)
  // 1: pc=pc[31:28]|instr_index<<2
  // 2: regfile_read1(bypass)
  io.id_pf_out.jump_val_id_pf(0) := io.if_id_in.pc_if_id + offset.asUInt()
  io.id_pf_out.jump_val_id_pf(1) := Cat(Seq(io.if_id_in.pc_if_id(31, 28), instr_index, "b00".U(2.W)))
  io.id_pf_out.jump_val_id_pf(2) := regfile_read1_with_bypass

  io.id_pf_out.jump_taken := (ins_beq && regfile1_eq_regfile2) ||
    (ins_bne && !regfile1_eq_regfile2) ||
    (ins_bgtz && regfile1_gt_0) ||
    ((ins_bgez | ins_bgezal) && regfile1_ge_0) ||
    ((ins_bltz | ins_bltzal) && !regfile1_ge_0) ||
    (ins_blez && !regfile1_gt_0) ||
    ins_jr || ins_jal || ins_j || ins_jalr
  io.is_delay_slot_id_if := ins_beq | ins_bne | ins_bgtz | ins_bgez | ins_bgezal | ins_bltz |
    ins_bltzal | ins_blez | ins_jal | ins_j | ins_bgtz | ins_jr | ins_jalr

  io.iram_en := 1.B
  io.iram_we := 0.B

  val src1_sel: AluSrc1Sel.Type = Wire(AluSrc1Sel())
  val src2_sel: AluSrc2Sel.Type = Wire(AluSrc2Sel())
  val alu_src1: UInt            = Wire(UInt(32.W))
  val alu_src2: UInt            = Wire(UInt(32.W))

  val bp_ex_id_reg_addr : UInt = io.bypass_bus.bp_ex_id.reg_addr
  val branch_inst_hazard: Bool = ((io.bypass_bus.bp_ex_id.bus_valid && !io.bypass_bus.bp_ex_id.data_valid) ||
    io.bypass_bus.bp_ms_id.bus_valid && !io.bypass_bus.bp_ms_id.data_valid) && bp_ex_id_reg_addr =/= 0.U &&
    (((ins_jr || ins_jalr) && rs === bp_ex_id_reg_addr) ||
      ((ins_beq || ins_bne) && (rs === bp_ex_id_reg_addr || rt === bp_ex_id_reg_addr)) ||
      ((ins_bgez || ins_bgtz || ins_blez || ins_bltz || ins_bltzal || ins_bgezal) && (rs === bp_ex_id_reg_addr)))
  val normal_inst_hazard: Bool = ((io.bypass_bus.bp_ex_id.bus_valid && !io.bypass_bus.bp_ex_id.data_valid) ||
    io.bypass_bus.bp_ms_id.bus_valid && !io.bypass_bus.bp_ms_id.data_valid) && io.bypass_bus.bp_ex_id.reg_addr =/= 0.U &&
    ((src1_sel === AluSrc1Sel.regfile_read1 && rs === io.bypass_bus.bp_ex_id.reg_addr) ||
      (src2_sel === AluSrc2Sel.regfile_read2 && rt === io.bypass_bus.bp_ex_id.reg_addr)) &&
    // 当ex阶段的输出有效的时候说明lw指令还停留在ex阶段
    io.ex_out_valid

  // 译码输出准备完毕条件：
  //  上一条lw的目的操作数为0
  //  上一条lw不与本指令的操作寄存器相关
  //  前递路径中有需要从cp0寄存器读入的数据且还没有在wb阶段准备好
  //  如果不满足以上条件，则译码阶段没有准备好

  val jump_use_regfile_read1: Bool = ins_beq | ins_bne | ins_bgez | ins_bgtz |
    ins_blez | ins_bltz | ins_bgezal | ins_bltzal | ins_jr | ins_jalr
  val jump_use_regfile_read2: Bool = ins_beq | ins_bne

  // 由于syscall在exe生效，而mtc0在写回生效，需要等待其先写入结束
  def hasBypassHazard(bypass_addr: UInt): Bool = {
    bypass_addr =/= 0.U && (((src1_sel === AluSrc1Sel.regfile_read1 || jump_use_regfile_read1) &&
      bypass_addr === rs) ||
      ((src2_sel === AluSrc2Sel.regfile_read2 || jump_use_regfile_read2) && bypass_addr === rt))
  }

  val bp_ex_id_stall_required: Bool = hasBypassHazard(io.bypass_bus.bp_ex_id.reg_addr) &&
    (io.bypass_bus.bp_ex_id.bus_valid && (!io.bypass_bus.bp_ex_id.data_valid || io.bypass_bus.bp_ex_id.force_stall))
  val bp_ms_id_stall_required: Bool = hasBypassHazard(io.bypass_bus.bp_ms_id.reg_addr) &&
    (io.bypass_bus.bp_ms_id.bus_valid && (!io.bypass_bus.bp_ms_id.data_valid || io.bypass_bus.bp_ms_id.force_stall))

  ready_go := !normal_inst_hazard && !branch_inst_hazard && (!bp_ex_id_stall_required && !bp_ms_id_stall_required)


  src1_sel := Mux1H(Seq(
    (ins_addu | ins_add | ins_addiu | ins_addi | ins_subu | ins_sub | ins_lw | ins_lb |
      ins_lbu | ins_lh | ins_lhu | ins_sw | ins_sh | ins_sb |
      ins_slt | ins_sltu | ins_and | ins_or | ins_xor | ins_nor | ins_sltu | ins_slti | ins_sltiu |
      ins_andi | ins_ori | ins_xori | ins_sllv | ins_srlv | ins_srav | ins_multu |
      ins_mult | ins_div | ins_divu | ins_mthi | ins_mtlo) -> AluSrc1Sel.regfile_read1,
    (ins_jal | ins_bgezal | ins_bltzal | ins_jalr) -> AluSrc1Sel.pc_delay,
    (ins_sll | ins_srl | ins_sra | ins_mtc0) -> AluSrc1Sel.sa_32,
  ))

  src2_sel := Mux1H(Seq(
    (ins_addu | ins_add | ins_subu | ins_sub | ins_slt | ins_sltu | ins_sll | ins_srl |
      ins_sra | ins_and | ins_or | ins_xor | ins_nor | ins_sllv | ins_srlv | ins_srav |
      ins_mult | ins_multu | ins_div | ins_divu | ins_mtc0) -> AluSrc2Sel.regfile_read2,
    (ins_addiu | ins_addi | ins_lw | ins_lb |
      ins_lbu | ins_lh | ins_lhu | ins_sw | ins_sh | ins_sb | ins_lui | ins_slti |
      ins_sltiu) -> AluSrc2Sel.imm_32_signed_extend,
    (ins_andi | ins_ori | ins_xori) -> AluSrc2Sel.imm_32_unsigned_extend,
    (ins_jal | ins_bgezal | ins_bltzal | ins_jalr) -> AluSrc2Sel.const_4
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
    is(AluSrc2Sel.imm_32_signed_extend) {
      alu_src2 := imm_signed.asUInt()
    }
    is(AluSrc2Sel.const_4) {
      alu_src2 := 4.U
    }
    is(AluSrc2Sel.regfile_read2) {
      alu_src2 := regfile_read2_with_bypass
    }
    is(AluSrc2Sel.imm_32_unsigned_extend) {
      alu_src2 := imm_unsigned
    }
  }
  io.id_ex_out.alu_src1_id_ex := alu_src1
  io.id_ex_out.alu_src2_id_ex := alu_src2
  io.id_ex_out.alu_op_id_ex := Mux1H(Seq(
    (ins_addu | ins_add | ins_addiu | ins_addi | ins_lw | ins_lb |
      ins_lbu | ins_lh | ins_lhu | ins_sw | ins_sh | ins_sb |
      ins_jal | ins_bltzal | ins_bgezal | ins_jalr | ins_mtc0) -> AluOp.op_add,
    (ins_subu | ins_sub) -> AluOp.op_sub,
    (ins_slt | ins_slti) -> AluOp.op_slt,
    (ins_sltu | ins_sltiu) -> AluOp.op_sltu,
    (ins_and | ins_andi) -> AluOp.op_and,
    ins_nor -> AluOp.op_nor,
    (ins_or | ins_ori) -> AluOp.op_or,
    (ins_xor | ins_xori) -> AluOp.op_xor,
    (ins_sll | ins_sllv) -> AluOp.op_sll,
    (ins_srl | ins_srlv) -> AluOp.op_srl,
    (ins_sra | ins_srav) -> AluOp.op_sra,
    ins_lui -> AluOp.op_lui,
    ins_mult -> AluOp.op_mult,
    ins_multu -> AluOp.op_multu,
    ins_div -> AluOp.op_div,
    ins_divu -> AluOp.op_divu
  ))
  io.id_ex_out.regfile_we_id_ex := ins_addu | ins_add | ins_addiu | ins_addi | ins_subu | ins_sub |
    ins_lw | ins_lb |
    ins_lbu | ins_lh | ins_lhu | ins_jal | ins_bgezal | ins_bltzal | ins_slt | ins_sltu | ins_sll | ins_srl | ins_sra | ins_lui | ins_and | ins_or |
    ins_xor | ins_nor | ins_sltiu | ins_slti | ins_andi | ins_ori | ins_xori | ins_sllv | ins_srlv |
    ins_srav | ins_mfhi | ins_mflo | ins_jalr | ins_mfc0
  io.id_ex_out.regfile_waddr_sel_id_ex := Mux1H(Seq(
    (ins_addu | ins_add | ins_subu | ins_sub | ins_slt | ins_sltu | ins_sll | ins_srl |
      ins_sra | ins_and | ins_or | ins_xor | ins_nor | ins_sllv | ins_srlv |
      ins_srav | ins_mfhi | ins_mflo | ins_jalr) -> RegFileWAddrSel.inst_rd,
    (ins_addiu | ins_addi | ins_lw | ins_lb |
      ins_lbu | ins_lh | ins_lhu | ins_lui | ins_slti | ins_sltiu | ins_andi | ins_ori |
      ins_xori | ins_mfc0) -> RegFileWAddrSel.inst_rt,
    (ins_jal | ins_bgezal | ins_bltzal) -> RegFileWAddrSel.const_31
  ))

  io.ins_opcode := opcode
  io.id_ex_out.inst_rd_id_ex := rd
  io.id_ex_out.inst_rt_id_ex := rt
  io.id_ex_out.inst_rs_id_ex := rs
  io.id_ex_out.sa_32_id_ex := sa
  io.id_ex_out.imm_32_id_ex := imm_signed.asUInt()


  io.id_ex_out.mem_en_id_ex := ins_lw | ins_lb |
    ins_lbu | ins_lh | ins_lhu | ins_sw | ins_sh | ins_sb
  io.id_ex_out.mem_wen_id_ex := MuxCase(0.U, Seq(
    ins_sw -> 0xf.U,
    ins_sh -> 0x3.U,
    ins_sb -> 0x1.U
  ))
  io.id_ex_out.regfile_wsrc_sel_id_ex := ins_lw | ins_lb |
    ins_lbu | ins_lh | ins_lhu
  io.id_ex_out.mem_data_sel_id_ex := MuxCase(MemDataSel.word, Seq(
    (ins_lb | ins_lbu | ins_sb) -> MemDataSel.byte,
    (ins_lh | ins_lhu | ins_sh) -> MemDataSel.hword,
    (ins_lw | ins_sw) -> MemDataSel.word
  )) // 正常情况下只在load指令时起效，可以用这个参数来捎带传输load指令的位选择宽度
  io.id_ex_out.mem_rdata_extend_is_signed_id_ex := ins_lb | ins_lh

  io.id_ex_out.hi_wen := ins_multu | ins_mult | ins_div | ins_divu | ins_mthi
  io.id_ex_out.lo_wen := ins_multu | ins_mult | ins_div | ins_divu | ins_mtlo
  io.id_ex_out.hilo_sel := HiloSel.hi
  io.id_ex_out.hilo_sel := Mux1H(Seq(
    (ins_mthi | ins_mfhi) -> HiloSel.hi,
    (ins_mtlo | ins_mflo) -> HiloSel.lo
  ))


  io.id_ex_out.pc_id_ex := io.if_id_in.pc_if_id
  io.id_ex_out.pc_id_ex_debug := io.if_id_in.pc_debug_if_id
  io.decode_to_fetch_next_pc(0) := io.if_id_in.pc_if_id + offset.asUInt()
  io.decode_to_fetch_next_pc(1) := Cat(Seq(io.if_id_in.pc_if_id(31, 28), instr_index, "b00".U(2.W)))
  // sw会使用寄存器堆读端口2的数据写入内存
  io.id_ex_out.mem_wdata_id_ex := MuxCase(regfile_read2_with_bypass, Seq(
    ins_sh -> VecInit(Seq.fill(2)(regfile_read2_with_bypass(15, 0))).asUInt(),
    ins_sb -> VecInit(Seq.fill(4)(regfile_read2_with_bypass(7, 0))).asUInt(),
  ))

  io.this_allowin := io.next_allowin && !reset.asBool() && ready_go
  io.id_ex_out.bus_valid := io.if_id_in.bus_valid && !reset.asBool() && ready_go && !io.flush

  // 只有在分支命令产生冲突时才向预取阶段发送stall
  io.id_pf_out.stall_id_pf := branch_inst_hazard
  // 在指令数据冲突期间，给予pf阶段的选择是无效的
  io.id_pf_out.bus_valid := io.if_id_in.bus_valid && !io.flush && !branch_inst_hazard

  io.id_ex_out.cp0_wen_id_ex := ins_mtc0
  io.id_ex_out.cp0_addr_id_ex := rd
  io.id_ex_out.cp0_sel_id_ex := io.if_id_in.ins_if_id(2, 0)
  io.id_ex_out.regfile_wdata_from_cp0_id_ex := ins_mfc0

  // 相信我 其实我也不想写这么长
  val ins_valid: Bool = ins_addu ||
    ins_add ||
    ins_addiu ||
    ins_addi ||
    ins_subu ||
    ins_sub ||
    ins_lw ||
    ins_sw ||
    ins_beq ||
    ins_bne ||
    ins_jal ||
    ins_jr ||
    ins_slt ||
    ins_sltu ||
    ins_sll ||
    ins_srl ||
    ins_sra ||
    ins_lui ||
    ins_and ||
    ins_or ||
    ins_xor ||
    ins_nor ||
    ins_slti ||
    ins_sltiu ||
    ins_andi ||
    ins_ori ||
    ins_xori ||
    ins_sllv ||
    ins_srlv ||
    ins_srav ||
    ins_mult ||
    ins_multu ||
    ins_div ||
    ins_divu ||
    ins_mfhi ||
    ins_mflo ||
    ins_mthi ||
    ins_mtlo ||
    ins_bgez ||
    ins_bgtz ||
    ins_blez ||
    ins_bltz ||
    ins_j ||
    ins_bltzal ||
    ins_bgezal ||
    ins_jalr ||
    ins_lb ||
    ins_lbu ||
    ins_lh ||
    ins_lhu ||
    ins_sb ||
    ins_sh ||
    ins_mfc0 ||
    ins_mtc0 ||
    ins_nop ||
    ins_syscall ||
    ins_break ||
    ins_eret

  io.id_ex_out.is_delay_slot := io.if_id_in.is_delay_slot
  io.id_ex_out.overflow_detection_en := ins_add | ins_addi | ins_sub
  io.id_ex_out.ins_eret := ins_eret

  io.id_ex_out.exception_flags := io.if_id_in.exception_flags |
    Mux(io.if_id_in.pc_debug_if_id(1, 0) === 0.U, 0.U, ExceptionConst.EXCEPTION_FETCH_ADDR) |
    Mux(ins_valid, 0.U, ExceptionConst.EXCEPTION_RESERVE_INST) |
    Mux(ins_syscall, ExceptionConst.EXCEPTION_SYSCALL, 0.U) |
    Mux(ins_break, ExceptionConst.EXCEPTION_TRAP, 0.U)
}

object InsDecode extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new InsDecode())))
}