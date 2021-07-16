package com.github.hectormips.cache.icache

import com.github.hectormips.amba.{AXIAddr,AXIReadData}
import com.github.hectormips.cache.setting.CacheConfig
import chisel3._
import chisel3.util._

import chisel3.experimental.{ChiselEnum}


/**
 * 存放cache data
 * ICache 没有写，不分bank
 */
class BankData(val config: CacheConfig) extends Bundle {
  val addr = UInt(config.indexWidth.W)
  val read = Vec(config.wayNum, Vec(config.bankNum, UInt(32.W)))
  val write = Vec(config.bankNum, UInt(32.W))
  val wEn = Vec(config.wayNum, Bool())
}
class TAGVData(val config: CacheConfig) extends Bundle {
  val addr = UInt(config.indexWidth.W)
  val read = Vec(config.wayNum, UInt((config.tagWidth+1).W))
  val write = Vec(config.bankNum, UInt((config.tagWidth+1).W))
  val wEn = Vec(config.wayNum, Bool())
  def tag(way: Int):UInt={
    read(way)(20,0)
  }
  def valid(way:Int):UInt={
    read(way)(21)
  }
}
class DataBank(val AddrLength: Int) extends BlackBox{
  val io = IO(new Bundle {
    val clka = Input(Clock())
    val ena = Input(Bool())
    val wea = Input(UInt(4.W))
    val addra = Input(UInt(AddrLength.W))
    val dina = Input(UInt(32.W))
    val dout = Output(UInt(32.W))
  })
}
class TAGVBank(val AddrLength: Int,val TAGVLength:Int) extends BlackBox{
  val io = IO(new Bundle {
    val clka = Input(Clock())
    val ena = Input(Bool())
    val wea = Input(Bool())
    val addra = Input(UInt(AddrLength.W))
    val dina = Input(UInt(TAGVLength.W))
    val dout = Output(UInt(TAGVLength.W))
  })
}
/**
 *
 * 第一拍：取bram中数据
 * 第二拍：比
 */
object CacheFSMState extends ChiselEnum {
  val IDLE,LOOKUP,REPLACE,REFILL = Value
}
class ICache(val config:CacheConfig)
 extends Module{
  var io = IO(new Bundle{
    val valid   = Input(Bool())
    val addr    = Input(UInt(32.W)) //等到ok以后才能撤去数据
    val addr_ok = Output(Bool()) //等到ok以后才能撤去数据

    val inst1    = Output(UInt(32.W))
    val inst1Valid =Output(Bool())
    val inst1OK  = Output(Bool())
    val inst2    = Output(UInt(32.W))
    val inst2Valid =Output(Bool())
    val inst2OK  = Output(Bool())
//    val inst3    = Output(UInt(32.W))
//    val inst3Valid =Output(Bool())
//    val inst4    = Output(UInt(32.W))
//    val inst4Valid =Output(Bool())

    val axi     = new Bundle{
      val readAddr  =  Decoupled(new AXIAddr(32,4))
      val readData  = Flipped(Decoupled(new AXIReadData(32,4)))
    }
  })
  // cache 数据区
  val dataMem = List.fill(config.wayNum) {
    List.fill(config.bankNum) { // 4字节长就有4个
      Module(new DataBank(config.indexWidth))
    }
  }
  val tagvMem = List.fill(config.wayNum) {
      Module(new TAGVBank(config.indexWidth,config.tagWidth+1.U))
  }
  val state = UInt(CacheFSMState.getWidth.W)
  val addrokReg = RegInit(false.B)
  io.addr_ok := addrokReg
  state := CacheFSMState.IDLE

  val index   = Wire(UInt(config.indexWidth.W))
  index := config.getIndex(io.addr)
  val bankIndex = Wire(UInt(config.offsetWidth.W))
  bankIndex := config.getBankIndex(io.addr)
  val tag  = Wire(UInt(config.tagWidth.W))
  tag := config.getTag(io.addr)
  val addrReg = Reg(UInt(32.W)) //地址寄存器
  val bankdata = Wire(new BankData(config))
  val tagvdata = Wire(new TAGVData(config))
  val dataMemEn = RegInit(Bool())

  dataMem.indices.foreach(w=>{
    dataMem.indices.foreach(b=>{
      val m = dataMem(w)(b)
      m.io.clka := clock
      m.io.ena := true.B
      m.io.wea := bankdata.wEn(w)
      m.io.addra := bankdata.addr //index
      m.io.dina := bankdata.write(b)
      bankdata.read(w)(b) := m.io.dout
    })
  })
  tagvMem.indices.foreach(w=>{
    val m = tagvMem(w)
    m.io.clka := clock
    m.io.ena := true.B
    m.io.wea := tagvdata.wEn(w)
    m.io.addra := tagvdata.addr //index
    m.io.dina := tagvdata.write
    tagvdata.read(w) := m.io.dout
  })
  bankdata.addr := Mux(state.equals(CacheFSMState.REFILL).asBool(),_,index)
  val cache_hit_onehot = Wire(Vec(config.wayNum, Bool())) // 命中的路
  val cache_hit_way = Wire(UInt(config.wayNumWidth.W))
  cache_hit_onehot.indices.foreach(i=>{
    cache_hit_onehot(i) := tagvdata.tag(i) === tag & tagvdata.valid(i)
  })
  cache_hit_way := OHToUInt(cache_hit_onehot)
  io.inst1Valid := true.B
  io.inst2Valid := bankIndex === (config.DataWidthByByte - 1).U //==bank-1
  io.inst1OK    := false.B
  io.inst2OK    := false.B
  addrokReg := false.B

  switch(state){
    is(CacheFSMState.IDLE){
      when(io.valid){
        state := CacheFSMState.LOOKUP
        addrokReg := true.B
        addrReg := io.addr
      }
    }
    is(CacheFSMState.LOOKUP){
      when(cache_hit_way===0.U){
        //没命中,尝试请求
        state := CacheFSMState.REPLACE
      }.otherwise {
        io.inst1 := bankdata.read(cache_hit_way)(bankIndex)
        io.inst1OK := true.B
        io.inst2 := bankdata.read(cache_hit_way)(bankIndex+1.U)
        io.inst2OK := true.B
        when(io.valid) {
          addrokReg := true.B
        }.otherwise {
          state := CacheFSMState.IDLE
        }
      }
    }
    is(CacheFSMState.REPLACE){
      io.axi.readAddr.valid := true.B
      //采用cache的突发
//      io.axi.readAddr.re
    }

  }
  when(io.axi.readAddr.fire()){
    io.axi.readAddr.valid := false.B
  }

  /**
   * axi访问设置
   */
  io.axi.readAddr.bits.id := 0.U
  io.axi.readAddr.bits.len := (config.bankNum - 1).U
  io.axi.readAddr.bits.size := 2.U // 4B
  io.axi.readAddr.bits.addr := addrReg
  io.axi.readAddr.bits.cache := 0.U
  io.axi.readAddr.bits.lock := 0.U
  io.axi.readAddr.bits.prot := 0.U
}
