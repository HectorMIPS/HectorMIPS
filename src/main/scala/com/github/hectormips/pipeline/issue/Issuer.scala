package com.github.hectormips.pipeline.issue

import chisel3._


class IssuerOut extends Bundle {
  // 能发射的条数
  val issue_count: UInt = UInt(2.W)
  // 首先发射的指令在槽中的位置
  val first_index: Bool = Bool()
  // 是否存在写后写冲突，如果存在，需要将后面的指令无效化
  val waw_hazard : Bool = Bool()

}

// 指令发射模块，属于decode阶段，用于得到指令发射信息
class Issuer extends Module {
  class IssueIO extends Bundle {
    val in_decoder1: DecoderIssueOut = Input(new DecoderIssueOut)
    val in_decoder2: DecoderIssueOut = Input(new DecoderIssueOut)
    val out        : IssuerOut       = Output(new IssuerOut)
  }

  val io               : IssueIO = IO(new IssueIO)
  // 两个槽中的指令是否有写后读冲突
  // 包括寄存器堆、hilo和cp0
  val has_raw_hazard   : Bool    = Wire(Bool())
  // 两个槽中的指令是否有写后写冲突
  val has_waw_hazard   : Bool    = Wire(Bool())
  // 槽1中的指令有效并且为分支指令时只能发射一条
  // 即跳转指令永远等待它的延迟槽指令到位才发射
  val is_decoder2_jump : Bool    = Wire(Bool())
  // 是否存在器件冲突
  val has_device_hazard: Bool    = Wire(Bool())

  // TODO: 当前始终从0号槽开始发射指令
  io.out.first_index := 0.B
  val has_raw_regfile_hazard: Bool = io.in_decoder1.rf_wen && io.in_decoder1.rf_wnum =/= 0.U &&
    (io.in_decoder2.op1_rf_num === io.in_decoder1.rf_wnum ||
      io.in_decoder2.op2_rf_num === io.in_decoder1.rf_wnum)
  val has_raw_cp0_hazard    : Bool = io.in_decoder1.cp0_wen && io.in_decoder2.op2_from_cp0 &&
    io.in_decoder1.cp0_addr === io.in_decoder2.cp0_addr
  val has_raw_hilo_hazard   : Bool = io.in_decoder1.hilo_wen && io.in_decoder2.op2_from_hilo &&
    io.in_decoder1.hilo_sel === io.in_decoder2.hilo_sel

  val has_waw_regfile_hazard: Bool = io.in_decoder1.rf_wen && io.in_decoder2.rf_wen &&
    io.in_decoder1.rf_wnum =/= 0.U && io.in_decoder1.rf_wnum === io.in_decoder2.rf_wnum

  val has_waw_cp0_hazard : Bool = io.in_decoder1.cp0_wen && io.in_decoder2.cp0_wen &&
    io.in_decoder1.cp0_addr === io.in_decoder2.cp0_addr
  val has_waw_hilo_hazard: Bool = io.in_decoder1.hilo_wen && io.in_decoder2.hilo_wen &&
    io.in_decoder1.hilo_sel === io.in_decoder2.hilo_sel

  has_raw_hazard := has_raw_regfile_hazard || has_raw_cp0_hazard || has_raw_hilo_hazard
  is_decoder2_jump := io.in_decoder2.is_valid && io.in_decoder2.is_jump
  has_device_hazard := io.in_decoder1.div_or_mult && io.in_decoder2.div_or_mult
  has_waw_hazard := has_waw_regfile_hazard || has_waw_hilo_hazard || has_waw_hilo_hazard
  // 有冲突或者只有一条指令的时候只发射一条
  // 当一条指令为eret的时候也只发射一条
  io.out.issue_count := Mux(has_raw_hazard || has_device_hazard ||
    io.in_decoder1.is_eret || io.in_decoder2.is_eret || is_decoder2_jump, 1.U, 2.U)
  io.out.waw_hazard := Mux(io.in_decoder2.is_valid, !has_waw_hazard, 0.B)


}
