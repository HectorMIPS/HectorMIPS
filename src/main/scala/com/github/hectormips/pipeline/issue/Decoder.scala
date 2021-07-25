package com.github.hectormips.pipeline.issue

import chisel3.{Bundle, _}
import chisel3.util._
import com.github.hectormips.pipeline.{AluOp, AluSrc1Sel, AluSrc2Sel, BypassMsgBundle, DecodeBypassBundle, ExceptionConst, HiloSel, InsJumpSel, MemDataSel, RegFileWAddrSel}

class DecoderIn extends Bundle {
  val instruction  : UInt               = UInt(32.W)
  val bypass_bus   : DecodeBypassBundle = new DecodeBypassBundle
  val regfile_read1: UInt               = UInt(32.W)
  val regfile_read2: UInt               = UInt(32.W)
  val pc_debug     : UInt               = UInt(32.W)
  val is_delay_slot: Bool               = Bool()
  val ins_valid    : Bool               = Bool()
}

class DecoderRegularOut extends Bundle {
  val alu_op                    : AluOp.Type           = AluOp()
  val pc_delay_slot             : UInt                 = UInt(32.W)
  val sa_32                     : UInt                 = UInt(32.W)
  val imm_32                    : UInt                 = UInt(32.W)
  val alu_src1                  : UInt                 = UInt(32.W)
  val alu_src2                  : UInt                 = UInt(32.W)
  val mem_en                    : Bool                 = Bool()
  val mem_wen                   : Bool                 = Bool()
  val regfile_wsrc_sel          : Bool                 = Bool()
  val regfile_waddr_sel         : RegFileWAddrSel.Type = RegFileWAddrSel()
  val rs                        : UInt                 = UInt(5.W)
  val rd                        : UInt                 = UInt(5.W)
  val rt                        : UInt                 = UInt(5.W)
  val regfile_we                : Bool                 = Bool()
  val pc_debug                  : UInt                 = UInt(32.W)
  val mem_wdata                 : UInt                 = UInt(32.W)
  val hi_wen                    : Bool                 = Bool()
  val lo_wen                    : Bool                 = Bool()
  val hilo_sel                  : HiloSel.Type         = HiloSel()
  val mem_data_sel              : MemDataSel.Type      = MemDataSel() // 假设数据已经将指定地址对齐到最低位
  val mem_rdata_extend_is_signed: Bool                 = Bool()
  val cp0_wen                   : Bool                 = Bool()
  val cp0_addr                  : UInt                 = UInt(5.W)
  val cp0_sel                   : UInt                 = UInt(3.W)
  val regfile_wdata_from_cp0    : Bool                 = Bool()
  val overflow_detection_en     : Bool                 = Bool()
  val ins_eret                  : Bool                 = Bool()
  val is_delay_slot             : Bool                 = Bool()
  val exception_flags           : UInt                 = UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W)
  val ins_valid                 : Bool                 = Bool()
  val src_use_hilo              : Bool                 = Bool()
}

class DecoderBranchOut extends Bundle {
  val jump_sel  : InsJumpSel.Type = InsJumpSel()
  val jump_val  : Vec[UInt]       = Vec(3, UInt(32.W))
  val is_jump   : Bool            = Bool()
  val jump_taken: Bool            = Bool()
}

class DecoderHazardOut extends Bundle {
  // load冲突是由写寄存器堆数据来源于cp0或者是内存引起的
  val load_to_branch : Bool = Bool()
  val load_to_regular: Bool = Bool()
}

class DecoderIssueOut extends Bundle {
  val rf_wen    : Bool = Bool()
  val rf_wnum   : UInt = UInt(5.W)
  val op1_rf_num: UInt = UInt(5.W)
  val op2_rf_num: UInt = UInt(5.W)

  val cp0_wen     : Bool = Bool()
  val cp0_addr    : UInt = UInt(8.W)
  val op2_from_cp0: Bool = Bool()

  val hilo_sel     : HiloSel.Type = HiloSel()
  val hilo_wen     : Bool         = Bool()
  val op2_from_hilo: Bool         = Bool()
  val is_jump      : Bool         = Bool()
  val div_or_mult  : Bool         = Bool()
  val is_valid     : Bool         = Bool()
  val is_eret      : Bool         = Bool()
}

class Decoder extends Module {

  class DecoderIO extends Bundle {
    val in         : DecoderIn         = Input(new DecoderIn)
    val out_regular: DecoderRegularOut = Output(new DecoderRegularOut)
    val out_branch : DecoderBranchOut  = Output(new DecoderBranchOut)
    val out_hazard : DecoderHazardOut  = Output(new DecoderHazardOut)
    val out_issue  : DecoderIssueOut   = Output(new DecoderIssueOut)
  }

  val io: DecoderIO = IO(new DecoderIO)

  val opcode       : UInt = UInt(6.W)
  val sa           : UInt = UInt(5.W)
  val imm_signed   : SInt = SInt(32.W)
  val imm_unsigned : UInt = UInt(16.W)
  val func         : UInt = UInt(6.W)
  val rs           : UInt = UInt(5.W)
  val rt           : UInt = UInt(5.W)
  val rd           : UInt = UInt(5.W)
  val ins_addu     : Bool = Bool()
  val ins_add      : Bool = Bool()
  val ins_addiu    : Bool = Bool()
  val ins_addi     : Bool = Bool()
  val ins_subu     : Bool = Bool()
  val ins_sub      : Bool = Bool()
  val ins_lw       : Bool = Bool()
  val ins_sw       : Bool = Bool()
  val ins_beq      : Bool = Bool()
  val ins_bne      : Bool = Bool()
  val ins_jal      : Bool = Bool()
  val ins_jr       : Bool = Bool()
  val ins_slt      : Bool = Bool()
  val ins_sltu     : Bool = Bool()
  val ins_sll      : Bool = Bool()
  val ins_srl      : Bool = Bool()
  val ins_sra      : Bool = Bool()
  val ins_lui      : Bool = Bool()
  val ins_and      : Bool = Bool()
  val ins_or       : Bool = Bool()
  val ins_xor      : Bool = Bool()
  val ins_nor      : Bool = Bool()
  val ins_slti     : Bool = Bool()
  val ins_sltiu    : Bool = Bool()
  val ins_andi     : Bool = Bool()
  val ins_ori      : Bool = Bool()
  val ins_xori     : Bool = Bool()
  val ins_sllv     : Bool = Bool()
  val ins_srlv     : Bool = Bool()
  val ins_srav     : Bool = Bool()
  val ins_mult     : Bool = Bool()
  val ins_multu    : Bool = Bool()
  val ins_mul      : Bool = Bool()
  val ins_div      : Bool = Bool()
  val ins_divu     : Bool = Bool()
  val ins_mfhi     : Bool = Bool()
  val ins_mflo     : Bool = Bool()
  val ins_mthi     : Bool = Bool()
  val ins_mtlo     : Bool = Bool()
  val ins_bgez     : Bool = Bool()
  val ins_bgtz     : Bool = Bool()
  val ins_blez     : Bool = Bool()
  val ins_bltz     : Bool = Bool()
  val ins_j        : Bool = Bool()
  val ins_bltzal   : Bool = Bool()
  val ins_bgezal   : Bool = Bool()
  val ins_jalr     : Bool = Bool()
  val ins_lb       : Bool = Bool()
  val ins_lbu      : Bool = Bool()
  val ins_lh       : Bool = Bool()
  val ins_lhu      : Bool = Bool()
  val ins_sb       : Bool = Bool()
  val ins_sh       : Bool = Bool()
  val ins_mfc0     : Bool = Bool()
  val ins_mtc0     : Bool = Bool()
  val ins_nop      : Bool = Bool()
  val ins_syscall  : Bool = Bool()
  val ins_break    : Bool = Bool()
  val ins_eret     : Bool = Bool()
  val offset       : SInt = SInt(32.W)
  val instr_index  : UInt = UInt(26.W)
  val instruction  : UInt = io.in.instruction
  val pc           : UInt = pc
  val pc_delay_slot: UInt = pc + 4.U

  opcode := instruction(31, 26)
  imm_signed := Cat(VecInit(Seq.fill(16)(instruction(15))).asUInt(), instruction(15, 0))
  sa := instruction(10, 6)
  imm_unsigned := instruction(15, 0)
  func := instruction(5, 0)
  rs := instruction(25, 21)
  rt := instruction(20, 16)
  rd := instruction(15, 11)
  ins_addu := opcode === 0.U && sa === 0.U && func === "b100001".U
  ins_add := opcode === 0x0.U && sa === 0x0.U && func === 0x20.U
  ins_addiu := opcode === "b001001".U
  ins_addi := opcode === 0x8.U
  ins_subu := opcode === 0.U && sa === 0.U && func === "b100011".U
  ins_sub := opcode === 0.U && sa === 0.U && func === 0x22.U
  ins_lw := opcode === "b100011".U
  ins_sw := opcode === "b101011".U
  ins_beq := opcode === "b000100".U
  ins_bne := opcode === "b000101".U
  ins_jal := opcode === "b000011".U
  ins_jr := opcode === "b000000".U && rt === "b00000".U && rd === "b00000".U && sa === 0.U && func === 0x08.U
  ins_slt := opcode === "b000000".U && sa === "b00000".U && func === "b101010".U
  ins_sltu := opcode === "b000000".U && sa === "b00000".U && func === "b101011".U
  ins_sll := opcode === "b000000".U && rs === "b00000".U && func === "b000000".U
  ins_srl := opcode === "b000000".U && rs === "b00000".U && func === "b000010".U
  ins_sra := opcode === "b000000".U && rs === "b00000".U && func === "b000011".U
  ins_lui := opcode === "b001111".U && rs === "b00000".U
  ins_and := opcode === "b000000".U && sa === "b00000".U && func === "b100100".U
  ins_or := opcode === "b000000".U && sa === "b00000".U && func === "b100101".U
  ins_xor := opcode === "b000000".U && sa === "b00000".U && func === "b100110".U
  ins_nor := opcode === "b000000".U && sa === "b00000".U && func === "b100111".U
  ins_slti := opcode === 0xa.U
  ins_sltiu := opcode === 0xb.U
  ins_andi := opcode === 0xc.U
  ins_ori := opcode === 0xd.U
  ins_xori := opcode === 0xe.U
  ins_sllv := opcode === 0.U && sa === 0.U && func === 0x04.U
  ins_srlv := opcode === 0.U && sa === 0.U && func === 0x6.U
  ins_srav := opcode === 0.U && sa === 0.U && func === 0x7.U
  ins_mult := opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x18.U
  ins_multu := opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x19.U
  ins_mul := opcode === 0x1c.U && sa === 0.U && func === 0x2.U
  ins_div := opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x1a.U
  ins_divu := opcode === 0.U && rd === 0.U && sa === 0.U && func === 0x1b.U
  ins_mfhi := opcode === 0.U && rs === 0.U && rt === 0.U && sa === 0.U && func === 0x10.U
  ins_mflo := opcode === 0.U && rs === 0.U && rt === 0.U && sa === 0.U && func === 0x12.U
  ins_mthi := opcode === 0.U && rt === 0.U && rd === 0.U && sa === 0.U && func === 0x11.U
  ins_mtlo := opcode === 0.U && rt === 0.U && rd === 0.U && sa === 0.U && func === 0x13.U
  ins_bgez := opcode === 1.U && rt === 1.U
  ins_bgtz := opcode === 7.U && rt === 0.U
  ins_blez := opcode === 6.U && rt === 0.U
  ins_bltz := opcode === 1.U && rt === 0.U
  ins_j := opcode === 2.U
  ins_bltzal := opcode === 1.U && rt === 0x10.U
  ins_bgezal := opcode === 1.U && rt === 0x11.U
  ins_jalr := opcode === 0.U && rt === 0.U && sa === 0.U && func === 0x9.U
  ins_lb := opcode === 0x20.U
  ins_lbu := opcode === 0x24.U
  ins_lh := opcode === 0x21.U
  ins_lhu := opcode === 0x25.U
  ins_sb := opcode === 0x28.U
  ins_sh := opcode === 0x29.U
  ins_mfc0 := opcode === 0x10.U && rs === 0.U && instruction(10, 3) === 0.U
  ins_mtc0 := opcode === 0x10.U && rs === 0x04.U && instruction(10, 3) === 0.U
  ins_nop := instruction === 0.U
  ins_syscall := opcode === 0.U && func === 0x0c.U
  ins_break := opcode === 0.U && func === 0x0d.U
  ins_eret := instruction === 0x42000018.U
  offset := Cat(VecInit(Seq.fill(14)(instruction(15))).asUInt(), instruction(15, 0), 0.U(2.W))
  instr_index := instruction(25, 0)

  val regfile_read1_with_bypass: UInt = Wire(UInt(32.W))
  val regfile_read2_with_bypass: UInt = Wire(UInt(32.W))

  def bypassDataPair(rf_addr: UInt, bypass: BypassMsgBundle): (Bool, UInt) = {
    (bypass.bus_valid && bypass.data_valid && bypass.reg_addr === rf_addr) -> bypass.reg_data
  }

  def regfileReadGen(rf_addr: UInt, rf_read: UInt): UInt = {
    Mux(rf_addr === 0.U, 0.U, MuxCase(rf_read, Seq(
      bypassDataPair(rf_addr, io.in.bypass_bus.bp_ex_id(1)),
      bypassDataPair(rf_addr, io.in.bypass_bus.bp_ex_id(0)),
      bypassDataPair(rf_addr, io.in.bypass_bus.bp_ms_id(1)),
      bypassDataPair(rf_addr, io.in.bypass_bus.bp_ms_id(0)),
      bypassDataPair(rf_addr, io.in.bypass_bus.bp_wb_id(1)),
      bypassDataPair(rf_addr, io.in.bypass_bus.bp_wb_id(0)),
    )))
  }

  regfile_read1_with_bypass := regfileReadGen(rs, io.in.regfile_read1)
  regfile_read2_with_bypass := regfileReadGen(rt, io.in.regfile_read2)
  val regfile1_eq_regfile2: Bool = regfile_read1_with_bypass === regfile_read2_with_bypass
  val regfile1_gt_0       : Bool = regfile_read1_with_bypass.asSInt() > 0.S
  val regfile1_ge_0       : Bool = regfile_read1_with_bypass.asSInt() >= 0.S
  io.out_branch.jump_sel := MuxCase(InsJumpSel.nop, Seq(
    (ins_addu | ins_add | ins_addiu | ins_addi | ins_subu | ins_sub | ins_lw | ins_lb |
      ins_lbu | ins_lh | ins_lhu | ins_sw | ins_sh | ins_sb |
      ins_slt | ins_sltu | ins_sll | ins_srl | ins_sra | ins_lui | ins_and | ins_or |
      ins_xor | ins_nor | ins_slti | ins_sltiu | ins_andi | ins_ori | ins_xori | ins_sllv |
      ins_srlv | ins_srav | ins_mult | ins_multu | ins_mul | ins_div | ins_divu | ins_mfhi | ins_mflo |
      ins_mthi | ins_mtlo | ins_mfc0 | ins_mtc0) -> InsJumpSel.seq_pc,
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
      (ins_blez && regfile1_gt_0)) -> InsJumpSel.seq_pc,
    (ins_jr | ins_jalr) -> InsJumpSel.regfile_read1
  ))
  val is_jump: Bool = ins_beq | ins_bne | ins_bgez | ins_bgtz | ins_blez | ins_bltz |
    ins_bgezal | ins_bltzal | ins_j | ins_jal | ins_jr | ins_jalr
  io.out_branch.is_jump := is_jump


  // 0: pc=pc+(signed)(offset<<2)
  // 1: pc=pc[31:28]|instr_index<<2
  // 2: regfile_read1(bypass)
  io.out_branch.jump_val(0) := pc_delay_slot + offset.asUInt()
  io.out_branch.jump_val(1) := Cat(Seq(pc_delay_slot(31, 28), instr_index, "b00".U(2.W)))
  io.out_branch.jump_val(2) := regfile_read1_with_bypass

  io.out_branch.jump_taken := (ins_beq && regfile1_eq_regfile2) ||
    (ins_bne && !regfile1_eq_regfile2) ||
    (ins_bgtz && regfile1_gt_0) ||
    ((ins_bgez | ins_bgezal) && regfile1_ge_0) ||
    ((ins_bltz | ins_bltzal) && !regfile1_ge_0) ||
    (ins_blez && !regfile1_gt_0) ||
    ins_jr || ins_jal || ins_j || ins_jalr

  val src1_sel: AluSrc1Sel.Type = Wire(AluSrc1Sel())
  val src2_sel: AluSrc2Sel.Type = Wire(AluSrc2Sel())
  val alu_src1: UInt            = Wire(UInt(32.W))
  val alu_src2: UInt            = Wire(UInt(32.W))


  // 前递数据有效但是尚未准备完成
  def hasValidBypassButNotReady(bypass: Vec[BypassMsgBundle]): Bool = {
    (bypass(1).reg_addr =/= 0.U && bypass(1).bus_valid && !bypass(1).data_valid) ||
      (bypass(1).reg_addr =/= 0.U && bypass(0).bus_valid && !bypass(0).data_valid)
  }

  val ex_id_hazard_but_not_ready: Bool = hasValidBypassButNotReady(io.in.bypass_bus.bp_ex_id)
  val ms_id_hazard_but_not_ready: Bool = hasValidBypassButNotReady(io.in.bypass_bus.bp_ms_id)

  def hasBranchHazard(bus_hazard_but_not_ready: Bool, regaddr: UInt): Bool = {
    bus_hazard_but_not_ready &&
      (((ins_jr || ins_jalr) && (rs === regaddr)) ||
        ((ins_beq || ins_bne) && (rs === regaddr || rt === regaddr)) ||
        ((ins_bgez || ins_bgtz || ins_blez || ins_bltz || ins_bltzal || ins_bgezal) && (rs === regaddr)))
  }

  // 当前递路径中的任意一条指令没有准备完成并且与当前decoder中的指令有冲突的时候，都需要暂停
  val load_branch_hazard: Bool = hasBranchHazard(ex_id_hazard_but_not_ready, io.in.bypass_bus.bp_ex_id(0).reg_addr) ||
    hasBranchHazard(ex_id_hazard_but_not_ready, io.in.bypass_bus.bp_ex_id(1).reg_addr) ||
    hasBranchHazard(ms_id_hazard_but_not_ready, io.in.bypass_bus.bp_ms_id(0).reg_addr) ||
    hasBranchHazard(ms_id_hazard_but_not_ready, io.in.bypass_bus.bp_ms_id(1).reg_addr)

  def hasRegularHazard(bus_hazard_but_not_ready: Bool, regaddr: UInt): Bool = {
    bus_hazard_but_not_ready &&
      ((src1_sel === AluSrc1Sel.regfile_read1 && rs === regaddr) ||
        (src2_sel === AluSrc2Sel.regfile_read2 && rt === regaddr))
  }

  val load_regular_hazard: Bool = hasRegularHazard(ex_id_hazard_but_not_ready, io.in.bypass_bus.bp_ex_id(0).reg_addr) ||
    hasRegularHazard(ex_id_hazard_but_not_ready, io.in.bypass_bus.bp_ex_id(1).reg_addr) ||
    hasRegularHazard(ms_id_hazard_but_not_ready, io.in.bypass_bus.bp_ms_id(0).reg_addr) ||
    hasRegularHazard(ms_id_hazard_but_not_ready, io.in.bypass_bus.bp_ms_id(1).reg_addr)

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

  io.out_hazard.load_to_branch := load_branch_hazard
  io.out_hazard.load_to_regular := load_regular_hazard


  src1_sel := MuxCase(AluSrc1Sel.nop, Seq(
    (ins_addu | ins_add | ins_addiu | ins_addi | ins_subu | ins_sub | ins_lw | ins_lb |
      ins_lbu | ins_lh | ins_lhu | ins_sw | ins_sh | ins_sb |
      ins_slt | ins_sltu | ins_and | ins_or | ins_xor | ins_nor | ins_sltu | ins_slti | ins_sltiu |
      ins_andi | ins_ori | ins_xori | ins_sllv | ins_srlv | ins_srav | ins_multu | ins_mul |
      ins_mult | ins_div | ins_divu | ins_mthi | ins_mtlo) -> AluSrc1Sel.regfile_read1,
    (ins_jal | ins_bgezal | ins_bltzal | ins_jalr) -> AluSrc1Sel.pc_delay,
    (ins_sll | ins_srl | ins_sra | ins_mtc0) -> AluSrc1Sel.sa_32,
  ))

  src2_sel := MuxCase(AluSrc2Sel.nop, Seq(
    (ins_addu | ins_add | ins_subu | ins_sub | ins_slt | ins_sltu | ins_sll | ins_srl |
      ins_sra | ins_and | ins_or | ins_xor | ins_nor | ins_sllv | ins_srlv | ins_srav |
      ins_mult | ins_multu | ins_mul | ins_div | ins_divu | ins_mtc0) -> AluSrc2Sel.regfile_read2,
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
      alu_src1 := pc_delay_slot
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
  io.out_regular.alu_src1 := alu_src1
  io.out_regular.alu_src2 := alu_src2
  io.out_regular.alu_op := MuxCase(AluOp.nop, Seq(
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
    (ins_mult | ins_mul) -> AluOp.op_mult,
    ins_multu -> AluOp.op_multu,
    ins_div -> AluOp.op_div,
    ins_divu -> AluOp.op_divu
  ))
  val rf_wen     : Bool                 = ins_addu | ins_add | ins_addiu | ins_addi | ins_subu | ins_sub |
    ins_lw | ins_lb | ins_mul |
    ins_lbu | ins_lh | ins_lhu | ins_jal | ins_bgezal | ins_bltzal | ins_slt | ins_sltu | ins_sll | ins_srl | ins_sra |
    ins_lui | ins_and | ins_or |
    ins_xor | ins_nor | ins_sltiu | ins_slti | ins_andi | ins_ori | ins_xori | ins_sllv | ins_srlv |
    ins_srav | ins_mfhi | ins_mflo | ins_jalr | ins_mfc0
  val rf_wdst_sel: RegFileWAddrSel.Type = MuxCase(RegFileWAddrSel.nop, Seq(
    (ins_addu | ins_add | ins_subu | ins_sub | ins_slt | ins_sltu | ins_sll | ins_srl |
      ins_sra | ins_and | ins_or | ins_xor | ins_nor | ins_sllv | ins_srlv |
      ins_srav | ins_mfhi | ins_mflo | ins_jalr | ins_mul) -> RegFileWAddrSel.inst_rd,
    (ins_addiu | ins_addi | ins_lw | ins_lb |
      ins_lbu | ins_lh | ins_lhu | ins_lui | ins_slti | ins_sltiu | ins_andi | ins_ori |
      ins_xori | ins_mfc0) -> RegFileWAddrSel.inst_rt,
    (ins_jal | ins_bgezal | ins_bltzal) -> RegFileWAddrSel.const_31
  ))
  io.out_regular.regfile_we := rf_wen
  io.out_regular.regfile_waddr_sel := rf_wdst_sel

  io.out_regular.rd := rd
  io.out_regular.rt := rt
  io.out_regular.rs := rs
  io.out_regular.sa_32 := sa
  io.out_regular.imm_32 := imm_signed.asUInt()


  io.out_regular.mem_en := ins_lw | ins_lb |
    ins_lbu | ins_lh | ins_lhu | ins_sw | ins_sh | ins_sb
  io.out_regular.mem_wen := ins_sw | ins_sh | ins_sb
  io.out_regular.regfile_wsrc_sel := ins_lw | ins_lb |
    ins_lbu | ins_lh | ins_lhu
  io.out_regular.mem_data_sel := MuxCase(MemDataSel.word, Seq(
    (ins_lb | ins_lbu | ins_sb) -> MemDataSel.byte,
    (ins_lh | ins_lhu | ins_sh) -> MemDataSel.hword,
    (ins_lw | ins_sw) -> MemDataSel.word
  )) // 正常情况下只在load指令时起效，可以用这个参数来捎带传输load指令的位选择宽度
  io.out_regular.mem_rdata_extend_is_signed := ins_lb | ins_lh

  val hilo_sel: HiloSel.Type = MuxCase(HiloSel.nop, Seq(
    (ins_mthi | ins_mfhi) -> HiloSel.hi,
    (ins_mtlo | ins_mflo | ins_mul) -> HiloSel.lo
  ))
  val hilo_wen: Bool         = ins_multu | ins_mult | ins_div | ins_divu | ins_mthi | ins_mtlo | ins_mul
  io.out_regular.hi_wen := ins_multu | ins_mult | ins_div | ins_divu | ins_mthi
  io.out_regular.lo_wen := ins_multu | ins_mult | ins_div | ins_divu | ins_mtlo | ins_mul
  io.out_regular.hilo_sel := hilo_sel


  io.out_regular.pc_delay_slot := pc + 4.U
  io.out_regular.pc_debug := pc
  // sw会使用寄存器堆读端口2的数据写入内存
  io.out_regular.mem_wdata := regfile_read2_with_bypass

  val cp0_wen    : Bool = ins_mtc0
  val cp0_addr   : UInt = Cat(rd, instruction(2, 0))
  val op_from_cp0: Bool = ins_mfc0

  io.out_regular.cp0_wen := cp0_wen
  io.out_regular.cp0_addr := cp0_addr(7, 3)
  io.out_regular.cp0_sel := cp0_addr(2, 0)
  io.out_regular.regfile_wdata_from_cp0 := op_from_cp0

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
    ins_mul ||
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

  io.out_regular.is_delay_slot := io.in.is_delay_slot
  io.out_regular.overflow_detection_en := ins_add | ins_addi | ins_sub
  io.out_regular.ins_eret := ins_eret
  io.out_regular.src_use_hilo := ins_mfhi | ins_mflo

  io.out_regular.exception_flags := Mux(pc(1, 0) === 0.U,
    0.U, ExceptionConst.EXCEPTION_FETCH_ADDR) |
    Mux(ins_valid, 0.U, ExceptionConst.EXCEPTION_RESERVE_INST) |
    Mux(ins_syscall, ExceptionConst.EXCEPTION_SYSCALL, 0.U) |
    Mux(ins_break, ExceptionConst.EXCEPTION_TRAP, 0.U)
  io.out_regular.ins_valid := io.in.ins_valid

  io.out_issue.op1_rf_num := Mux(src1_sel === AluSrc1Sel.regfile_read1, rs, 0.U)
  io.out_issue.op2_rf_num := Mux(src2_sel === AluSrc2Sel.regfile_read2, rs, 0.U)
  io.out_issue.rf_wen := rf_wen
  io.out_issue.rf_wnum := MuxCase(0.U, Seq(
    (rf_wdst_sel === RegFileWAddrSel.inst_rt) -> rt,
    (rf_wdst_sel === RegFileWAddrSel.inst_rd) -> rd,
    (rf_wdst_sel === RegFileWAddrSel.const_31) -> 31.U
  ))

  io.out_issue.cp0_wen := cp0_wen
  io.out_issue.cp0_addr := cp0_addr
  io.out_issue.op2_from_cp0 := op_from_cp0

  io.out_issue.hilo_sel := hilo_sel
  io.out_issue.hilo_wen := hilo_wen
  io.out_issue.op2_from_hilo := ins_mfhi | ins_mflo
  io.out_issue.is_jump := is_jump
  io.out_issue.div_or_mult := ins_div | ins_divu | ins_multu | ins_mult | ins_mul
  io.out_issue.is_valid := io.in.ins_valid
  io.out_issue.is_eret := ins_eret && io.in.ins_valid
}
