package com.github.hectormips

import chisel3._
import chisel3.util.experimental.forceName

class axi_crossbar_2x1 extends BlackBox {
  val io = IO(new Bundle {
    val aclk = Input(Clock())
    val aresetn = Input(Bool())
    //64 bit 输入
    val in = Flipped(new AXIIO(3))
    val s_arqos = Input(UInt(12.W))
    val s_awqos = Input(UInt(12.W))
    val m_arqos = Output(UInt(4.W))
    val m_awqos = Output(UInt(4.W))
    //32 bit 输出
    val out = new AXIIO(1)
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
  forceName(io.s_arqos, "s_axi_arqos")
  forceName(io.s_awqos, "s_axi_awqos")


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

  forceName(io.m_arqos, "m_axi_arqos") // 这两个端口不用
  forceName(io.m_awqos, "m_axi_awqos") // 这两个端口不用
}