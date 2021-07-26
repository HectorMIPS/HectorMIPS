package com.github.hectormips

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util.experimental.forceName
import com.github.hectormips.cache.access_judge.MemAccessJudge
import com.github.hectormips.cache.cache.Cache
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.axi_crossbar_2x1


class SocTopSRamLikeBundle extends Bundle {
  val axi_io   : AXIIO       = new AXIIO(1)
  val interrupt: UInt        = Input(UInt(6.W))
  val debug    : DebugBundle = new DebugBundle
}



// 使用axi的Soc顶层
class SocTopAXI extends Module {
  val io: SocTopSRamLikeBundle = IO(new SocTopSRamLikeBundle)
  withReset(!reset.asBool()) {
    val cpu_top: CpuTopSRamLike = Module(new CpuTopSRamLike(0xbfbffffcL, 0))
    val cache  : Cache          = Module(new Cache(new CacheConfig()))
    val crossbar : axi_crossbar_2x1 =  Module(new axi_crossbar_2x1)
    val mem_judge: MemAccessJudge = Module(new MemAccessJudge)


    io.axi_io.force_name()
    cpu_top.io.interrupt := io.interrupt
    io.debug := cpu_top.io.debug

    // TODO: 以后高32位要加上
    mem_judge.io.inst.req := cpu_top.io.inst_sram_like_io.req
    mem_judge.io.inst.wr := cpu_top.io.inst_sram_like_io.wr
    mem_judge.io.inst.size := cpu_top.io.inst_sram_like_io.size
    mem_judge.io.inst.addr := cpu_top.io.inst_sram_like_io.addr
    mem_judge.io.inst.wdata := cpu_top.io.inst_sram_like_io.wdata
    mem_judge.io.inst.wr := cpu_top.io.inst_sram_like_io.wr
    cpu_top.io.inst_sram_like_io.addr_ok := mem_judge.io.inst.addr_ok
    cpu_top.io.inst_sram_like_io.data_ok := mem_judge.io.inst.data_ok
    cpu_top.io.inst_sram_like_io.rdata := mem_judge.io.inst.rdata(31,0)
    //    cpu_top.io.inst_sram_like_io <> mem_judge.io.inst

    cpu_top.io.data_sram_like_io <> mem_judge.io.data(0)
    mem_judge.io.data(1):=DontCare



    mem_judge.io.cached_inst <> cache.io.icache
    mem_judge.io.cached_data <> cache.io.dcache
    mem_judge.io.uncached_data <> cache.io.uncached

    cache.io.axi <> crossbar.io.in

    crossbar.io.aclk := clock
    crossbar.io.aresetn := reset.asBool() // reset在上面取反了
    crossbar.io.s_arqos := 0.U
    crossbar.io.s_awqos := 0.U
    io.axi_io <> crossbar.io.out

//    io.axi_io.arid := crossbar.io.out.arid
//    io.axi_io.araddr := crossbar.io.out.araddr
//    io.axi_io.arlen := crossbar.io.out.arlen
//    io.axi_io.arsize := crossbar.io.out.arsize
//    io.axi_io.arburst := crossbar.io.out.arburst
//    io.axi_io.arlock := crossbar.io.out.arlock
//    io.axi_io.arcache := crossbar.io.out.arcache
//    io.axi_io.arprot  := crossbar.io.out.arprot
//    io.axi_io.arvalid := crossbar.io.out.arvalid
//    crossbar.io.out.arready := io.axi_io.arready
//
//    crossbar.io.out.rid :=  io.axi_io.rid
//    crossbar.io.out.rdata := io.axi_io.rdata
//    crossbar.io.out.rresp := io.axi_io.rresp//    crossbar.io.out.rlast :=  io.axi_io.rlast
//    crossbar.io.out.rvalid :=  io.axi_io.rvalid
//    io.axi_io.rready := crossbar.io.out.rready
//
//    io.axi_io.awid := crossbar.io.out.awid
//    io.axi_io.awaddr := crossbar.io.out.awaddr
//    io.axi_io.awlen := crossbar.io.out.awlen
//    io.axi_io.awsize := crossbar.io.out.awsize
//    io.axi_io.awburst := crossbar.io.out.awburst
//    io.axi_io.awlock := crossbar.io.out.awlock
//    io.axi_io.awcache := crossbar.io.out.awcache
//    io.axi_io.awprot := crossbar.io.out.awprot
//    io.axi_io.awvalid := crossbar.io.out.awvalid
//    crossbar.io.out.awready := io.axi_io.awready
//
//    io.axi_io.wid := crossbar.io.out.wid
//    io.axi_io.wdata := crossbar.io.out.wdata
//    io.axi_io.wstrb := crossbar.io.out.wstrb
//    io.axi_io.wlast := crossbar.io.out.wlast
//    io.axi_io.wvalid := crossbar.io.out.wvalid
//    crossbar.io.out.wready := io.axi_io.wready
//
//    crossbar.io.out.bid   := io.axi_io.bid
//    crossbar.io.out.bresp := io.axi_io.bresp
//    crossbar.io.out.bvalid :=  io.axi_io.bvalid
//    io.axi_io.bready := crossbar.io.out.bready


  }
  forceName(clock, "aclk")
  forceName(reset, "aresetn")
  forceName(io.interrupt, "ext_int")
  override val desiredName = s"mycpu_top"
}

object SocTopAXI extends App {
  (new ChiselStage).emitVerilog(new SocTopAXI)
}