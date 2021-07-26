package com.github.hectormips

import chisel3._
import chisel3.util.experimental.forceName

/**
 * 没有WID的接口，给crossbar
 * @param num AXI 接口的数量，一般是1
 */
class AXIIOWithoutWid(num:Int = 1) extends Bundle {
  // axi
  val arid   : UInt = Output(UInt((4*num).W))
  val araddr : UInt = Output(UInt((32*num).W))
  val arlen  : UInt = Output(UInt((4*num).W)) // axi3 arlen长度是3，如果要
  val arsize : UInt = Output(UInt((3*num).W))
  val arburst: UInt = Output(UInt((2*num).W))
  val arlock : UInt = Output(UInt((2*num).W))
  val arcache: UInt = Output(UInt((4*num).W))
  val arprot : UInt = Output(UInt((3*num).W))
  val arvalid: UInt = Output(UInt((1*num).W))
  val arready: UInt = Input(UInt((1*num).W))

  val rid    : UInt = Input(UInt((4*num).W))
  val rdata  : UInt = Input(UInt((32*num).W))
  val rresp  : UInt = Input(UInt((2*num).W))
  val rlast  : UInt = Input(UInt((1*num).W))
  val rvalid : UInt = Input(UInt((1*num).W))
  val rready : UInt = Output(UInt((1*num).W))

  val awid   : UInt = Output(UInt((4*num).W))
  val awaddr : UInt = Output(UInt((32*num).W))
  val awlen  : UInt = Output(UInt((4*num).W)) //AXI3
  val awsize : UInt = Output(UInt((3*num).W))
  val awburst: UInt = Output(UInt((2*num).W))
  val awlock : UInt = Output(UInt((2*num).W))
  val awcache: UInt = Output(UInt((4*num).W))
  val awprot : UInt = Output(UInt((3*num).W))
  val awvalid: UInt = Output(UInt((1*num).W))
  val awready: UInt = Input(UInt((1*num).W))

  val wdata  : UInt = Output(UInt((32*num).W))
  val wstrb  : UInt = Output(UInt((4*num).W))
  val wlast  : UInt = Output(UInt((1*num).W))
  val wvalid : UInt = Output(UInt((1*num).W))
  val wready : UInt = Input(UInt((1*num).W))
  val bid    : UInt = Input(UInt((4*num).W))
  val bresp  : UInt = Input(UInt((2*num).W))
  val bvalid : UInt = Input(UInt((1*num).W))
  val bready : UInt = Output(UInt((1*num).W))
  def force_name():Unit= {
    forceName(arid, "arid")
    forceName(araddr, "araddr")
    forceName(arlen, "arlen")
    forceName(arsize, "arsize")
    forceName(arburst, "arburst")
    forceName(arlock, "arlock")
    forceName(arcache, "arcache")
    forceName(arprot, "arprot")
    forceName(arvalid, "arvalid")
    forceName(arready, "arready")
    forceName(rid, "rid")
    forceName(rdata, "rdata")
    forceName(rresp, "rresp")
    forceName(rlast, "rlast")
    forceName(rvalid, "rvalid")
    forceName(rready, "rready")
    forceName(awid, "awid")
    forceName(awaddr, "awaddr")
    forceName(awlen, "awlen")
    forceName(awsize, "awsize")
    forceName(awburst, "awburst")
    forceName(awlock, "awlock")
    forceName(awcache, "awcache")
    forceName(awprot, "awprot")
    forceName(awvalid, "awvalid")
    forceName(awready, "awready")
    forceName(wdata, "wdata")
    forceName(wstrb, "wstrb")
    forceName(wlast, "wlast")
    forceName(wvalid, "wvalid")
    forceName(wready, "wready")
    forceName(bid, "bid")
    forceName(bresp, "bresp")
    forceName(bvalid, "bvalid")
    forceName(bready, "bready")
  }
}

/**
 *
 * @param num AXI 接口的数量，一般是1
 */
class AXIIO(num:Int) extends AXIIOWithoutWid(num:Int) {
  // axi
  val wid    : UInt = Output(UInt((4*num).W))

  override  def force_name():Unit= {
    forceName(arid, "arid")
    forceName(araddr, "araddr")
    forceName(arlen, "arlen")
    forceName(arsize, "arsize")
    forceName(arburst, "arburst")
    forceName(arlock, "arlock")
    forceName(arcache, "arcache")
    forceName(arprot, "arprot")
    forceName(arvalid, "arvalid")
    forceName(arready, "arready")
    forceName(rid, "rid")
    forceName(rdata, "rdata")
    forceName(rresp, "rresp")
    forceName(rlast, "rlast")
    forceName(rvalid, "rvalid")
    forceName(rready, "rready")
    forceName(awid, "awid")
    forceName(awaddr, "awaddr")
    forceName(awlen, "awlen")
    forceName(awsize, "awsize")
    forceName(awburst, "awburst")
    forceName(awlock, "awlock")
    forceName(awcache, "awcache")
    forceName(awprot, "awprot")
    forceName(awvalid, "awvalid")
    forceName(awready, "awready")
    forceName(wid, "wid")
    forceName(wdata, "wdata")
    forceName(wstrb, "wstrb")
    forceName(wlast, "wlast")
    forceName(wvalid, "wvalid")
    forceName(wready, "wready")
    forceName(bid, "bid")
    forceName(bresp, "bresp")
    forceName(bvalid, "bvalid")
    forceName(bready, "bready")
  }
}

