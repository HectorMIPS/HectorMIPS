package com.github.hectormips.pipeline

import Chisel.{BitPat, Cat, Mux1H, RegEnable, UIntToOH}
import chisel3._
import chisel3.experimental.ChiselEnum
import com.github.hectormips.pipeline.CP0Const


class ExecuteCP0Bundle extends Bundle {
  val exception_occur: Bool = Bool()
  val pc             : UInt = UInt(32.W)
  val is_delay_slot  : Bool = Bool() // 执行阶段的指令是否是分支延迟槽指令
  val exc_code       : UInt = UInt(ExcCodeConst.WIDTH.W)
  val badvaddr       : UInt = UInt(32.W)
  val eret_occur     : Bool = Bool()
}

class CP0Bundle extends Bundle {
  val regaddr   : UInt             = Input(UInt(5.W))
  val regsel    : UInt             = Input(UInt(3.W))
  val wdata     : UInt             = Input(UInt(32.W))
  val rdata     : UInt             = Output(UInt(32.W))
  val wen       : Bool             = Input(Bool())
  val ex_cp0_in : ExecuteCP0Bundle = Input(new ExecuteCP0Bundle)
  val cp0_ex_out: CP0ExecuteBundle = Output(new CP0ExecuteBundle)
  val epc       : UInt             = Output(UInt(32.W))

}

class CP0 extends Module {
  val io: CP0Bundle = IO(new CP0Bundle)

  val status_exl: Bool = Wire(Bool())

  val cause   : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val status  : UInt = RegInit(UInt(32.W), init = 0x400000.U)
  val count   : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val compare : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val badvaddr: UInt = RegInit(UInt(32.W), init = 0x0.U)
  val epc     : UInt = RegInit(UInt(32.W), init = 0x0.U)

  def changeBit(reg: UInt, index: Int, new_bit: Bool): Unit = {
    reg := (new_bit << index).asUInt() | (reg & (~(1.U << index)).asUInt())
  }

  when(io.regaddr === CP0Const.CP0_REGADDR_CAUSE && io.regsel === 0.U && io.wen) {
    cause := Cat(0.U(22.W),
      io.wdata(9, 8), // ip1..ip0
      0.U(8.W)
    )
  }

  when(io.ex_cp0_in.exception_occur) {
    when(!status_exl) {
      cause := Cat(io.ex_cp0_in.is_delay_slot,
        0.U(21.W),
        cause(9, 8), // ip1..ip0
        0.U(1.W),
        io.ex_cp0_in.exc_code, // ExcCode
        0.U(2.W)
      )
    }
  }

  when(io.regaddr === CP0Const.CP0_REGADDR_STATUS && io.regsel === 0.U && io.wen) {
    status := Cat(0.U(1.W),
      1.U(1.W), // bev
      0.U(6.W),
      io.wdata(15, 8), // im
      0.U(6.W),
      io.wdata(1), // exl
      io.wdata(0) // ie
    )
  }

  status_exl := status(1)
  when(io.ex_cp0_in.exception_occur) {
    status := Cat(status(31, 2),
      1.B, // exl
      status(0) // ie
    )
  }
  when(io.ex_cp0_in.eret_occur) {
    status := Cat(status(31, 2),
      0.B, // exl
      status(0) // ie
    )
  }

  when(io.ex_cp0_in.exception_occur) {
    when(io.ex_cp0_in.exc_code === ExcCodeConst.ADEL) {
      badvaddr := io.ex_cp0_in.badvaddr
    }
  }

  when(io.regaddr === CP0Const.CP0_REGADDR_COUNT && io.regsel === 0.U && io.wen) {
    count := io.wdata
  }


  when(io.regaddr === CP0Const.CP0_REGADDR_COMPARE && io.regsel === 0.U && io.wen) {
    compare := io.wdata
  }

  when(io.regaddr === CP0Const.CP0_REGADDR_EPC && io.regsel === 0.U && io.wen) {
    epc := io.wdata
  }
  when(io.ex_cp0_in.exception_occur) {
    when(!status_exl) { // 只有exl置0时更新epc
      epc := Mux(io.ex_cp0_in.is_delay_slot, io.ex_cp0_in.pc - 4.U, io.ex_cp0_in.pc)
    }
  }
  io.epc := epc
  io.cp0_ex_out.epc := epc

  io.rdata := 0.U
  io.rdata := Mux1H(Seq(
    (io.regaddr === CP0Const.CP0_REGADDR_EPC) -> epc,
    (io.regaddr === CP0Const.CP0_REGADDR_CAUSE) -> cause,
    (io.regaddr === CP0Const.CP0_REGADDR_STATUS) -> status,
    (io.regaddr === CP0Const.CP0_REGADDR_COMPARE) -> compare,
    (io.regaddr === CP0Const.CP0_REGADDR_COUNT) -> count,
    (io.regaddr === CP0Const.CP0_REGADDR_BADVADDR) -> badvaddr,
  ))
  io.cp0_ex_out.status_exl := status_exl
}