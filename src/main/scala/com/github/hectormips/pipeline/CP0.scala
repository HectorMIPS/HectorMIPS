package com.github.hectormips.pipeline

import Chisel.{BitPat, Cat, Mux1H, RegEnable, UIntToOH}
import chisel3._
import chisel3.experimental.ChiselEnum
import com.github.hectormips.pipeline.CP0Const

object ExcCode extends ChiselEnum {
  val int : Type = Value(0x00.U)
  val adel: Type = Value(0x04.U)
  val ades: Type = Value(0x05.U)
  val sys : Type = Value(0x08.U)
  val bp  : Type = Value(0x09.U)
  val ri  : Type = Value(0x0a.U)
  val ov  : Type = Value(0x0c.U)
}

//class CauseRegBundle extends Bundle {
//  val bd      : Bool         = Bool()
//  val ti      : Bool         = Bool()
//  val zero_0  : UInt         = 0.U(14.W)
//  val ip      : UInt         = UInt(8.W)
//  val zero_1  : Bool         = 0.B
//  val exc_code: ExcCode.Type = ExcCode()
//  val zero_2  : UInt         = 0.U(2.W)
//}
//
//class StatusRegBundle extends Bundle {
//  val zero_0: UInt = 0.U(9.W)
//  val bev   : Bool = 1.B
//  val zero_1: UInt = 0.U(6.W)
//  // 仅由MTC0维护
//  val im    : UInt = UInt(8.W)
//  val zero_2: UInt = 0.U(6.W)
//  val exl   : Bool = Bool()
//  val ie    : Bool = Bool()
//}

class CP0Bundle extends Bundle {
  val regaddr: UInt = Input(UInt(5.W))
  val regsel : UInt = Input(UInt(3.W))
  val wdata  : UInt = Input(UInt(32.W))
  val rdata  : UInt = Output(UInt(32.W))
  val wen    : Bool = Input(Bool())

}

class CP0 extends Module {
  val io: CP0Bundle = IO(new CP0Bundle)

  val cause: UInt = Reg(UInt(32.W))
  when(io.regaddr === CP0Const.CP0_REGADDR_CAUSE && io.regsel === 0.U && io.wen) {
    cause := io.wdata
  }

  val status: UInt = Reg(UInt(32.W))
  when(io.regaddr === CP0Const.CP0_REGADDR_STATUS && io.regsel === 0.U && io.wen) {
    status := io.wdata
  }

  val count: UInt = Reg(UInt(32.W))

  when(io.regaddr === CP0Const.CP0_REGADDR_COUNT && io.regsel === 0.U && io.wen) {
    count := io.wdata
  }

  val compare: UInt = Reg(UInt(32.W))

  when(io.regaddr === CP0Const.CP0_REGADDR_COMPARE && io.regsel === 0.U && io.wen) {
    compare := io.wdata
  }

  val badvaddr: UInt = Reg(UInt(32.W))

  when(io.regaddr === CP0Const.CP0_REGADDR_BADVADDR && io.regsel === 0.U && io.wen) {
    badvaddr := io.wdata
  }

  val epc: UInt = Reg(UInt(32.W))

  when(io.regaddr === CP0Const.CP0_REGADDR_EPC && io.regsel === 0.U && io.wen) {
    epc := io.wdata
  }

  io.rdata := 0.U
  io.rdata := Mux1H(Seq(
    (io.regaddr === CP0Const.CP0_REGADDR_EPC) -> epc,
    (io.regaddr === CP0Const.CP0_REGADDR_CAUSE) -> cause,
    (io.regaddr === CP0Const.CP0_REGADDR_STATUS) -> status,
    (io.regaddr === CP0Const.CP0_REGADDR_COMPARE) -> compare,
    (io.regaddr === CP0Const.CP0_REGADDR_COUNT) -> count,
    (io.regaddr === CP0Const.CP0_REGADDR_BADVADDR) -> badvaddr,
  ))
}
