package com.github.hectormips.cache.icache

import com.github.hectormips.amba.{AXIAddr, AXIReadData}
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.cache.lru.LruMem
import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chisel3.util.experimental.{forceName}

class BankData(val config: CacheConfig) extends Bundle {
  val addr = Wire(UInt(config.indexWidth.W))
  val read = Wire(Vec(config.wayNum, Vec(config.bankNum, UInt(32.W))))
  val write = Wire(Vec(config.bankNum, UInt(32.W)))
  val wEn = Wire(Vec(config.wayNum, Vec(config.bankNum,Bool())))
}
class TAGVData(val config: CacheConfig) extends Bundle {
  val addr = Wire(UInt(config.indexWidth.W))
  val read = Wire(Vec(config.wayNum, UInt((config.tagWidth).W)))//一次读n个
  val write = Wire(UInt((config.tagWidth).W)) //一次写1个
  val wEn = Wire(Vec(config.wayNum, Bool()))
}


class ICache(val config:CacheConfig)
 extends Module{
  var io = IO(new Bundle{
    val valid   = Input(Bool())
    val addr    = Input(UInt(32.W)) //等到ok以后才能撤去数据
    val addr_ok = Output(Bool()) //等到ok以后才能撤去数据

    val inst   = Output(UInt(64.W))
    val instOK = Output(Bool())

    val instValid =Output(Bool())

    val axi     = new Bundle{
      val readAddr  =  Decoupled(new AXIAddr(32,4))
      val readData  = Flipped(Decoupled(new AXIReadData(32,4)))
    }

    val debug_total_count = Output(UInt(32.W))  // cache总查询次数
    val debug_hit_count   = Output(UInt(32.W))  // cache命中数
  })
  val sIDLE::sLOOKUP::sREPLACE::sREFILL::Nil =Enum(4)
  val state = RegInit(0.U(3.W))// 初始化阶段



  /**
   * cache的数据
   */
  val dataMem = List.fill(config.wayNum) {
    List.fill(config.bankNum) { // 4字节长就有4个
      SyncReadMem(config.lineNum,UInt(32.W))
    }
  }
  val tagvMem = List.fill(config.wayNum) {
    SyncReadMem(config.lineNum, UInt(config.tagWidth.W))
  }

  val validMem = RegInit(VecInit(Seq.fill(config.wayNum)(VecInit(Seq.fill(config.lineNum)(false.B)))))

  val lruMem = Module(new LruMem(config.wayNumWidth,config.indexWidth))// lru

  val cache_hit_onehot = Wire(Vec(config.wayNum, Bool())) // 命中的路
  val cache_hit_way = Wire(UInt(config.wayNumWidth.W))

  val addrReg = RegInit(0.U(32.W)) //地址寄存器
  val bData = new BankData(config)
  val tagvData = new TAGVData(config)


  val dataMemEn = RegInit(false.B)
  val bDataWtBank = RegInit(0.U((config.offsetWidth-2).W))
  val AXI_readyReg = RegInit(false.B)

  val is_hitWay = Wire(Bool())
  is_hitWay := cache_hit_onehot.asUInt().orR() // 判断是否命中cache


  val addrokReg = RegInit(false.B)
  io.addr_ok := state === sIDLE || (state === sLOOKUP && is_hitWay)
  state := sIDLE

  val index  = Wire(UInt(config.indexWidth.W))
  val bankIndex = Wire(UInt((config.offsetWidth-2).W))
  val tag  = Wire(UInt(config.tagWidth.W))
  val waySelReg = Reg(UInt(config.wayNumWidth.W))
  index := config.getIndex(addrReg)

  bankIndex := config.getBankIndex(addrReg)

  tag := config.getTag(addrReg)

//  val dataBankWtMask = WireInit(VecInit.tabulate(4) { _ => true.B })
//  val writeData =WireInit(VecInit.tabulate(4) { _ => 0.U(8.W) })
  /**
   * dataMem
   */
  for(way <- 0 until config.wayNum){
    for(bank <- 0 until config.bankNum) {
      bData.read(way)(bank) := dataMem(way)(bank).read(config.getIndex(io.addr))
      bData.write(bank) := io.axi.readData.bits.data
    }
  }
  for(way <- 0 until config.wayNum){
    tagvData.read(way) := tagvMem(way).read(config.getIndex(io.addr))
  }
  for(way <- 0 until config.wayNum ) {
    for (bank <- 0 until config.bankNum) {
      when(bData.wEn(way)(bank) && state === sREFILL) {
        dataMem(way)(bank).write(bData.addr, bData.write(bank))
      }
    }
  }
  for(way <- 0 until config.wayNum){
    when(tagvData.wEn(way)){//写使能
      tagvMem(way).write(tagvData.addr,tag)
      validMem(way)(tagvData.addr) := true.B
    }
  }
  for(way<- 0 until config.wayNum){
    for(bank <- 0 until config.bankNum) {
      bData.wEn(way)(bank) := state===sREFILL && waySelReg === way.U && bDataWtBank ===bank.U && !clock.asBool()
    }
  }
  for(way<- 0 until config.wayNum){
    tagvData.wEn(way) :=  (state === sREFILL && waySelReg===way.U)
  }

  tagvData.write := tag
  bData.addr := index
  tagvData.addr := index
  cache_hit_way := OHToUInt(cache_hit_onehot)
  // 判断是否命中cache
  cache_hit_onehot.indices.foreach(i=>{
    cache_hit_onehot(i) :=  tagvData.read(i) === tag & validMem(i)(index)
  })

  /**
   * IO初始化
   */

  io.instValid := bankIndex =/= ((config.bankNum) - 1).U //==bank-1
  io.instOK    := false.B
//  printf("%d\n",io.instValid)
  io.inst := 0.U

  /**
   * LRU 配置
   */
  lruMem.io.setAddr := index
  lruMem.io.visit := 0.U
  lruMem.io.visitValid := is_hitWay

  /**
   * reset
   * 重写数据为0
   */
  val resetCounter = Counter(1<<config.indexWidth)
  resetCounter.inc()

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
//  val readAddrValidReg = RegInit(false.B)
  //  readAddrReg := ()

//  io.axi.readAddr.valid:= true.B
  io.axi.readAddr.valid := false.B
  //  val AXIReadDataReady = RegInit(false.B)
  //  AXIReadDataReady := () &&
  io.axi.readData.ready := true.B  //ready最多持续一拍

  /**
   * debug
   */
  val debug_total_count_r = RegInit(0.U(32.W))
  val debug_hit_count_r = RegInit(0.U(32.W))

  io.debug_total_count := debug_total_count_r
  io.debug_hit_count := debug_hit_count_r

  when(state===sLOOKUP){
    debug_total_count_r := debug_total_count_r + 1.U
    when(is_hitWay){
      debug_hit_count_r := debug_hit_count_r + 1.U
    }
  }

  /**
   * Cache状态机
   */
  switch(state){
    is(sIDLE){
      when(io.valid){
        state := sLOOKUP
//        io.addr_ok:= true.B
//        addrokReg := true.B
        addrReg := io.addr
      }
    }
    is(sLOOKUP){
      when(is_hitWay){
        when(bankIndex === (config.bankNum - 1).U){
          io.inst := Cat(0.U(32.W),bData.read(cache_hit_way)(bankIndex))
        }.otherwise{
          io.inst := Cat(bData.read(cache_hit_way)(bankIndex+1.U),bData.read(cache_hit_way)(bankIndex))
        }
//        io.inst1OK := true.B
        io.instOK := true.B
        lruMem.io.visit := cache_hit_way // lru记录命中
        when(io.valid) {
          // 直接进入下一轮
          addrReg := io.addr
          state := sLOOKUP
        }.otherwise {
          state := sIDLE
        }
      }.otherwise {
        //没命中,尝试请求
        io.inst := 0.U
        io.instOK := false.B
        state :=  sREPLACE
        io.axi.readAddr.valid := true.B
      }
    }
    is(sREPLACE){
      waySelReg := lruMem.io.waySel
      bDataWtBank := bankIndex
      when(io.axi.readAddr.ready) {
        state := sREFILL
      }.otherwise{
        state := sREPLACE
        io.axi.readAddr.valid := true.B
      }
    }
    is(sREFILL){
      // 取数据，重写TAGV
      state := sREFILL
      when(io.axi.readData.valid && io.axi.readData.bits.id === io.axi.readAddr.bits.id){
        bDataWtBank := bDataWtBank+1.U
        when(bDataWtBank === (bankIndex + 2.U)){
          // 关键字优先
          when(bankIndex === (config.bankNum - 1).U){
            io.inst := Cat(0.U(32.W),bData.read(cache_hit_way)(bankIndex))
          }.otherwise{
            io.inst := Cat(bData.read(cache_hit_way)(bankIndex+1.U),bData.read(cache_hit_way)(bankIndex))
          }
          io.instOK := true.B
        }
        when(io.axi.readData.bits.last){
          state := sIDLE
        }
      }
    }
//    is(sWaiting){
//      state := sIDLE
//      io.inst := Cat(bData.read(waySelReg)(bankIndex+1.U),bData.read(waySelReg)(bankIndex))
////      io.inst(31,0) := bData.read(waySelReg)(bankIndex)
////      io.inst(63,32) := bData.read(waySelReg)(bankIndex+1.U)
//      io.instOK := true.B
//    }
  }

//  when(io.axi.readAddr.fire()){
//    io.axi.readAddr.valid := false.B
//  }


}
object ICache extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new ICache(new CacheConfig()))))
}
