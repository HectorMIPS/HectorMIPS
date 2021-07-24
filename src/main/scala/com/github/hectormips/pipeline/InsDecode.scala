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
  val hi: Type = Value(1.U)
  val lo: Type = Value(2.U)
}

class FetchDecodeBundle extends WithValid {
  val ins_if_id          : UInt = UInt(64.W)
  val pc_delay_slot_if_id: UInt = UInt(32.W) // 延迟槽pc
  val pc_debug_if_id     : UInt = UInt(32.W) // 转移pc
  val ins_valid_if_id    : UInt = UInt(2.W)
  val is_delay_slot      : UInt = UInt(2.W)

  override def defaults(): Unit = {
    super.defaults()
    ins_if_id := 0.U
    pc_delay_slot_if_id := 0xbfbffffcL.U
    pc_debug_if_id := 0xbfbffffcL.U
    is_delay_slot := 0.U
  }
}

class BypassMsgBundle extends Bundle {
  val reg_addr  : UInt = UInt(5.W)
  val reg_data  : UInt = UInt(32.W)
  val bus_valid : Bool = Bool()
  val data_valid: Bool = Bool()
}

// 当数据没有冲突的时候，一次会有两条指令处于后面的阶段，因此bypass的宽度需要x2
// TODO: 写后写冲突交由前递发起者解决
//  即保证在两个前递addr的时候保证index为1的前递为有效前递
class DecodeBypassBundle extends Bundle {
  val bp_ex_id: Vec[BypassMsgBundle] = Vec(2, new BypassMsgBundle)
  val bp_ms_id: Vec[BypassMsgBundle] = Vec(2, new BypassMsgBundle)
  val bp_wb_id: Vec[BypassMsgBundle] = Vec(2, new BypassMsgBundle)
}

class InsDecodeBundle extends WithAllowin {

  val bypass_bus: DecodeBypassBundle = Input(new DecodeBypassBundle)
  val if_id_in  : FetchDecodeBundle  = Input(new FetchDecodeBundle)

  val regfile_raddr1: Vec[UInt] = Output(Vec(2, UInt(32.W)))
  val regfile_raddr2: Vec[UInt] = Output(Vec(2, UInt(32.W)))
  val regfile_read1 : UInt      = Input(UInt(32.W))
  val regfile_read2 : UInt      = Input(UInt(32.W))

  val id_pf_out: DecodePreFetchBundle = Output(new DecodePreFetchBundle)

  val id_ex_out : DecodeExecuteBundle = Output(new DecodeExecuteBundle)
  val ins_opcode: UInt                = Output(UInt(6.W))
  val flush     : Bool                = Input(Bool())


  val decode_to_fetch_next_pc: Vec[UInt] = Output(Vec(2, UInt(32.W))) // 回馈给取值的pc通路
}

class InsDecode extends Module {
  val io      : InsDecodeBundle = IO(new InsDecodeBundle)
  val ready_go: Bool            = Wire(Bool())
  val decoder1: Decoder         = Module(new Decoder)
  val decoder2: Decoder         = Module(new Decoder)
  // 给出发射控制信号
  val issuer  : Issuer          = Module(new Issuer)

  decoder1.io.in.pc_debug := io.if_id_in.pc_debug_if_id
  decoder1.io.in.is_delay_slot := io.if_id_in.is_delay_slot(0)
  decoder1.io.in.bypass_bus := io.bypass_bus
  decoder1.io.in.regfile_read1 := io.regfile_read1(0)
  decoder1.io.in.regfile_read2 := io.regfile_read2(0)
  decoder1.io.in.instruction := io.if_id_in.ins_if_id(31, 0)

  decoder2.io.in.pc_debug := io.if_id_in.pc_debug_if_id + 4.U
  decoder2.io.in.is_delay_slot := io.if_id_in.is_delay_slot(1)
  decoder2.io.in.bypass_bus := io.bypass_bus
  decoder2.io.in.regfile_read1 := io.regfile_read1(1)
  decoder2.io.in.regfile_read2 := io.regfile_read2(1)
  decoder2.io.in.instruction := io.if_id_in.ins_if_id(63, 32)

  io.regfile_raddr1 := VecInit(Seq(decoder2.io.out_regular.rs, decoder1.io.out_regular.rs))
  io.regfile_raddr2 := VecInit(Seq(decoder2.io.out_regular.rt, decoder1.io.out_regular.rt))

}

object InsDecode extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new InsDecode())))
}