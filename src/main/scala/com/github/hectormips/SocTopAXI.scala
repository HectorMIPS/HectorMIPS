package com.github.hectormips

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util.experimental.forceName
import com.github.hectormips.cache.cache.Cache
import com.github.hectormips.cache.dcache.DCache
import com.github.hectormips.cache.icache.ICache
import com.github.hectormips.cache.setting.CacheConfig


class SocTopSRamLikeBundle extends Bundle {
  val axi_io   : AXIIO       = new AXIIO
  val interrupt: UInt        = Input(UInt(6.W))
  val debug    : DebugBundle = new DebugBundle
}

// 使用axi的Soc顶层
class SocTopAXI extends Module {
  val io: SocTopSRamLikeBundle = IO(new SocTopSRamLikeBundle)
  withReset(!reset.asBool()) {
    val cpu_top: CpuTopSRamLike = Module(new CpuTopSRamLike(0xbfbffffcL, 0))
    val cache  : Cache          = Module(new Cache(new CacheConfig()))

    cpu_top.io.interrupt := io.interrupt
    io.debug := cpu_top.io.debug

    io.axi_io <> cache.io.axi

    cpu_top.io.inst_sram_like_io <> cache.io.icache
    cpu_top.io.data_sram_like_io <> cache.io.dcache
  }
  forceName(clock, "aclk")
  forceName(reset, "aresetn")
  forceName(io.interrupt, "ext_int")
  override val desiredName = s"mycpu_top"
}

object SocTopAXI extends App {
  (new ChiselStage).emitVerilog(new SocTopAXI)
}