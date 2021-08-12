package com.github.hectormips.pipeline.cp0

import chisel3._

object CP0Const {
  val CP0_REGADDR_BADVADDR: UInt = 8.U

  val CP0_REGADDR_COUNT: UInt = 9.U

  val CP0_REGADDR_COMPARE: UInt = 11.U

  val CP0_REGADDR_STATUS: UInt = 12.U

  val CP0_REGADDR_CAUSE: UInt = 13.U

  val CP0_REGADDR_EPC: UInt = 14.U

  val CP0_REGADDR_ENTRYHI: UInt = 10.U

  val CP0_REGADDR_PAGEMASK: UInt = 5.U

  val CP0_REGADDR_ENTRYLO0: UInt = 2.U

  val CP0_REGADDR_ENTRYLO1: UInt = 3.U

  val CP0_REGADDR_INDEX: UInt = 0.U

}

// 例外常量，从低位到高位优先级依次降低
object ExceptionConst {
  val EXCEPTION_FETCH_ADDR        : UInt = 0x1.U // 0
  val EXCEPTION_RESERVE_INST      : UInt = 0x2.U // 1
  val EXCEPTION_INT_OVERFLOW      : UInt = 0x4.U // 2
  val EXCEPTION_TRAP              : UInt = 0x8.U // 3
  val EXCEPTION_SYSCALL           : UInt = 0x10.U // 4
  val EXCEPTION_BAD_RAM_ADDR_WRITE: UInt = 0x20.U // 5
  val EXCEPTION_BAD_RAM_ADDR_READ : UInt = 0x40.U // 6

  val EXCEPTION_TLB_REFILL_FETCH : UInt = 0x80.U // 7
  val EXCEPTION_TLB_INVALID_FETCH: UInt = 0x100.U // 8
  val EXCEPTION_TLB_REFILL_DATA  : UInt = 0x200.U // 9
  val EXCEPTION_TLB_INVALID_DATA : UInt = 0x400.U // 10
  val EXCEPTION_TLB_MODIFIED_DATA: UInt = 0x800.U // 11

  val EXCEPTION_PROGRAM_ADDR       : UInt = 0xBFC00380L.U
  val EXCEPTION_PROGRAM_ADDR_REFILL: UInt = 0xBFC00200L.U

  val EXCEPTION_FLAG_WIDTH: Int = 12
}

object ExcCodeConst {
  val INT : UInt = 0x00.U
  val MOD : UInt = 0x01.U
  val TLBL: UInt = 0x02.U
  val TLBS: UInt = 0x02.U
  val ADEL: UInt = 0x04.U
  val ADES: UInt = 0x05.U
  val SYS : UInt = 0x08.U
  val BP  : UInt = 0x09.U
  val RI  : UInt = 0x0a.U
  val OV  : UInt = 0x0c.U

  val WIDTH: Int = 5
}