package com.github.hectormips.cache.dcache

import com.github.hectormips.amba._
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.cache.lru.LruMem
import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

class BankData(val config: CacheConfig) extends Bundle {
  val addr = UInt(config.indexWidth.W)
  val read = Vec(config.wayNum, Vec(config.bankNum, UInt(32.W)))
  //  val write = Vec(config.bankNum, UInt(32.W))
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
  def tag(way: UInt):UInt={
    read(way)(19,0)
  }
  def valid(way:Int):UInt={
    read(way)(20)
  }
  def valid(way:UInt):UInt={
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
      val writeAddr  =  Decoupled(new AXIAddr(32,4))
      val writeData  = Decoupled(new AXIWriteData(32,4))
      val writeResp =  Flipped(Decoupled( new AXIWriteResponse(4)))
    }
  })
  val sIDLE::sLOOKUP::sREPLACE::sREFILL::sWaiting::sInit::Nil =Enum(6)
  val state = RegInit(5.U(3.W))
  val debug_counter = RegInit(0.U(10.W))

  /**
   * cache的数据
   */
  val dirtyMem = Mem(config.lineNum,Vec(config.wayNum, Bool())) //注意前面的是way

  val dataMem = List.fill(config.wayNum) {
    List.fill(config.bankNum) { // 4字节长就有4个
      SyncReadMem(config.lineNum,Vec(4, UInt(8.W)))
    }
  }
  val tagvMem = List.fill(config.wayNum) {
    SyncReadMem(config.lineNum, UInt((config.tagWidth+1).W))
  }

  val lruMem = Module(new LruMem(config.wayNumWidth,config.indexWidth))// lru
  val victim = Module(new Victim(config)) // 写代理
  io.axi.writeAddr <> victim.io.axi.writeAddr
  io.axi.writeData <> victim.io.axi.writeData
  io.axi.writeResp <> victim.io.axi.writeResp

  val cache_hit_onehot = Wire(Vec(config.wayNum, Bool())) // 命中的路
  val cache_hit_way = Wire(UInt(config.wayNumWidth.W))

  val addrReg = RegInit(0.U(32.W)) //地址寄存器
  val bData = Wire(new BankData(config))
  //  val bDataWriteReg = RegInit(VecInit(Seq.fill(config.bankNum)(0.U(32.W))))
  val tagvData = Wire(new TAGVData(config))
  val dataMemEn = RegInit(false.B)
  val bDataWtBank = RegInit(0.U((config.offsetWidth-2).W))
  val AXI_readyReg = RegInit(false.B)

  val is_hitWay = Wire(Bool())
  val addrokReg = RegInit(false.B)
  val index  = Wire(UInt(config.indexWidth.W))
  val bankIndex = Wire(UInt((config.offsetWidth-2).W))
  val tag  = Wire(UInt(config.tagWidth.W))
  val waySelReg = RegInit(0.U(config.wayNumWidth.W))
  val fetch_ready_go = Wire(Bool())
  val eviction = Wire(Bool()) //出现驱逐
  val sizeReg = RegInit(2.U(2.W))
  val wrReg = RegInit(false.B)
  is_hitWay := cache_hit_onehot.asUInt().orR() // 判断是否命中cache
  io.addr_ok := addrokReg
  state := sIDLE

  index := config.getIndex(addrReg)

  bankIndex := config.getBankIndex(addrReg) //offset去掉尾部2位

  tag := config.getTag(addrReg)

  /**
   * dataMem
   */
  //  val writeData
  val _victim_odata_vec = Wire(Vec(config.bankNum,Vec(4,UInt(8.W))))
  for(bank <- 0 until config.bankNum){
    _victim_odata_vec(bank)(0) := victim.io.odata(bank)(7,0)
    _victim_odata_vec(bank)(1) := victim.io.odata(bank)(15,8)
    _victim_odata_vec(bank)(2) := victim.io.odata(bank)(23,16)
    _victim_odata_vec(bank)(3) := victim.io.odata(bank)(31,24)
  }
  val _bdata_vec = Wire(Vec(config.bankNum,Vec(4,UInt(8.W))))
  for(bank <- 0 until config.bankNum){
    _bdata_vec(bank)(0) := io.axi.readData.bits.data(7,0)
    _bdata_vec(bank)(1) := io.axi.readData.bits.data(15,8)
    _bdata_vec(bank)(2) := io.axi.readData.bits.data(23,16)
    _bdata_vec(bank)(3) := io.axi.readData.bits.data(31,24)
  }
  val _wdata_vec = Wire(Vec(4,UInt(8.W))) // 外部写数据
  _wdata_vec(0) := io.wdata(7,0)
  _wdata_vec(1) := io.wdata(15,8)
  _wdata_vec(2) := io.wdata(23,16)
  _wdata_vec(3) := io.wdata(31,24)
  val _wstrb_vec = Wire(Vec(4,Bool()))
  _wstrb_vec(0) := io.wstrb(0)
  _wstrb_vec(1) := io.wstrb(1)
  _wstrb_vec(2) := io.wstrb(2)
  _wstrb_vec(3) := io.wstrb(3)
  val _read_data = Wire(Vec(config.wayNum,Vec(config.bankNum,Vec(4,UInt(8.W)))))
  _read_data.indices.foreach(way => {
    _read_data(way).indices.foreach(bank =>{
      bData.read(way)(bank) := Cat(_read_data(way)(bank)(3),_read_data(way)(bank)(2),_read_data(way)(bank)(1),
        _read_data(way)(bank)(0))
    })
  })
  when(victim.io.find) {
    dataMem.indices.foreach(way => {
      dataMem(way).indices.foreach(bank => {
        val m = dataMem(way)(bank)
        when(bData.wEn(way)(bank)) {
          m.write(bData.addr, _victim_odata_vec(bank))//重填
          dirtyMem(bData.addr)(way) := false.B
        }
        _read_data(way)(bank)  := m(config.getIndex(io.addr))
      })
    })
  }.otherwise {
    dataMem.indices.foreach(way => {
//      printf("[%d] dirtyMem(%x)(%x)=%d \n",debug_counter,bData.addr,way.U,dirtyMem(bData.addr)(way))
      dataMem(way).indices.foreach(bank => {
        val m = dataMem(way)(bank)
        when(bData.wEn(way)(bank)) {
          when(state === sREFILL) {
            when(wrReg){
              dirtyMem(bData.addr)(way) := true.B //Write miss 的 目标
            }.otherwise {
              dirtyMem(bData.addr)(way) := false.B //进入到REFILL的必然是新数据
            }
            when(wrReg && bank.U === bankIndex) {
              m.write(bData.addr, _wdata_vec, _wstrb_vec)
            }.otherwise {
              m.write(bData.addr, _bdata_vec(bank))
            }
          }.otherwise{
            when(is_hitWay){
              dirtyMem(bData.addr)(way) := true.B //旧数据
            }.otherwise{
              dirtyMem(bData.addr)(way) := false.B //新数据
            }
            m.write(bData.addr, _wdata_vec,_wstrb_vec)
          }
        }
        //        bData.write(bank) := io.axi.readData.bits.data
        _read_data(way)(bank)  := m(config.getIndex(io.addr))
      })
    })
  }

  // 初始化
  val resetCounter = Counter(1<<config.indexWidth)
  resetCounter.inc()
  when(state===sInit){
    tagvData.write := 0.U
    tagvData.addr := resetCounter.value
  }

  for(way <- 0 until config.wayNum){
    val m = tagvMem(way)
    when(tagvData.wEn(way)){//写使能
      m.write(tagvData.addr,tagvData.write)
    }
    tagvData.read(way) := m(config.getIndex(io.addr))
  }
  for(way<- 0 until config.wayNum){
    for(bank <- 0 until config.bankNum) {
      bData.wEn(way)(bank) := (state===sREFILL && waySelReg === way.U && bDataWtBank ===bank.U && !clock.asBool() )||
        //          (state===sREPLACE && victim.io.find && waySelReg === way.U) ||// 如果victim buffer里找到了
        (is_hitWay && state === sLOOKUP && waySelReg === way.U && bDataWtBank ===bank.U && wrReg === true.B)||
        (is_hitWay && state === sREFILL && waySelReg === way.U && bDataWtBank ===bank.U && wrReg === true.B)||
        (state===sLOOKUP && !is_hitWay  && victim.io.find && waySelReg === way.U )
    }
  }
  for(way<- 0 until config.wayNum){
    tagvData.wEn(way) := state === sInit ||
      (state === sREFILL && waySelReg===way.U) ||
      (victim.io.find && waySelReg===way.U)
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
  io.data_ok := false.B
  io.rdata := 0.U
  addrokReg := false.B
  /**
   * LRU 配置
   */
  lruMem.io.setAddr := index
  lruMem.io.visit := 0.U
  lruMem.io.visitValid := is_hitWay


//  printf("[%d] %d,%d tagv=%x\n",tmp,tagvData.wEn(0),tagvData.wEn(1),tagvData.write)
  /**
   * Cache状态机
   */


  io.axi.readAddr.valid :=  false.B
  victim.io.addr := Cat(io.addr(31,config.offsetWidth))
  switch(state){
    is(sIDLE){
      when(io.valid){
        state := sLOOKUP
        addrokReg := true.B
        addrReg := io.addr
        sizeReg := io.size
        wrReg := io.wr
      }
    }
    is(sLOOKUP){
      when(is_hitWay){
        lruMem.io.visit := cache_hit_way // lru记录命中
        when(io.valid) {
          // 直接进入下一轮
          state := sLOOKUP
          addrokReg := true.B
          addrReg := io.addr
          sizeReg := io.size
          wrReg := io.wr
        }.otherwise {
          state := sIDLE
        }
        io.data_ok := true.B
        when(!wrReg) {
          // 读
          io.rdata := get_read_data(bData.read(cache_hit_way)(bankIndex), addrReg(1, 0),sizeReg)
        }
      }.elsewhen(victim.io.find){
        // 在 victim buffer里找到了
        when(fetch_ready_go){
          state := sIDLE
          victim.io.odata
        }.otherwise{
          state := sLOOKUP
        }
        io.data_ok := true.B
//        printf("%x\n",victim.io.odata(bankIndex))
        when(!wrReg) {
          // 读
          io.rdata := get_read_data(victim.io.odata(bankIndex), addrReg(1, 0),sizeReg)
        }
      }.otherwise {
        //没命中,尝试请求
        when(fetch_ready_go){
          io.axi.readAddr.valid := true.B
          waySelReg := lruMem.io.waySel
          state := sREPLACE
        }.otherwise{
          state := sLOOKUP
        }
      }
    }
    is(sREPLACE){
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
        when(io.axi.readData.bits.last){
          state := sWaiting
          when(!wrReg){
            io.rdata := get_read_data(bData.read(waySelReg)(bankIndex),addrReg(1,0),sizeReg)
            io.data_ok := true.B
          }

        }
      }

    }
    is(sWaiting){
      state := sIDLE
      io.rdata := get_read_data(bData.read(waySelReg)(bankIndex),addrReg(1,0),sizeReg)
      io.data_ok := true.B
//            lruMem.io.visit := waySelReg
//      when(io.valid && !io.wr){
//        io.data_ok := false.B
//        state := sLOOKUP
//        addrokReg := true.B
//        addrReg := io.addr
//        sizeReg := io.size
//      }
    }
  }

  /**
   * 驱逐
   */
  debug_counter := debug_counter + 1.U
  fetch_ready_go := (eviction && !victim.io.full) || !eviction
  //TODO:改为state==sLOOKUP
  when((tagvData.valid(waySelReg).asBool() && (state ===sREFILL) && !is_hitWay)){
    // 没找到，并且要填充一个已存在的位置
    //    printf("[%d]should eviction\n",debug_counter)
    victim.io.addr := Cat(tagvData.read(waySelReg)(19,0) ,index(7,0))
    eviction := true.B
    victim.io.op := true.B
    victim.io.dirty := dirtyMem(index)(waySelReg)
    for(i <- 0 to 3){
      victim.io.idata(i) := bData.read(waySelReg)(i)
    }
  }.otherwise{
    eviction := false.B
    victim.io.op := false.B
    victim.io.dirty := false.B
    for(i <- 0 to 3){
      victim.io.idata(i) := 0.U
    }
  }

  /**
   * axi访问设置
   */
  io.axi.readAddr.bits.id := 1.U
//  io.axi.readAddr.bits.len := 6.U
  io.axi.readAddr.bits.len := (config.bankNum - 1).U
  io.axi.readAddr.bits.size := 2.U // 4B
  io.axi.readAddr.bits.addr := addrReg
  io.axi.readAddr.bits.cache := 0.U
  io.axi.readAddr.bits.lock := 0.U
  io.axi.readAddr.bits.prot := 0.U
  io.axi.readAddr.bits.burst := 2.U //突发模式2

  io.axi.readData.ready := true.B  //ready最多持续一拍


  def get_read_data(data:UInt,last_two_bit:UInt,size:UInt):UInt={
    val output = Wire(UInt(32.W))
    output:=data
    when(size===0.U){
      when(last_two_bit===0.U){
        output := Cat(0.U(24.W),data(7,0))
      }.elsewhen(last_two_bit===1.U) {
        output := Cat(0.U(24.W),data(15,8))
      }.elsewhen(last_two_bit===2.U) {
        output := Cat(0.U(24.W),data(23,16))
      }.elsewhen(last_two_bit===3.U) {
        output := Cat(0.U(24.W),data(31,24))
      }
    }.elsewhen(size===1.U){
      when(last_two_bit===0.U){
        output := Cat(0.U(16.W),data(15,0))
      }.elsewhen(last_two_bit===1.U) {
        output := Cat(0.U(16.W),data(23,8))
      }.elsewhen(last_two_bit===2.U) {
        output := Cat(0.U(16.W),data(31,16))
      }.otherwise{
        output := Cat(0.U(16.W),data(31,16))
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