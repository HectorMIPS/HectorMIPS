package com.github.hectormips

import chisel3._
import chisel3.util.experimental.forceName

class SRamBridge extends BlackBox {
  val io: SRamLikeIO = IO(Flipped(new SRamLikeIO))
  forceName(io.clk, "clk")
  forceName(io.req, "req")
  forceName(io.wr, "wr")
  forceName(io.size, "size")
  forceName(io.addr, "addr")
  forceName(io.wdata, "wdata")
  forceName(io.addr_ok, "addr_ok")
  forceName(io.data_ok, "data_ok")
  forceName(io.rdata, "rdata")
}
