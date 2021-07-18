package com.github.hectormips

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util.experimental.forceName


class SocTopSRamLikeBundle extends Bundle {
  val axi_io   : AXIIO       = new AXIIO
  val interrupt: UInt        = Input(UInt(6.W))
  val debug    : DebugBundle = new DebugBundle
}

// 使用axi的Soc顶层
class SocTopAXI extends Module {
  val io: SocTopSRamLikeBundle = IO(new SocTopSRamLikeBundle)
  withReset(!reset.asBool()) {
    val cpu_top             : CpuTopSRamLike    = Module(new CpuTopSRamLike(0xbfbffffcL, 0))
    val axi_sram_like_bridge: AXISRamLikeBridge = Module(new AXISRamLikeBridge)

    cpu_top.io.interrupt := io.interrupt
    io.debug := cpu_top.io.debug
    io.axi_io <> axi_sram_like_bridge.io.axi_io
    axi_sram_like_bridge.io.resetn := reset
    axi_sram_like_bridge.io.clock := clock.asBool()

    cpu_top.io.inst_sram_like_io <> axi_sram_like_bridge.io.inst_sram_like_io
    cpu_top.io.data_sram_like_io <> axi_sram_like_bridge.io.data_sram_like_io
  }
  forceName(clock, "aclk")
  forceName(reset, "aresetn")
  forceName(io.interrupt, "ext_int")
  override val desiredName = s"mycpu_top"
}

object SocTopAXI extends App {
  (new ChiselStage).emitVerilog(new SocTopAXI)
}