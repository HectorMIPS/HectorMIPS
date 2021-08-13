package com.github.hectormips

import Chisel.Cat
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util.experimental.forceName
import com.github.hectormips.cache.access_judge.MemAccessJudge
import com.github.hectormips.cache.cache.Cache
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.axi_crossbar_2x1
import chisel3.util.experimental.forceName
import com.github.hectormips.tlb.TLB

class SocTopSRamLikeBundle extends Bundle {
  val axi_io   : AXIIO       = new AXIIO(1)
  val interrupt: UInt        = Input(UInt(6.W))
  val debug    : DebugBundle = new DebugBundle
  forceName(debug.debug_wb_pc, "debug_wb_pc")
  forceName(debug.debug_wb_rf_wen, "debug_wb_rf_wen")
  forceName(debug.debug_wb_rf_wnum, "debug_wb_rf_wnum")
  forceName(debug.debug_wb_rf_wdata, "debug_wb_rf_wdata")
  forceName(debug.debug_flush, "debug_fifo_flush")
  forceName(debug.debug_predict_success, "debug_predict_success")
  forceName(debug.debug_predict_fail, "debug_predict_fail")
}


// 使用axi的Soc顶层
class SocTopAXI(cache_all: Boolean = false, timer_int_en: Boolean = true) extends Module {
  val io: SocTopSRamLikeBundle = IO(new SocTopSRamLikeBundle)
  withReset(!reset.asBool()) {
    val n_tlb                       = 32
    val cpu_top  : CpuTopSRamLike   = Module(new CpuTopSRamLike(0xbfbffffcL, 0, n_tlb, timer_int_en))
    val cache    : Cache            = Module(new Cache(new CacheConfig()))
    val crossbar : axi_crossbar_2x1 = Module(new axi_crossbar_2x1)
    val mem_judge: MemAccessJudge   = Module(new MemAccessJudge(cache_all.B))
    val tlb      : TLB              = Module(new TLB(n_tlb))

    io.axi_io.force_name()
    cpu_top.io.interrupt := io.interrupt
    cpu_top.io.tlb <> tlb.io.tlb_inst_io

    io.debug <> cpu_top.io.debug

    cache.io.tlb0 <> tlb.io.s0
    cache.io.tlb1 <> tlb.io.s1

    mem_judge.io.inst <> cpu_top.io.inst_sram_like_io

    mem_judge.io.data(0) <> cpu_top.io.data_sram_like_io(0)
    mem_judge.io.data(1) <> cpu_top.io.data_sram_like_io(1)


    mem_judge.io.mapped_inst <> cache.io.icache
    mem_judge.io.mapped_data <> cache.io.dcache
    mem_judge.io.unmapped_data <> cache.io.uncached
    mem_judge.io.unmapped_inst <> cache.io.uncache_inst
    cache.io.inst_addr_is_mapped := mem_judge.io.inst_addr_is_mapped
    cache.io.data_addr_is_mapped := mem_judge.io.data_addr_is_mapped
    cache.io.inst_unmap_should_cache := mem_judge.io.inst_unmap_should_cache
    cache.io.data_unmap_should_cache := mem_judge.io.data_unmap_should_cache

    cache.io.axi <> crossbar.io.in

    crossbar.io.aclk := clock
    crossbar.io.aresetn := reset.asBool() // reset在上面取反了
    crossbar.io.s_arqos := 0.U
    crossbar.io.s_awqos := 0.U
    io.axi_io <> crossbar.io.out


  }
  forceName(clock, "aclk")
  forceName(reset, "aresetn")
  forceName(io.interrupt, "ext_int")
  override val desiredName = s"mycpu_top"
}

object SocTopAXI extends App {
  // 生成根目录下的mycpu_top.v
  (new ChiselStage).emitVerilog(new SocTopAXI)
  // 生成开启加速的func test文件
  (new ChiselStage).emitVerilog(new SocTopAXI(true, false), "--target-dir build/func".split(" +"))
  // 生成不开启加速的perf test文件
  (new ChiselStage).emitVerilog(new SocTopAXI(false, false), "--target-dir build/perf".split(" +"))
  // 生成不开启加速的sys test文件
  (new ChiselStage).emitVerilog(new SocTopAXI(false), "--target-dir build/sys".split(" +"))
}