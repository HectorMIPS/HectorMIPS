package com.github.hectormips.pipeline.issue

import chisel3._

// 指令发射模块，属于decode阶段，用于得到指令发射信息
class Issuer extends Module {
  class IssueIO extends Bundle {

  }

  val io: IssueIO = IO(new IssueIO)

}
