package com.github.hectormips.pipeline

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile

class SocTopBundle extends Bundle {
  val debug_wb_pc      : UInt = Output(UInt(32.W))
  val debug_wb_rf_wen  : UInt = Output(UInt(4.W))
  val debug_wb_rf_wnum : UInt = Output(UInt(5.W))
  val debug_wb_rf_wdata: UInt = Output(UInt(32.W))
}


class SocTop(ram_filename: String) extends MultiIOModule {
  val io      : SocTopBundle = IO(new SocTopBundle())
  val cpu     : CpuTop       = Module(new CpuTop(0xfffffffc, 5))
  val inst_ram: SyncRam      = Module(new SyncRam(0x2000L))
  val data_ram: SyncRam      = Module(new SyncRam(0x2000L))
  loadMemoryFromFile(inst_ram.ram, ram_filename)

  cpu.io.inst_sram_rdata := inst_ram.io.ram_rdata
  inst_ram.io.ram_en := cpu.io.inst_sram_en
  inst_ram.io.ram_wen := cpu.io.inst_sram_wen
  inst_ram.io.ram_wdata := cpu.io.inst_sram_wdata
  inst_ram.io.ram_addr := cpu.io.inst_sram_addr

  cpu.io.data_sram_rdata := data_ram.io.ram_rdata
  data_ram.io.ram_en := cpu.io.data_sram_en
  data_ram.io.ram_wen := cpu.io.data_sram_wen
  data_ram.io.ram_wdata := cpu.io.data_sram_wdata
  data_ram.io.ram_addr := cpu.io.data_sram_addr

  io.debug_wb_pc := cpu.io.debug_wb_pc
  io.debug_wb_rf_wen := cpu.io.debug_wb_rf_wen
  io.debug_wb_rf_wnum := cpu.io.debug_wb_rf_wnum
  io.debug_wb_rf_wdata := cpu.io.debug_wb_rf_wdata


}
