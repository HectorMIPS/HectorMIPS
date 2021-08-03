package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import com.github.hectormips.pipeline
import com.github.hectormips.pipeline.issue.{Decoder, Issuer}

object RegFileWAddrSel extends OneHotEnum {
  val inst_rd : Type = Value(1.U)
  val inst_rt : Type = Value(2.U)
  val const_31: Type = Value(4.U)
}

object AluSrc1Sel extends OneHotEnum {
  val regfile_read1: Type = Value(1.U)
  val pc_delay     : Type = Value(2.U)
  val sa_32        : Type = Value(4.U) // sa零扩展
}

object AluSrc2Sel extends OneHotEnum {
  val regfile_read2         : Type = Value(1.U)
  val imm_32_signed_extend  : Type = Value(2.U) // 立即数域符号扩展
  val const_4               : Type = Value(4.U) // 立即数4，用于jal
  val imm_32_unsigned_extend: Type = Value(8.U)
}

object HiloSel extends OneHotEnum {
  val hi  : Type = Value(1.U)
  val lo  : Type = Value(2.U)
  val both: Type = Value(3.U)
}

class FetchDecodeBundle extends WithValid {
  val ins_if_id      : UInt = UInt(64.W)
  val pc_debug_if_id : UInt = UInt(32.W)
  val ins_valid_if_id: UInt = UInt(2.W)


  override def defaults(): Unit = {
    super.defaults()
    ins_if_id := 0.U
    pc_debug_if_id := 0xbfbffffcL.U
    ins_valid_if_id := 0.U
  }
}

class BypassMsgBundle extends Bundle {
  val reg_addr  : UInt = UInt(5.W)
  val reg_data  : UInt = UInt(32.W)
  val bus_valid : Bool = Bool()
  val data_valid: Bool = Bool()
}

// 当数据没有冲突的时候，一次会有两条指令处于后面的阶段，因此bypass的宽度需要x2
class DecodeBypassBundle extends Bundle {
  val bp_ex_id: Vec[BypassMsgBundle] = Vec(2, new BypassMsgBundle)
  val bp_ms_id: Vec[BypassMsgBundle] = Vec(2, new BypassMsgBundle)
  val bp_wb_id: Vec[BypassMsgBundle] = Vec(2, new BypassMsgBundle)
}

class InsDecodeBundle extends WithAllowin {

  val bypass_bus: DecodeBypassBundle = Input(new DecodeBypassBundle)
  val if_id_in  : FetchDecodeBundle  = Input(new FetchDecodeBundle)

  val regfile_raddr1: Vec[UInt] = Output(Vec(2, UInt(5.W)))
  val regfile_raddr2: Vec[UInt] = Output(Vec(2, UInt(5.W)))
  val regfile_read1 : Vec[UInt] = Input(Vec(2, UInt(32.W)))
  val regfile_read2 : Vec[UInt] = Input(Vec(2, UInt(32.W)))

  val id_pf_out: DecodePreFetchBundle = Output(new DecodePreFetchBundle)

  val id_ex_out: Vec[DecodeExecuteBundle] = Output(Vec(2, new DecodeExecuteBundle))
  val flush    : Bool                     = Input(Bool())

}

class InsDecode extends Module {
  val io                       : InsDecodeBundle        = IO(new InsDecodeBundle)
  val decoders                 : Vec[Decoder#DecoderIO] = VecInit(Seq.fill(2)(Module(new Decoder).io))
  // 给出发射控制信号
  val issuer                   : Issuer                 = Module(new Issuer)
  // 如果当前有两条指令，只有一条能被发射，同时新进入了两条指令，则靠后的一条会被存储在extra_ins_buffer中
  val issue_remain_buffer      : UInt                   = RegInit(0.U(32.W))
  val issue_remain_buffer_valid: Bool                   = RegInit(0.B)


  decoders(0).in.ins_valid := Mux(issue_remain_buffer_valid,
    1.B, io.if_id_in.ins_valid_if_id(0)) && io.if_id_in.bus_valid
  decoders(1).in.ins_valid := Mux(issue_remain_buffer_valid,
    io.if_id_in.ins_valid_if_id(0), io.if_id_in.ins_valid_if_id(1)) && io.if_id_in.bus_valid

  decoders(0).in.pc_debug := Mux(issue_remain_buffer_valid,
    io.if_id_in.pc_debug_if_id - 4.U, io.if_id_in.pc_debug_if_id)
  decoders(1).in.pc_debug := Mux(issue_remain_buffer_valid,
    io.if_id_in.pc_debug_if_id, io.if_id_in.pc_debug_if_id + 4.U)

  decoders(0).in.instruction := Mux(issue_remain_buffer_valid,
    issue_remain_buffer, io.if_id_in.ins_if_id(31, 0))
  decoders(1).in.instruction := Mux(issue_remain_buffer_valid,
    io.if_id_in.ins_if_id(31, 0), io.if_id_in.ins_if_id(63, 32))
  for (i <- 0 to 1) {
    decoders(i).in.bypass_bus := io.bypass_bus
    decoders(i).in.regfile_read1 := io.regfile_read1(i)
    decoders(i).in.regfile_read2 := io.regfile_read2(i)
  }

  decoders(0).in.is_delay_slot := 0.B
  decoders(1).in.is_delay_slot := decoders(0).out_branch.is_jump

  io.regfile_raddr1 := VecInit(Seq(decoders(0).out_regular.rs, decoders(1).out_regular.rs))
  io.regfile_raddr2 := VecInit(Seq(decoders(0).out_regular.rt, decoders(1).out_regular.rt))

  issuer.io.in_decoder1 <> decoders(0).out_issue
  issuer.io.in_decoder2 <> decoders(1).out_issue
  val issue_count    : UInt = issuer.io.out.issue_count
  // 当前输入的指令有效数
  val ins_valid_count: UInt = MuxCase(0.U, Seq(
    (!io.if_id_in.ins_valid_if_id(0)) -> 0.U,
    io.if_id_in.ins_valid_if_id(1) -> 2.U,
    io.if_id_in.ins_valid_if_id(0) -> 1.U,
  ))
  val ins1_ready     : Bool = !decoders(0).out_hazard.load_to_regular &&
    !(decoders(0).out_branch.is_jump && (decoders(0).out_hazard.load_to_branch || decoders(0).out_hazard.ex_to_branch))
  val ins2_ready     : Bool = !decoders(1).out_hazard.load_to_regular &&
    !(decoders(1).out_branch.is_jump && (decoders(1).out_hazard.load_to_branch || decoders(1).out_hazard.ex_to_branch))


  // 当准备被发射的指令没有冲突可以进入下一个阶段的时候准入指令
  val ready_go: Bool = MuxCase(0.B, Seq(
    (issue_count === 1.U) -> ins1_ready,
    (issue_count === 2.U) -> (ins1_ready && ins2_ready)
  ))

  val issue_from_buffer  : Bool = issue_remain_buffer_valid && issue_count >= 1.U
  val issue_from_slot1   : Bool = (!issue_remain_buffer_valid) || (issue_from_buffer && issue_count === 2.U)
  val issue_from_slot2   : Bool = !issue_from_buffer && io.if_id_in.ins_valid_if_id(1) && issue_count === 2.U
  // 当当前的指令非分支指令+延迟槽指令时，将分支指令装入缓存，等待延迟槽指令
  val wait_for_delay_slot: Bool = Mux(issue_from_buffer,
    // 发射的第一条指令来自于buffer，满足1. 译码的第二条结果是跳转 2. fifo给出的结果第二条指令无效 的情况下需要等待
    decoders(1).out_branch.is_jump && decoders(1).out_regular.ins_valid && !io.if_id_in.ins_valid_if_id(1),
    // 发射的第一条指令不是来自buffer，正常判断所有情况
    (decoders(1).out_branch.is_jump && decoders(1).out_regular.ins_valid && io.if_id_in.bus_valid) ||
      (decoders(0).out_branch.is_jump && decoders(0).out_regular.ins_valid &&
        !decoders(1).out_regular.ins_valid && io.if_id_in.bus_valid)
  )


  // 当两条指令中只有一条被发射，新进入的指令有两条时，
  // 将未发射的指令先装入缓存，新的指令进入时，将缓存指令放在发射槽1，
  // 新指令放在槽2，若第二条指令有效则将缓冲更新为该指令
  when(wait_for_delay_slot && !io.flush && io.next_allowin && ready_go) {
    issue_remain_buffer := Mux(decoders(0).out_branch.is_jump || issue_from_buffer,
      io.if_id_in.ins_if_id(31, 0), io.if_id_in.ins_if_id(63, 32))
    issue_remain_buffer_valid := 1.B
  }.elsewhen(
    // 有两条指令在发射槽中，buffer未被占用，只发射一条时
    ((!issue_from_buffer && issue_from_slot1 && !issue_from_slot2 && io.if_id_in.ins_valid_if_id(1)) ||
      // 或者buffer的指令和取来的第一条指令被同时发射，还剩下一条指令未发射
      (issue_from_buffer && issue_from_slot1 && !issue_from_slot2)) &&
      // 如果只发射了buffer中的指令，并且剩下两条指令有效，则不需要将其存入buffer
      !io.flush && io.next_allowin && ready_go && io.if_id_in.ins_valid_if_id(1) && io.if_id_in.bus_valid) {
    issue_remain_buffer := io.if_id_in.ins_if_id(63, 32)
    issue_remain_buffer_valid := 1.B
  }.elsewhen(issue_from_buffer && ready_go && io.next_allowin && io.if_id_in.bus_valid) {
    // buffer中的内容被发射后需要将其置无效
    issue_remain_buffer_valid := 0.B
  }.elsewhen(io.flush) {
    issue_remain_buffer_valid := 0.B
  }
  when(issue_remain_buffer_valid &&
    decoders(0).out_regular.ins_valid && decoders(0).out_branch.is_jump && decoders(0).out_branch.jump_taken &&
    decoders(1).out_regular.ins_valid && ready_go && io.next_allowin && io.if_id_in.bus_valid) {
    // 如果有跳转行为并且执行了跳转才将buffer中的内容置为无效
    issue_remain_buffer_valid := 0.B
  }

  // 直到buffer的指令被发射之前，bus都是有效的
  io.this_allowin := io.next_allowin && !reset.asBool() && ready_go &&
    // 在等待延迟槽指令并且buffer中内容有效的情况下，至少把buffer中原有的指令发射出去
    // 否则，至少需要把槽1中的指令发射出去
    Mux(io.if_id_in.bus_valid,
      Mux(wait_for_delay_slot && issue_remain_buffer_valid, issue_from_buffer,
        issue_from_slot1 || issue_from_slot2), 1.B)

  // 如果有分支指令，仅当其在槽1时有效
  io.id_pf_out.jump_sel_id_pf := decoders(0).out_branch.jump_sel
  io.id_pf_out.jump_val_id_pf := decoders(0).out_branch.jump_val
  io.id_pf_out.is_jump := decoders(0).out_branch.is_jump
  // 仅在槽0是跳转并且延迟槽指令准备就绪时才对pf进行控制
  io.id_pf_out.bus_valid := decoders(0).out_regular.ins_valid && decoders(0).out_branch.is_jump &&
    decoders(0).out_branch.jump_taken && !wait_for_delay_slot && io.if_id_in.bus_valid &&
    !decoders(0).out_hazard.load_to_branch && !decoders(0).out_hazard.ex_to_branch
  io.id_pf_out.jump_taken := decoders(0).out_branch.jump_taken && !wait_for_delay_slot
  io.id_pf_out.stall_id_pf := decoders(0).out_regular.ins_valid && decoders(0).out_branch.is_jump &&
    decoders(1).out_regular.ins_valid && (decoders(0).out_hazard.load_to_branch || decoders(1).out_hazard.ex_to_branch)


  val has_waw_hazard  : Bool = issuer.io.out.waw_hazard
  val bus_valid_common: Bool = !reset.asBool() && ready_go && !io.flush

  def wawEliminate(index: Int, wen: Bool): Bool = {
    // 如果同时有raw和waw冲突，则首先解决raw冲突
    wen && Mux(issuer.io.out.issue_count === 2.U, Mux(has_waw_hazard, index.U === 1.U, 1.B), 1.B)
  }

  for (i <- 0 to 1) {
    io.id_ex_out(i).alu_op_id_ex := decoders(i).out_regular.alu_op
    io.id_ex_out(i).sa_32_id_ex := decoders(i).out_regular.sa_32
    io.id_ex_out(i).imm_32_id_ex := decoders(i).out_regular.imm_32
    io.id_ex_out(i).alu_src1_id_ex := decoders(i).out_regular.alu_src1
    io.id_ex_out(i).alu_src2_id_ex := decoders(i).out_regular.alu_src2
    io.id_ex_out(i).mem_en_id_ex := decoders(i).out_regular.mem_en
    io.id_ex_out(i).mem_wen_id_ex := decoders(i).out_regular.mem_wen
    io.id_ex_out(i).regfile_wsrc_sel_id_ex := decoders(i).out_regular.regfile_wsrc_sel
    io.id_ex_out(i).regfile_waddr_sel_id_ex := decoders(i).out_regular.regfile_waddr_sel
    io.id_ex_out(i).inst_rs_id_ex := decoders(i).out_regular.rs
    io.id_ex_out(i).inst_rd_id_ex := decoders(i).out_regular.rd
    io.id_ex_out(i).inst_rt_id_ex := decoders(i).out_regular.rt
    io.id_ex_out(i).regfile_we_id_ex := decoders(i).out_regular.regfile_we
    io.id_ex_out(i).pc_id_ex_debug := decoders(i).out_regular.pc_debug
    io.id_ex_out(i).mem_wdata_id_ex := decoders(i).out_regular.mem_wdata
    io.id_ex_out(i).hi_wen := wawEliminate(i, decoders(i).out_regular.hi_wen)
    io.id_ex_out(i).lo_wen := wawEliminate(i, decoders(i).out_regular.lo_wen)
    io.id_ex_out(i).hilo_sel := decoders(i).out_regular.hilo_sel
    io.id_ex_out(i).mem_data_sel_id_ex := decoders(i).out_regular.mem_data_sel
    io.id_ex_out(i).mem_rdata_extend_is_signed_id_ex := decoders(i).out_regular.mem_rdata_extend_is_signed
    io.id_ex_out(i).cp0_wen_id_ex := wawEliminate(i, decoders(i).out_regular.cp0_wen)
    io.id_ex_out(i).cp0_addr_id_ex := decoders(i).out_regular.cp0_addr
    io.id_ex_out(i).cp0_sel_id_ex := decoders(i).out_regular.cp0_sel
    io.id_ex_out(i).regfile_wdata_from_cp0_id_ex := decoders(i).out_regular.regfile_wdata_from_cp0
    io.id_ex_out(i).overflow_detection_en := decoders(i).out_regular.overflow_detection_en
    io.id_ex_out(i).ins_eret := decoders(i).out_regular.ins_eret
    io.id_ex_out(i).src_use_hilo := decoders(i).out_regular.src_use_hilo
    io.id_ex_out(i).is_delay_slot := decoders(i).out_regular.is_delay_slot
    io.id_ex_out(i).issue_num := issuer.io.out.issue_count
    io.id_ex_out(i).exception_flags := decoders(i).out_regular.exception_flags
  }
  // 当第一条指令为跳转指令的时候，只有两条指令同时有效时才能发射
  io.id_ex_out(0).bus_valid := bus_valid_common && decoders(0).out_regular.ins_valid &&
    Mux(decoders(0).out_branch.is_jump,
      decoders(1).out_regular.ins_valid,
      1.B)
  io.id_ex_out(1).bus_valid := bus_valid_common && decoders(1).out_regular.ins_valid &&
    issuer.io.out.issue_count === 2.U


}

object InsDecode extends App {
  (new ChiselStage).emitVerilog(new InsDecode)
}
