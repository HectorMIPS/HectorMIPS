package com.github.hectormips.cache.dcache

import com.github.hectormips.amba.{AXIAddr, AXIReadData}
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.cache.lru.LruMem
import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

class BankData(val config: CacheConfig) extends Bundle {
  val addr = UInt(config.indexWidth.W)
  val read = Vec(config.wayNum, Vec(config.bankNum, UInt(32.W)))
  val write = Vec(config.bankNum, UInt(32.W))
  val wEn = Vec(config.wayNum, Vec(config.bankNum,Bool()))
}
class TAGVData(val config: CacheConfig) extends Bundle {
  val addr = UInt(config.indexWidth.W)
  val read = Vec(config.wayNum, UInt((config.tagWidth+1).W)) //一次读n个
  val write = UInt((config.tagWidth+1).W) //一次写1个
  val wEn = Vec(config.wayNum, Bool())
  def tag(way: Int):UInt={
    read(way)(19,0)
  }
  def valid(way:Int):UInt={
    read(way)(20)
  }
}
class DirtyData(val config:CacheConfig) extends Bundle{
  val addr = UInt(config.indexWidth.W)
  val read = Vec(config.wayNum, Bool())
  val write = Bool()
  val wEn = Vec(config.wayNum, Bool())
}
class DCache(val config:CacheConfig)
  extends Module {
  var io = IO(new Bundle {
    val valid = Input(Bool())
    val addr = Input(UInt(32.W))
    val addr_ok = Output(Bool())

    val wr   = Input(Bool())
    val size = Input(UInt(2.W))
    val data_ok = Output(Bool())

    val rdata = Output(UInt(32.W))

    val wdata = Input(UInt(32.W))
    val wstrb = Input(UInt(4.W))

    val axi     = new Bundle{
      val readAddr  =  Decoupled(new AXIAddr(32,4))
      val readData  = Flipped(Decoupled(new AXIReadData(32,4)))
    }
  })
  val sIDLE::sLOOKUP::sREPLACE::sREFILL::sWaiting::Nil =Enum(5)
  val state = Reg(UInt(3.W))
  /**
   * cache的数据
   */
  val dataMem = List.fill(config.wayNum) {
    List.fill(config.bankNum) { // 4字节长就有4个
      SyncReadMem(config.indexWidth,UInt(32.W))
    }
  }
  val tagvMem = List.fill(config.wayNum) {
    SyncReadMem(config.lineNum, UInt((config.tagWidth+1).W))
  }

  val lruMem = Module(new LruMem(config.wayNumWidth,config.indexWidth))// lru

  val cache_hit_onehot = Wire(Vec(config.wayNum, Bool())) // 命中的路
  val cache_hit_way = Wire(UInt(config.wayNumWidth.W))

  val addrReg = Reg(UInt(32.W)) //地址寄存器
  val bData = Wire(new BankData(config))
  //  val bDataWriteReg = RegInit(VecInit(Seq.fill(config.bankNum)(0.U(32.W))))
  val tagvData = Wire(new TAGVData(config))
  val dataMemEn = RegInit(false.B)
  val bDataWtBank = Reg(UInt((config.offsetWidth-2).W))
  val AXI_readyReg = Reg(Bool())
  /**
   * 其他配置
   */
  val is_hitWay = Wire(Bool())
  is_hitWay := cache_hit_onehot.asUInt().orR() // 判断是否命中cache


  val addrokReg = RegInit(false.B)
  io.addr_ok := addrokReg
  state := sIDLE

  val index  = Wire(UInt(config.indexWidth.W))
  val bankIndex = Wire(UInt((config.offsetWidth-2).W))
  val tag  = Wire(UInt(config.tagWidth.W))
  val waySelReg = Reg(UInt(config.wayNumWidth.W))
//  val readByteFrom = Wire(UInt(2.W))
//  val readByteTo = Wire(UInt(2.W))
//  val readMSB  = Wire(UInt(2.W))//最高有效字节的位置
//  when(io.size===0.U){
//    readMSB := 0.U
//  }.elsewhen(io.size===1.U){
//    readMSB := 1.U
//  }.otherwise{
//    readMSB := 3.U
//  }

//  Cat(readByteFrom,readByteTo) := get_read_mask(io.addr(1,0),io.size)

  index := config.getIndex(addrReg)

  bankIndex := config.getBankIndex(addrReg) //offset去掉尾部2位

  tag := config.getTag(addrReg)

  //  val dataBankWtMask = WireInit(VecInit.tabulate(4) { _ => true.B })
  //  val writeData =WireInit(VecInit.tabulate(4) { _ => 0.U(8.W) })
  /**
   * dataMem
   */
  dataMem.indices.foreach(way=>{
    dataMem(way).indices.foreach(bank=>{
      val m = dataMem(way)(bank)
      when(bData.wEn(way)(bank) && state === sREFILL){
        //        writeData(0) := bData.write(bank)(31,24)
        //        writeData(1) := bData.write(bank)(23,16)
        //        writeData(2) := bData.write(bank)(15,8)
        //        writeData(3) := bData.write(bank)(7,0)
        m.write(bData.addr,bData.write(bank))
      }
      bData.write(bank) := io.axi.readData.bits.data
      bData.read(way)(bank) := m.read(bData.addr)
    })
  })
  for(way <- 0 until config.wayNum){
    val m = tagvMem(way)
    when(tagvData.wEn(way)){//写使能
      m.write(tagvData.addr,tagvData.write)
    }
    tagvData.read(way) := m.read(tagvData.addr)
  }
  for(way<- 0 until config.wayNum){
    for(bank <- 0 until config.bankNum) {
      bData.wEn(way)(bank) := state===sREFILL && waySelReg === way.U && bDataWtBank ===bank.U && !clock.asBool()
    }
  }
  for(way<- 0 until config.wayNum){
    tagvData.wEn(way) := reset.asBool() || (state === sREFILL && waySelReg===way.U)
  }

  tagvData.write := Cat(true.B,tag)
  bData.addr := index
  tagvData.addr := index
  cache_hit_way := OHToUInt(cache_hit_onehot)
  // 判断是否命中cache
  cache_hit_onehot.indices.foreach(i=>{
    cache_hit_onehot(i) :=  tagvData.tag(i) === tag & tagvData.valid(i)
  })

  /**
   * IO初始化
   */
  io.data_ok := true.B
  io.rdata := 0.U
  addrokReg := false.B
  /**
   * LRU 配置
   */
  lruMem.io.setAddr := index
  lruMem.io.visit := 0.U
  lruMem.io.visitValid := is_hitWay

  /**
   * reset
   */
  val tmp = Wire(UInt(config.indexWidth.W))
  withClockAndReset(clock,false.B){
    val resetValidCounter = RegInit(0.U(config.indexWidth.W))
    resetValidCounter := resetValidCounter + 1.U
    tmp:= resetValidCounter
  }

  when(reset.asBool()){
    tagvData.write := 0.U((config.tagWidth+1).W)
    tagvData.addr := tmp
  }

  printf("[%d] %d,%d tagv=%x\n",tmp,tagvData.wEn(0),tagvData.wEn(1),tagvData.write)
  /**
   * Cache状态机
   */
  switch(state){
    is(sIDLE){
      when(io.valid){
        state := sLOOKUP
        addrokReg := true.B
        addrReg := io.addr
      }
    }
    is(sLOOKUP){
      when(is_hitWay){
        when(!io.wr) {
          io.rdata := get_read_data(bData.read(cache_hit_way)(bankIndex), addrReg(1, 0), io.size)
          io.data_ok := true.B
        }
        lruMem.io.visit := cache_hit_way // lru记录命中
        when(io.valid) {
          // 直接进入下一轮
          addrokReg := true.B
          addrReg := io.addr
        }.otherwise {
          state := sIDLE
        }
      }.otherwise {
        //没命中,尝试请求
        state := sREPLACE
      }
    }
    is(sREPLACE){
      waySelReg := lruMem.io.waySel
      bDataWtBank := bankIndex
      when(io.axi.readAddr.ready) {
        state := sREFILL
      }.otherwise{
        state := sREPLACE
      }
    }
    is(sREFILL){
      // 取数据，重写TAGV
      state := sREFILL
      when(io.axi.readData.valid){
        bDataWtBank := bDataWtBank+1.U
      }
      when(io.axi.readData.bits.last){
        state := sWaiting
        io.rdata := get_read_data(bData.read(waySelReg)(bankIndex),addrReg(1,0),io.size)
        io.data_ok := true.B
      }
    }
    is(sWaiting){
      state := sIDLE
      io.rdata := get_read_data(bData.read(waySelReg)(bankIndex),addrReg(1,0),io.size)
      io.data_ok := true.B
    }
  }

  //  when(io.axi.readAddr.fire()){
  //    io.axi.readAddr.valid := false.B
  //  }

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
  io.axi.readAddr.bits.burst := 2.U //突发模式2
  io.axi.readAddr.valid:= (!io.axi.readAddr.ready &&state ===sREPLACE)
  //  val AXIReadDataReady = RegInit(false.B)
  //  AXIReadDataReady := () &&
  io.axi.readData.ready := state === sREFILL  //ready最多持续一拍


  def get_read_data(data:UInt,last_two_bit:UInt,size:UInt):UInt={
    val output = Wire(UInt(32.W))
      output:=data
      when(size===0.U){
          when(last_two_bit===0.U){
            output := Cat(0.U(24.W),data(0,0))
          }.elsewhen(last_two_bit===1.U) {
            output := Cat(0.U(24.W),data(1,1))
          }.elsewhen(last_two_bit===2.U) {
            output := Cat(0.U(24.W),data(2,2))
          }.elsewhen(last_two_bit===3.U) {
            output := Cat(0.U(24.W),data(3,3))
          }
      }.elsewhen(size===1.U){
        when(last_two_bit===0.U){
          output := Cat(0.U(16.W),data(1,0))
        }.elsewhen(last_two_bit===1.U) {
          output := Cat(0.U(16.W),data(2,1))
        }.elsewhen(last_two_bit===2.U) {
          output := Cat(0.U(16.W),data(3,2))
        }.otherwise{
          output := Cat(0.U(16.W),data(3,2))
        }
      }.elsewhen(size===2.U){
        output := data
      }
    output
  }
}

object DCache extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new DCache(new CacheConfig()))))
}
