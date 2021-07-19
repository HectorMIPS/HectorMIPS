package com.github.hectormips

import chisel3._
import chisel3.util.HasBlackBoxResource
import chisel3.util.experimental.forceName


class AXISRamLikeBridge extends HasBlackBoxResource {
  class AXISRamLikeBridgeIO extends Bundle {
    val clock            : Bool       = Input(Bool())
    val resetn           : Bool       = Input(Bool())
    val inst_sram_like_io: SRamLikeIO = Flipped(new SRamLikeIO(64))
    val data_sram_like_io: SRamLikeIO = Flipped(new SRamLikeIO)
    val axi_io           : AXIIO      = new AXIIO
  }

  val io: AXISRamLikeBridgeIO = IO(new AXISRamLikeBridgeIO)
  override val desiredName = s"cpu_axi_interface"

  forceName(io.clock, "clk")

  forceName(io.inst_sram_like_io.req, "inst_req")
  forceName(io.inst_sram_like_io.wr, "inst_wr")
  forceName(io.inst_sram_like_io.size, "inst_size")
  forceName(io.inst_sram_like_io.addr, "inst_addr")
  forceName(io.inst_sram_like_io.wdata, "inst_wdata")
  forceName(io.inst_sram_like_io.addr_ok, "inst_addr_ok")
  forceName(io.inst_sram_like_io.data_ok, "inst_data_ok")
  forceName(io.inst_sram_like_io.rdata, "inst_rdata")

  forceName(io.data_sram_like_io.req, "data_req")
  forceName(io.data_sram_like_io.wr, "data_wr")
  forceName(io.data_sram_like_io.size, "data_size")
  forceName(io.data_sram_like_io.addr, "data_addr")
  forceName(io.data_sram_like_io.wdata, "data_wdata")
  forceName(io.data_sram_like_io.addr_ok, "data_addr_ok")
  forceName(io.data_sram_like_io.data_ok, "data_data_ok")
  forceName(io.data_sram_like_io.rdata, "data_rdata")

  addResource("/cpu_axi_interface.v")
}
