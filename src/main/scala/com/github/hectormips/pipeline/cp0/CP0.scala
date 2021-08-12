package com.github.hectormips.pipeline.cp0

import Chisel.{Cat, MuxCase, UIntToOH, log2Ceil, log2Floor}
import chisel3._
import com.github.hectormips.pipeline.CP0ExecuteBundle


class ExecuteCP0Bundle(n_tlb: Int) extends Bundle {
  val exception_occur: Bool = Bool()
  val pc             : UInt = UInt(32.W)
  val is_delay_slot  : Bool = Bool() // 执行阶段的指令是否是分支延迟槽指令
  val exc_code       : UInt = UInt(ExcCodeConst.WIDTH.W)
  val badvaddr       : UInt = UInt(32.W)
  val entryhi_vpn2   : UInt = UInt(19.W)
  val eret_occur     : Bool = Bool()
}

class WriteBackCP0Bundle extends Bundle {
  val regaddr: UInt = Input(UInt(5.W))
  val regsel : UInt = Input(UInt(3.W))
  val wdata  : UInt = Input(UInt(32.W))
  val rdata  : UInt = Output(UInt(32.W))
  val wen    : Bool = Input(Bool())
}

class CP0Bundle(n_tlb: Int) extends Bundle {
  val wb_cp0    : Vec[WriteBackCP0Bundle] = Vec(2, new WriteBackCP0Bundle())
  val ex_cp0_in : ExecuteCP0Bundle        = Input(new ExecuteCP0Bundle(n_tlb))
  val cp0_ex_out: CP0ExecuteBundle        = Output(new CP0ExecuteBundle)
  val epc       : UInt                    = Output(UInt(32.W))
  val status_im : UInt                    = Output(UInt(8.W))
  val cause_ip  : UInt                    = Output(UInt(8.W))
  val int_in    : UInt                    = Input(UInt(6.W))

  val tlbp_cp0 : TLBPCP0Bundle  = Input(new TLBPCP0Bundle(n_tlb))
  val tlbwi_cp0: TLBWICP0Bundle = Output(new TLBWICP0Bundle(n_tlb))
  val tlbr_cp0 : TLBRCP0Bundle  = Input(new TLBRCP0Bundle())
  val asid     : UInt           = Output(UInt(8.W))
}

class CP0(n_tlb: Int = 32, timer_int_en: Boolean = true) extends Module {
  val io: CP0Bundle = IO(new CP0Bundle(n_tlb))

  val status_exl: Bool = Wire(Bool())

  val cause      : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val cause_15_10: UInt = RegNext(next = Cat(io.int_in(5) | (if (timer_int_en) 0.U else cause(30)), io.int_in(4, 0)), init = 0x0.U)
  val status     : UInt = RegInit(UInt(32.W), init = 0x400000.U)
  val count      : UInt = RegInit(UInt(32.W), init = 0x0.U)
  // tick寄存器，用于每两个周期count+1
  val tick_next  : Bool = Wire(Bool())
  val tick       : Bool = RegNext(init = 0.B, next = !tick_next)
  tick_next := tick
  val compare  : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val badvaddr : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val epc      : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val entryhi  : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val pagemask : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val entrylo0 : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val entrylo1 : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val index    : UInt = RegInit(UInt(32.W), init = 0x0.U)
  val log_n_tlb: Int  = log2Ceil(n_tlb)


  val compare_eq_count: Bool = compare === count

  for (i <- 0 to 1) {
    when(io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_CAUSE && io.wb_cp0(i).regsel === 0.U && io.wb_cp0(i).wen) {
      cause := Cat(
        cause(31, 30), // bd, ti
        0.U(20.W),
        io.wb_cp0(i).wdata(9, 8), // ip1..ip0
        0.U(8.W)
      )
    }
  }

  when(io.ex_cp0_in.exception_occur) {
    when(status_exl) {
      cause := Cat(cause(31), // bd
        cause(30), // ti
        0.U(20.W),
        cause(9, 8), // ip1..ip0
        0.U(1.W),
        io.ex_cp0_in.exc_code, // ExcCode
        0.U(2.W)
      )
    }.otherwise {
      cause := Cat(io.ex_cp0_in.is_delay_slot,
        cause(30), // ti
        0.U(20.W),
        cause(9, 8), // ip1..ip0
        0.U(1.W),
        io.ex_cp0_in.exc_code, // ExcCode
        0.U(2.W)
      )
    }
  }


  for (i <- 0 to 1) {
    when(io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_STATUS && io.wb_cp0(i).regsel === 0.U && io.wb_cp0(i).wen) {
      status := Cat(0.U(9.W),
        1.U(1.W), // bev
        0.U(6.W),
        io.wb_cp0(i).wdata(15, 8), // im
        0.U(6.W),
        io.wb_cp0(i).wdata(1), // exl
        io.wb_cp0(i).wdata(0) // ie
      )
    }
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
    when(io.ex_cp0_in.exc_code === ExcCodeConst.ADEL || io.ex_cp0_in.exc_code === ExcCodeConst.ADES) {
      badvaddr := io.ex_cp0_in.badvaddr
    }
  }

  for (i <- 0 to 1) {
    when(io.wb_cp0(i).regsel === 0.U && io.wb_cp0(i).wen) {
      when(io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_COUNT) {
        count := io.wb_cp0(i).wdata
      }

      when(io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_COMPARE) {
        compare := io.wb_cp0(i).wdata
        cause := Cat(cause(31), 0.B, cause(29, 0))
      }

      when(io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_EPC) {
        epc := io.wb_cp0(i).wdata
      }

      when(io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_ENTRYHI) {
        entryhi := Cat(io.wb_cp0(i).wdata(31, 13), 0.U(5.W), io.wb_cp0(i).wdata(7, 0))
      }

      when(io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_PAGEMASK) {
        pagemask := Cat(0.U(7.W), io.wb_cp0(i).wdata(24, 13), 0.U(13.W))
      }

      when(io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_ENTRYLO0) {
        entrylo0 := Cat(entrylo0(31, 26), io.wb_cp0(i).wdata(25, 0))
      }

      when(io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_ENTRYLO1) {
        entrylo1 := Cat(entrylo1(31, 26), io.wb_cp0(i).wdata(25, 0))
      }

      when(io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_INDEX) {
        index := Cat(index(31), 0.U((32 - log_n_tlb).W), io.wb_cp0(i).wdata(log_n_tlb - 1, 0))
      }
    }
  }
  when(io.ex_cp0_in.exception_occur) {
    when(!status_exl) { // 只有exl置0时更新epc
      epc := Mux(io.ex_cp0_in.is_delay_slot && io.ex_cp0_in.exc_code =/= ExcCodeConst.INT,
        io.ex_cp0_in.pc - 4.U, io.ex_cp0_in.pc)
    }
  }

  when(io.ex_cp0_in.exception_occur) {
    when(io.ex_cp0_in.exc_code === ExcCodeConst.TLBS || io.ex_cp0_in.exc_code === ExcCodeConst.TLBL ||
      io.ex_cp0_in.exc_code === ExcCodeConst.MOD) {
      entryhi := Cat(io.ex_cp0_in.entryhi_vpn2, entryhi(12, 0))
    }
  }

  // 没有对count的写行为时产生count++
  when(!((io.wb_cp0(0).regaddr === CP0Const.CP0_REGADDR_COUNT && io.wb_cp0(0).regsel === 0.U && io.wb_cp0(0).wen) ||
    (io.wb_cp0(1).regaddr === CP0Const.CP0_REGADDR_COUNT && io.wb_cp0(1).regsel === 0.U && io.wb_cp0(1).wen))) {
    when(tick) {
      count := count + 1.U
    }
  }
  // 没有对compare的写行为时才可能产生时钟中断
  when(!((io.wb_cp0(0).regaddr === CP0Const.CP0_REGADDR_COMPARE && io.wb_cp0(0).regsel === 0.U && io.wb_cp0(0).wen) ||
    (io.wb_cp0(1).regaddr === CP0Const.CP0_REGADDR_COMPARE && io.wb_cp0(1).regsel === 0.U && io.wb_cp0(1).wen))) {
    when(compare_eq_count) {
      cause := Cat(cause(31), if (timer_int_en) 1.B else 0.B, cause(29, 0))
    }
  }

  io.epc := epc
  io.cp0_ex_out.epc := epc
  io.cause_ip := Cat(cause_15_10, cause(9, 8))
  io.status_im := status(15, 8)
  io.cp0_ex_out.status_ie := status(0)


  for (i <- 0 to 1) {
    io.wb_cp0(i).rdata := Mux(io.wb_cp0(i).regsel === 0.U, MuxCase(0.U, Seq(
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_EPC) -> epc,
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_CAUSE) -> Cat(cause(31, 16), cause_15_10, cause(9, 0)),
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_STATUS) -> status,
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_COMPARE) -> compare,
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_COUNT) -> count,
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_BADVADDR) -> badvaddr,
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_ENTRYHI) -> entryhi,
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_ENTRYLO0) -> entrylo0,
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_ENTRYLO1) -> entrylo1,
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_INDEX) -> index,
      (io.wb_cp0(i).regaddr === CP0Const.CP0_REGADDR_PAGEMASK) -> pagemask,
    )), 0.U)
  }

  when(io.tlbp_cp0.is_tlbp) {
    index := Cat(!io.tlbp_cp0.found, 0.U((31 - log_n_tlb).W), io.tlbp_cp0.index)
  }
  io.cp0_ex_out.status_exl := status_exl
  io.cp0_ex_out.cp0_cause_ip := Cat(cause_15_10, cause(9, 8))
  io.cp0_ex_out.cp0_status_im := status(15, 8)
  io.cp0_ex_out.vpn2 := entryhi(31, 13)
  io.cp0_ex_out.asid := entryhi(7, 0)

  io.tlbwi_cp0.index := index(log_n_tlb - 1, 0)
  io.tlbwi_cp0.entrylo0 := entrylo0
  io.tlbwi_cp0.entrylo1 := entrylo1
  io.tlbwi_cp0.entryhi := entryhi
  io.tlbwi_cp0.pagemask := pagemask

  io.asid := entryhi(7, 0)
  when(io.tlbr_cp0.is_tlbr) {
    entryhi := io.tlbr_cp0.entryhi
    entrylo0 := io.tlbr_cp0.entrylo0
    entrylo1 := io.tlbr_cp0.entrylo1
    pagemask := io.tlbr_cp0.pagemask
  }
}
