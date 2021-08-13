package com.github.hectormips.pipeline

import chisel3.stage.ChiselStage
import chisel3.{Mux, _}
import chisel3.util._
import com.github.hectormips.pipeline.cp0.{CP0Const, TLBPCP0Bundle, TLBRCP0Bundle, TLBWICP0Bundle}
import com.github.hectormips.tlb.{TLBPBundle, TLBRBundle, TLBWIBundle}

class MemoryWriteBackBundle(n_tlb: Int) extends WithVEI {
  val regfile_waddr_sel_ms_wb     : RegFileWAddrSel.Type = RegFileWAddrSel()
  val inst_rd_ms_wb               : UInt                 = UInt(5.W)
  val inst_rt_ms_wb               : UInt                 = UInt(5.W)
  val regfile_we_ms_wb            : UInt                 = UInt(4.W)
  val regfile_wdata_ms_wb         : UInt                 = UInt(32.W)
  val pc_ms_wb                    : UInt                 = UInt(32.W)
  val cp0_wen_ms_wb               : Bool                 = Bool()
  val cp0_addr_ms_wb              : UInt                 = UInt(5.W)
  val cp0_sel_ms_wb               : UInt                 = UInt(3.W)
  val regfile_wdata_from_cp0_ms_wb: Bool                 = Bool()
  val tlbp                        : TLBPCP0Bundle        = new TLBPCP0Bundle(n_tlb)
  val tlbr                        : Bool                 = Bool()
  val tlbwi                       : Bool                 = Bool()

  override def defaults(): Unit = {
    super.defaults()
    regfile_waddr_sel_ms_wb := RegFileWAddrSel.inst_rt
    inst_rd_ms_wb := 0.U
    inst_rt_ms_wb := 0.U
    regfile_we_ms_wb := 0.B
    regfile_wdata_ms_wb := 0.U
    pc_ms_wb := 0.U
    cp0_wen_ms_wb := 0.B
    cp0_addr_ms_wb := 0.U
    cp0_sel_ms_wb := 0.U
    regfile_wdata_from_cp0_ms_wb := 0.B
    tlbp.defaults()
    tlbr := 0.B
    tlbwi := 0.B
  }
}

class InsWriteBackBundle(n_tlb: Int) extends WithAllowin {

  val ms_wb_in: Vec[MemoryWriteBackBundle] = Input(Vec(2, new MemoryWriteBackBundle(n_tlb)))

  val regfile_wdata: Vec[UInt] = Output(Vec(2, UInt(32.W)))
  val regfile_waddr: Vec[UInt] = Output(Vec(2, UInt(5.W)))
  val regfile_wen  : Vec[UInt] = Output(Vec(2, UInt(4.W)))
  val pc_wb        : Vec[UInt] = Output(Vec(2, UInt(32.W)))

  val cp0_rdata: Vec[UInt] = Input(Vec(2, UInt(32.W)))
  val cp0_wen  : Vec[Bool] = Output(Vec(2, Bool()))
  val cp0_addr : Vec[UInt] = Output(Vec(2, UInt(5.W)))
  val cp0_wdata: Vec[UInt] = Output(Vec(2, UInt(32.W)))
  val cp0_sel  : Vec[UInt] = Output(Vec(2, UInt(3.W)))

  val bypass_wb_id           : Vec[BypassMsgBundle] = Output(Vec(2, new BypassMsgBundle))
  val cp0_hazard_bypass_wb_ex: Vec[CP0HazardBypass] = Output(Vec(2, new CP0HazardBypass))
  val tlbp_cp0               : TLBPCP0Bundle        = Output(new TLBPCP0Bundle(n_tlb))
  val tlbwi_cp0              : TLBWICP0Bundle       = Input(new TLBWICP0Bundle(n_tlb))
  val tlbr_cp0               : TLBRCP0Bundle        = Output(new TLBRCP0Bundle)
  val wb_tlbwi_out           : TLBWIBundle          = Flipped(new TLBWIBundle(n_tlb))
  val wb_tlbr                : TLBRBundle           = Flipped(new TLBRBundle(n_tlb))
}

class InsWriteBack(n_tlb: Int) extends Module {
  val io: InsWriteBackBundle = IO(new InsWriteBackBundle(n_tlb))
  for (i <- 0 to 1) {
    io.regfile_wen(i) := io.ms_wb_in(i).regfile_we_ms_wb & VecInit(Seq.fill(4)(io.ms_wb_in(i).bus_valid)).asUInt()
    io.regfile_waddr(i) := 0.U
    switch(io.ms_wb_in(i).regfile_waddr_sel_ms_wb) {
      is(RegFileWAddrSel.inst_rd) {
        io.regfile_waddr(i) := io.ms_wb_in(i).inst_rd_ms_wb
      }
      is(RegFileWAddrSel.inst_rt) {
        io.regfile_waddr(i) := io.ms_wb_in(i).inst_rt_ms_wb
      }
      is(RegFileWAddrSel.const_31) {
        io.regfile_waddr(i) := 31.U
      }
    }
    val regfile_wdata: UInt = Mux(io.ms_wb_in(i).regfile_wdata_from_cp0_ms_wb, io.cp0_rdata(i),
      io.ms_wb_in(i).regfile_wdata_ms_wb)
    // mfc0的写入值直接在写回阶段取出，简化前递逻辑
    io.regfile_wdata(i) := regfile_wdata

    val bus_valid: Bool = Wire(Bool())
    bus_valid := !reset.asBool() && io.ms_wb_in(i).bus_valid

    io.this_allowin := !reset.asBool()
    io.pc_wb(i) := io.ms_wb_in(i).pc_ms_wb

    io.cp0_wen(i) := io.ms_wb_in(i).cp0_wen_ms_wb && io.ms_wb_in(i).bus_valid
    io.cp0_addr(i) := io.ms_wb_in(i).cp0_addr_ms_wb
    io.cp0_sel(i) := io.ms_wb_in(i).cp0_sel_ms_wb
    io.cp0_wdata(i) := io.ms_wb_in(i).regfile_wdata_ms_wb

    io.bypass_wb_id(i).bus_valid := bus_valid && io.ms_wb_in(i).regfile_we_ms_wb =/= 0.U
    // 不对lwl和swl进行前递
    io.bypass_wb_id(i).data_valid := io.ms_wb_in(0).regfile_we_ms_wb === 0xf.U
    io.bypass_wb_id(i).reg_data := regfile_wdata
    io.bypass_wb_id(i).reg_addr := Mux1H(Seq(
      (io.ms_wb_in(i).regfile_waddr_sel_ms_wb === RegFileWAddrSel.inst_rd) -> io.ms_wb_in(i).inst_rd_ms_wb,
      (io.ms_wb_in(i).regfile_waddr_sel_ms_wb === RegFileWAddrSel.inst_rt) -> io.ms_wb_in(i).inst_rt_ms_wb,
      (io.ms_wb_in(i).regfile_waddr_sel_ms_wb === RegFileWAddrSel.const_31) -> 31.U))

    io.cp0_hazard_bypass_wb_ex(i).bus_valid := bus_valid
    io.cp0_hazard_bypass_wb_ex(i).cp0_en := io.ms_wb_in(i).regfile_wdata_from_cp0_ms_wb || io.ms_wb_in(i).cp0_wen_ms_wb
    io.cp0_hazard_bypass_wb_ex(i).cp0_wen := io.ms_wb_in(i).cp0_wen_ms_wb || io.ms_wb_in(i).tlbr
    io.cp0_hazard_bypass_wb_ex(i).tlb_wen := io.ms_wb_in(i).tlbwi || io.ms_wb_in(i).tlbr
  }
  io.tlbp_cp0 := io.ms_wb_in(0).tlbp
  io.tlbr_cp0.is_tlbr := io.ms_wb_in(0).tlbp.is_tlbp && io.ms_wb_in(0).bus_valid
  io.wb_tlbwi_out.we := io.ms_wb_in(0).tlbwi && io.ms_wb_in(0).bus_valid
  io.wb_tlbwi_out.w_index := io.tlbwi_cp0.index
  io.wb_tlbwi_out.w_vpn2 := io.tlbwi_cp0.entryhi(31, 13)
  io.wb_tlbwi_out.w_asid := io.tlbwi_cp0.entryhi(7, 0)

  io.wb_tlbwi_out.w_g := io.tlbwi_cp0.entrylo0(0) & io.tlbwi_cp0.entrylo1(0)
  io.wb_tlbwi_out.w_pfn0 := io.tlbwi_cp0.entrylo0(25, 6)
  io.wb_tlbwi_out.w_c0 := io.tlbwi_cp0.entrylo0(5, 3)
  io.wb_tlbwi_out.w_d0 := io.tlbwi_cp0.entrylo0(2)
  io.wb_tlbwi_out.w_v0 := io.tlbwi_cp0.entrylo0(1)

  io.wb_tlbwi_out.w_pfn1 := io.tlbwi_cp0.entrylo1(25, 6)
  io.wb_tlbwi_out.w_c1 := io.tlbwi_cp0.entrylo1(5, 3)
  io.wb_tlbwi_out.w_d1 := io.tlbwi_cp0.entrylo1(2)
  io.wb_tlbwi_out.w_v1 := io.tlbwi_cp0.entrylo1(1)

  io.wb_tlbwi_out.w_pagemask := io.tlbwi_cp0.pagemask(24, 13)

  io.tlbr_cp0.is_tlbr := io.ms_wb_in(0).tlbr && io.ms_wb_in(0).bus_valid
  io.tlbr_cp0.entryhi := Cat(io.wb_tlbr.r_vpn2, 0.U(5.W), io.wb_tlbr.r_asid)
  io.tlbr_cp0.entrylo0 := Cat(0.U(6.W), io.wb_tlbr.r_pfn0, io.wb_tlbr.r_c0, io.wb_tlbr.r_d0, io.wb_tlbr.r_v0, io.wb_tlbr.r_g)
  io.tlbr_cp0.entrylo1 := Cat(0.U(6.W), io.wb_tlbr.r_pfn1, io.wb_tlbr.r_c1, io.wb_tlbr.r_d1, io.wb_tlbr.r_v1, io.wb_tlbr.r_g)
  io.tlbr_cp0.pagemask := Cat(0.U(7.W), io.wb_tlbr.r_pagemask, 0.U(13.W))
  io.wb_tlbr.r_index := io.tlbwi_cp0.index
}

object InsWriteBack extends App {
  (new ChiselStage).emitVerilog(new InsWriteBack(16))
}
