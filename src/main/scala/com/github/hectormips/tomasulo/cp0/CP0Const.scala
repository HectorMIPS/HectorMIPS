package com.github.hectormips.tomasulo.cp0

import chisel3._
import chisel3.experimental.ChiselEnum

object CP0Const {
  val CP0_REGADDR_BADVADDR: UInt = 8.U

  val CP0_REGADDR_COUNT: UInt = 9.U

  val CP0_REGADDR_COMPARE: UInt = 11.U

  val CP0_REGADDR_STATUS: UInt = 12.U

  val CP0_REGADDR_CAUSE: UInt = 13.U

  val CP0_REGADDR_EPC: UInt = 14.U

}

// 例外常量，从低位到高位优先级依次降低
object ExceptionConst {
  val EXCEPTION_FETCH_ADDR        : UInt = 0x1.U
  val EXCEPTION_RESERVE_INST      : UInt = 0x2.U
  val EXCEPTION_INT_OVERFLOW      : UInt = 0x4.U
  val EXCEPTION_TRAP              : UInt = 0x8.U
  val EXCEPTION_SYSCALL           : UInt = 0x10.U
  val EXCEPTION_BAD_RAM_ADDR_WRITE: UInt = 0x20.U
  val EXCEPTION_BAD_RAM_ADDR_READ : UInt = 0x40.U

  val EXCEPTION_PROGRAM_ADDR: UInt = 0xBFC0037CL.U

  val EXCEPTION_FLAG_WIDTH: Int = 7
}

object ExcCodeConst {
  val INT : UInt = 0x00.U
  val ADEL: UInt = 0x04.U
  val ADES: UInt = 0x05.U
  val SYS : UInt = 0x08.U
  val BP  : UInt = 0x09.U
  val RI  : UInt = 0x0a.U
  val OV  : UInt = 0x0c.U

  val WIDTH: Int = 5
}