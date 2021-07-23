package com.github.hectormips

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util.experimental.forceName
import com.github.hectormips.cache.access_judge.MemAccessJudge
import com.github.hectormips.cache.cache.Cache
import com.github.hectormips.cache.dcache.DCache
import com.github.hectormips.cache.icache.ICache
import com.github.hectormips.cache.setting.CacheConfig


class SocTopSRamLikeBundle extends Bundle {
  val axi_io   : AXIIO       = new AXIIO(1)
  val interrupt: UInt        = Input(UInt(6.W))
  val debug    : DebugBundle = new DebugBundle
}
class axi_crossbar_2x1 extends BlackBox{
  val io = IO(new Bundle{
    val aclk    = Input(Clock())
    val aresetn = Input(Bool())
    //64 bit 输入
    val in      = Flipped(new AXIIO(3))
    val arqos   = Input(UInt(8.W))
    //32 bit 输出
    val out     = new AXIIO(1)
  })
  forceName(io.in.arid, "s_axi_arid")
  forceName(io.in.araddr, "s_axi_araddr")
  forceName(io.in.arlen, "s_axi_arlen")
  forceName(io.in.arsize, "s_axi_arsize")
  forceName(io.in.arburst, "s_axi_arburst")
  forceName(io.in.arlock, "s_axi_arlock")
  forceName(io.in.arcache, "s_axi_arcache")
  forceName(io.in.arprot, "s_axi_arprot")
  forceName(io.in.arvalid, "s_axi_arvalid")
  forceName(io.in.arready, "s_axi_arready")
  forceName(io.in.rid, "s_axi_rid")
  forceName(io.in.rdata, "s_axi_rdata")
  forceName(io.in.rresp, "s_axi_rresp")
  forceName(io.in.rlast, "s_axi_rlast")
  forceName(io.in.rvalid, "s_axi_rvalid")
  forceName(io.in.rready, "s_axi_rready")
  forceName(io.in.awid, "s_axi_awid")
  forceName(io.in.awaddr, "s_axi_awaddr")
  forceName(io.in.awlen, "s_axi_awlen")
  forceName(io.in.awsize, "s_axi_awsize")
  forceName(io.in.awburst, "s_axi_awburst")
  forceName(io.in.awlock, "s_axi_awlock")
  forceName(io.in.awcache, "s_axi_awcache")
  forceName(io.in.awprot, "s_axi_awprot")
  forceName(io.in.awvalid, "s_axi_awvalid")
  forceName(io.in.awready, "s_axi_awready")
  forceName(io.in.wid, "s_axi_wid")
  forceName(io.in.wdata, "s_axi_wdata")
  forceName(io.in.wstrb, "s_axi_wstrb")
  forceName(io.in.wlast, "s_axi_wlast")
  forceName(io.in.wvalid, "s_axi_wvalid")
  forceName(io.in.wready, "s_axi_wready")
  forceName(io.in.bid, "s_axi_bid")
  forceName(io.in.bresp, "s_axi_bresp")
  forceName(io.in.bvalid, "s_axi_bvalid")
  forceName(io.in.bready, "s_axi_bready")
  forceName(io.arqos,"s_axi_arqos")


  forceName(io.out.arid, "m_axi_arid")
  forceName(io.out.araddr, "m_axi_araddr")
  forceName(io.out.arlen, "m_axi_arlen")
  forceName(io.out.arsize, "m_axi_arsize")
  forceName(io.out.arburst, "m_axi_arburst")
  forceName(io.out.arlock, "m_axi_arlock")
  forceName(io.out.arcache, "m_axi_arcache")
  forceName(io.out.arprot, "m_axi_arprot")
  forceName(io.out.arvalid, "m_axi_arvalid")
  forceName(io.out.arready, "m_axi_arready")
  forceName(io.out.rid, "m_axi_rid")
  forceName(io.out.rdata, "m_axi_rdata")
  forceName(io.out.rresp, "m_axi_rresp")
  forceName(io.out.rlast, "m_axi_rlast")
  forceName(io.out.rvalid, "m_axi_rvalid")
  forceName(io.out.rready, "m_axi_rready")
  forceName(io.out.awid, "m_axi_awid")
  forceName(io.out.awaddr, "m_axi_awaddr")
  forceName(io.out.awlen, "m_axi_awlen")
  forceName(io.out.awsize, "m_axi_awsize")
  forceName(io.out.awburst, "m_axi_awburst")
  forceName(io.out.awlock, "m_axi_awlock")
  forceName(io.out.awcache, "m_axi_awcache")
  forceName(io.out.awprot, "m_axi_awprot")
  forceName(io.out.awvalid, "m_axi_awvalid")
  forceName(io.out.awready, "m_axi_awready")
  forceName(io.out.wid, "m_axi_wid")
  forceName(io.out.wdata, "m_axi_wdata")
  forceName(io.out.wstrb, "m_axi_wstrb")
  forceName(io.out.wlast, "m_axi_wlast")
  forceName(io.out.wvalid, "m_axi_wvalid")
  forceName(io.out.wready, "m_axi_wready")
  forceName(io.out.bid, "m_axi_bid")
  forceName(io.out.bready, "m_axi_bready")
  forceName(io.out.bresp, "m_axi_bresp")
  forceName(io.out.bvalid, "m_axi_bvalid")
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

    cpu_top.io.data_sram_like_io <> mem_judge.io.data

    mem_judge.io.cached_inst <> cache.io.icache
    mem_judge.io.cached_data <> cache.io.dcache
    mem_judge.io.uncached_data <> cache.io.uncached

    cache.io.axi <> crossbar.io.in

    crossbar.io.aclk := clock
    crossbar.io.aresetn := reset.asBool() // reset取反了
    crossbar.io.arqos := 0.U
    io.axi_io.arid := crossbar.io.out.arid
    io.axi_io.araddr := crossbar.io.out.araddr
    io.axi_io.arlen := crossbar.io.out.arlen
    io.axi_io.arsize := crossbar.io.out.arsize
    io.axi_io.arburst := crossbar.io.out.arburst
    io.axi_io.arlock := crossbar.io.out.arlock
    io.axi_io.arcache := crossbar.io.out.arcache
    io.axi_io.arprot  := crossbar.io.out.arprot
    io.axi_io.arvalid := crossbar.io.out.arvalid
    crossbar.io.out.arready := io.axi_io.arready

    crossbar.io.out.rid :=  io.axi_io.rid
    crossbar.io.out.rdata := io.axi_io.rdata
    crossbar.io.out.rresp := io.axi_io.rresp
    crossbar.io.out.rlast :=  io.axi_io.rlast
    crossbar.io.out.rvalid :=  io.axi_io.rvalid
    io.axi_io.rready := crossbar.io.out.rready

    io.axi_io.awid := crossbar.io.out.awid
    io.axi_io.awaddr := crossbar.io.out.awaddr
    io.axi_io.awlen := crossbar.io.out.awlen
    io.axi_io.awsize := crossbar.io.out.awsize
    io.axi_io.awburst := crossbar.io.out.awburst
    io.axi_io.awlock := crossbar.io.out.awlock
    io.axi_io.awcache := crossbar.io.out.awcache
    io.axi_io.awprot := crossbar.io.out.awprot
    io.axi_io.awvalid := crossbar.io.out.awvalid
    crossbar.io.out.awready := io.axi_io.awready

    io.axi_io.wid := crossbar.io.out.wid
    io.axi_io.wdata := crossbar.io.out.wdata
    io.axi_io.wstrb := crossbar.io.out.wstrb
    io.axi_io.wlast := crossbar.io.out.wlast
    io.axi_io.wvalid := crossbar.io.out.wvalid
    crossbar.io.out.wready := io.axi_io.wready

    crossbar.io.out.bid   := io.axi_io.bid
    crossbar.io.out.bresp := io.axi_io.bresp
    crossbar.io.out.bvalid :=  io.axi_io.bvalid
    io.axi_io.bready := crossbar.io.out.bready



  }
  forceName(clock, "aclk")
  forceName(reset, "aresetn")
  forceName(io.interrupt, "ext_int")
  override val desiredName = s"mycpu_top"
}

object SocTopAXI extends App {
  (new ChiselStage).emitVerilog(new SocTopAXI)
}