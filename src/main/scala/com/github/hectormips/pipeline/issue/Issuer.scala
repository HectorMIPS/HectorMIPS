package com.github.hectormips.pipeline.issue

import chisel3._

class IssuerIn extends Bundle {
  val decoder_regular: DecoderRegularOut = new DecoderRegularOut
  val decoder_branch : DecoderBranchOut  = new DecoderBranchOut
  val decoder_hazard : DecoderHazardOut  = new DecoderHazardOut
}

class IssuerOut extends Bundle {
  // 能发射的条数
  val issue_count: UInt = UInt(2.W)
  // 默认顺序是decoder0的指令优先于decoder1的
  // 如果为1，说明decoder0输出的为后一条指令
  val reversed   : Bool = Bool()

}

// 指令发射模块，属于decode阶段，用于得到指令发射信息
class Issuer extends Module {
  class IssueIO extends Bundle {
    val in_decoder0: IssuerIn  = Input(new IssuerIn)
    val in_decoder1: IssuerIn  = Input(new IssuerIn)
    val out        : IssuerOut = Output(new IssuerOut)
  }

  val io: IssueIO = IO(new IssueIO)


}
