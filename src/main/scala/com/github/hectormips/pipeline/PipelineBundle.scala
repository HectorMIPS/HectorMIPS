package com.github.hectormips.pipeline

import chisel3._

trait WithAllowin extends Bundle {
  val this_allowin: Bool = Output(Bool())
  val next_allowin: Bool = Input(Bool())
}

trait WithValid extends Bundle {
  val bus_valid: Bool = Bool()

  def defaults(): Unit = {
    bus_valid := 0.B
  }
}

trait WithException extends Bundle {
  val exception_flags: UInt = UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W)

  def defaults(): Unit = {
    exception_flags := 0.U
  }
}

trait WithIssueNum extends Bundle {
  val issue_num: UInt = UInt(2.W)

  def defaults(): Unit = {
    issue_num := 0.U
  }
}

trait WithVEI extends WithValid with WithException with WithIssueNum {
  override def defaults(): Unit = {
    bus_valid := 0.B
    exception_flags := 0.U
    issue_num := 0.U
  }
}