package com.github.hectormips.cache.dcache

import chisel3.{Bool, Bundle, UInt, Vec, Wire}
import com.github.hectormips.cache.setting.CacheConfig
import chisel3._
import chisel3.util._

class BankData(val config: CacheConfig) extends Bundle {
//  val addr = Wire(Vec(2,UInt(config.indexWidth.W)))
  val read = Wire(Vec(2,Vec(config.wayNum, Vec(config.bankNum,UInt(32.W)))))
  //  val write = Vec(config.bankNum, UInt(32.W))
  val wEn = Wire(Vec(2,Vec(config.wayNum, Vec(config.bankNum,Bool()))))
}

class tagv(tagWidth:Int) extends Bundle{
  val tag = UInt(tagWidth.W)
  val valid = Bool()
}
class TAGVData(val config: CacheConfig) extends Bundle {
//  val addr = Wire(Vec(2,UInt(config.indexWidth.W)))
  val read = Wire(Vec(2,Vec(config.wayNum, new tagv(config.tagWidth)))) //一次读n个)
  val write = Wire(Vec(2,UInt((config.tagWidth+1).W))) //一次写1个
  val wEn = Wire(Vec(2,Vec(config.wayNum, Bool())))
}
class DirtyData(val config:CacheConfig) extends Bundle{
  val read = Wire(Vec(2,Vec(config.wayNum, Bool())))
}

//@departed 废弃
class ByteSelection extends Module{
  val io = IO(new Bundle{
    val data = Input(UInt(32.W))
    val size = Input(UInt(2.W))
    val offset  = Input(UInt(2.W))
    val select_data = Output(UInt(32.W))
  })
  io.select_data := 0.U
  when(io.size===0.U){
    when(io.offset===0.U){
      io.select_data := Cat(0.U(24.W),io.data(7,0))
    }.elsewhen(io.offset===1.U) {
      io.select_data := Cat(0.U(24.W),io.data(15,8))
    }.elsewhen(io.offset===2.U) {
      io.select_data := Cat(0.U(24.W),io.data(23,16))
    }.elsewhen(io.offset===3.U) {
      io.select_data := Cat(0.U(24.W),io.data(31,24))
    }
  }.elsewhen(io.size===1.U){
    when(io.offset===0.U){
      io.select_data := Cat(0.U(16.W),io.data(15,0))
    }.elsewhen(io.offset===1.U) {
      io.select_data := Cat(0.U(16.W),io.data(23,8))
    }.elsewhen(io.offset===2.U) {
      io.select_data := Cat(0.U(16.W),io.data(31,16))
    }.otherwise{
      io.select_data := Cat(0.U(16.W),io.data(31,16))
    }
  }.elsewhen(io.size===2.U){
    io.select_data := io.data
  }.otherwise{
    io.select_data := io.data
  }
}