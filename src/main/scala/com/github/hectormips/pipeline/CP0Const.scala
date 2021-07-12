package com.github.hectormips.pipeline

import chisel3._

object CP0Const {
  val CP0_REGADDR_BADVADDR: UInt = 8.U

  val CP0_REGADDR_COUNT: UInt = 9.U

  val CP0_REGADDR_COMPARE: UInt = 11.U

  val CP0_REGADDR_STATUS: UInt = 12.U

  val CP0_REGADDR_CAUSE: UInt = 13.U

  val CP0_REGADDR_EPC: UInt = 14.U
}