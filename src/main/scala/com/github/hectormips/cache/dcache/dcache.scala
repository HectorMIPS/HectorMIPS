//package com.github.hectormips.cache.dcache
//
//import com.github.hectormips.amba.{AXIMaster}
//import com.github.hectormips.cache.setting.CacheConfig
//import com.github.hectormips.cache.lru.LruMem
//import chisel3._
//import chisel3.util._
//import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
//
//class BankData(val config: CacheConfig) extends Bundle {
//  val addr = UInt(config.indexWidth.W)
//  val read = Vec(config.wayNum, Vec(config.bankNum, UInt(32.W)))
//  val write = Vec(config.bankNum, UInt(32.W))
//  val wEn = Vec(config.wayNum, Vec(config.bankNum,Bool()))
//}
//class TAGVData(val config: CacheConfig) extends Bundle {
//  val addr = UInt(config.indexWidth.W)
//  val read = Vec(config.wayNum, UInt((config.tagWidth+1).W)) //一次读n个
//  val write = UInt((config.tagWidth+1).W) //一次写1个
//  val wEn = Vec(config.wayNum, Bool())
//  def tag(way: Int):UInt={
//    read(way)(19,0)
//  }
//  def valid(way:Int):UInt={
//    read(way)(20)
//  }
//}
//class DirtyData(val config:CacheConfig) extends Bundle{
//  val addr = UInt(config.indexWidth.W)
//  val read = Vec(config.wayNum, Bool())
//  val write = Bool()
//  val wEn = Vec(config.wayNum, Bool())
//}
//class dcache(val config:CacheConfig)
//  extends Module {
//  var io = IO(new Bundle {
//    val valid = Input(Bool())
//    val addr = Input(UInt(32.W)) //等到ok以后才能撤去数据
//    val addr_ok = Output(Bool()) //等到ok以后才能撤去数据
//    val wr   = Input(Bool())
//
//    val inst1 = Output(UInt(32.W))
//    val inst1Valid = Output(Bool())
//
//    val axi = new AXIMaster()
//  })
//
//}