package com.github.hectormips.cache.dcache

import com.github.hectormips.amba._
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.cache.lru.LruMem
import com.github.hectormips.cache.utils.Wstrb
import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}



class QueueItem extends Bundle{
  val port = UInt(1.W)
  val addr = UInt(32.W)
  val wr   = Bool()
  val size = UInt(2.W)
  val wdata = UInt(32.W)
}


class DCache(val config:CacheConfig)
  extends Module {
  var io = IO(new Bundle {
    val valid = Input(Vec(2,Bool()))
    val addr = Input(Vec(2,UInt(32.W)))
    val addr_ok = Output(Vec(2,Bool()))

    val wr   = Input(Vec(2,Bool()))
    val size = Input(Vec(2,UInt(2.W)))
    val data_ok = Output(Vec(2,Bool()))

    val rdata = Output(Vec(2,UInt(32.W)))

    val wdata = Input(Vec(2,UInt(32.W)))

    val axi     = new Bundle{
      val readAddr  =  Decoupled(new AXIAddr(32,4))
      val readData  = Flipped(Decoupled(new AXIReadData(32,4)))
      val writeAddr  =  Decoupled(new AXIAddr(32,4))
      val writeData  = Decoupled(new AXIWriteData(32,4))
      val writeResp =  Flipped(Decoupled( new AXIWriteResponse(4)))
    }
    val debug_total_count = Output(UInt(32.W))  // cache总查询次数
    val debug_pure_hit_count = Output(UInt(32.W))
    val debug_hit_count   = Output(UInt(32.W))  // cache命中数

  })
  val sIDLE::sLOOKUP::sREPLACE::sREFILL::sWaiting::sEviction::sEvictionWaiting::Nil =Enum(7)

  val state = RegInit(0.U(3.W))
  val debug_counter = RegInit(0.U(10.W))
  val queue = Module(new Queue(new QueueItem, 2))
  val port_valid = RegInit(VecInit(Seq.fill(2)(true.B)))
  io.data_ok(1):= false.B
  io.data_ok(0):= false.B
  io.rdata(1) := 0.U
  io.rdata(0) := 0.U
  val polling = RegInit(false.B)
  polling := ~polling
  io.addr_ok(0) := port_valid(0) && polling
  io.addr_ok(1) := port_valid(1) && !polling
  when(queue.io.enq.ready){
    when(io.valid(0) && io.addr_ok(0)){
      queue.io.enq.valid := true.B
      queue.io.enq.bits.addr := io.addr(0)
      queue.io.enq.bits.port := 0.U
      queue.io.enq.bits.wr := io.wr(0)
      queue.io.enq.bits.size := io.size(0)
      queue.io.enq.bits.wdata := io.wdata(0)
      port_valid(0) := false.B
    }.elsewhen(io.valid(1) && io.addr_ok(1)){
      queue.io.enq.bits.addr := io.addr(1)
      queue.io.enq.bits.port := 1.U
      queue.io.enq.bits.wr := io.wr(1)
      queue.io.enq.bits.size := io.size(1)
      queue.io.enq.bits.wdata := io.wdata(1)
      queue.io.enq.valid := true.B
      port_valid(1) := false.B
    }.otherwise{
      queue.io.enq.bits := DontCare
      queue.io.enq.valid := false.B
    }
  }.otherwise{
    queue.io.enq.bits := DontCare
    queue.io.enq.valid := false.B
  }
  queue.io.deq.ready := false.B

  /**
   * cache的数据
   */
  val dataMem = List.fill(config.wayNum) {
    List.fill(config.bankNum) { // 4字节长就有4个
      Module(new dcache_data_bank(config.lineNum))
    }
  }
  val tagvMem = List.fill(config.wayNum) {
    Module(new dcache_tagv(config.tagWidth,config.lineNum))
  }
  val dirtyMem = List.fill(config.wayNum) {
    Module(new dcache_dirty(config.lineNum))
  }

//  val selection = Module(new ByteSelection)
  val wstrb = Module(new Wstrb())


  val lruMem = Module(new LruMem(config.wayNumWidth,config.indexWidth))// lru
//  val victim = Module(new Victim(config)) // 写代理
  val invalidateQueue = Module(new InvalidateQueue(new CacheConfig()))

  io.axi.writeAddr <> invalidateQueue.io.writeAddr
  io.axi.writeData <> invalidateQueue.io.writeData
  io.axi.writeResp <> invalidateQueue.io.writeResp

  val cache_hit_onehot = Wire(Vec(config.wayNum, Bool())) // 命中的路
  val cache_hit_way = Wire(UInt(config.wayNumWidth.W))

//  val addr_r = RegInit(0.U(32.W)) //地址寄存器
  val addr_r = Wire(UInt(32.W)) //地址寄存器
  addr_r := queue.io.deq.bits.addr
//  val size_r = RegInit(2.U(2.W))
  val size_r = Wire(UInt(2.W))
  size_r := queue.io.deq.bits.size

  val wr_r = Wire(Bool())
  wr_r := queue.io.deq.bits.wr

  val wdata_r = Wire(UInt(32.W))
  wdata_r := queue.io.deq.bits.wdata

  val port_r = Wire(UInt(1.W))
  port_r := queue.io.deq.bits.port
  val bData = new BankData(config)
  val tagvData = new TAGVData(config)
  val dirtyData = new DirtyData(config)

  val dataMemEn = RegInit(false.B)
  val bDataWtBank = RegInit(0.U((config.offsetWidth-2).W))
  val AXI_readyReg = RegInit(false.B)

  val is_hitWay = Wire(Bool())
//  val addrokReg = RegInit(false.B)
  val index  = Wire(UInt(config.indexWidth.W))
  val bankIndex = Wire(UInt((config.offsetWidth-2).W))
  val tag  = Wire(UInt(config.tagWidth.W))

  val waySelReg = RegInit(0.U(config.wayNumWidth.W))
//  val eviction = Wire(Bool()) //出现驱逐
  is_hitWay := cache_hit_onehot.asUInt().orR() // 判断是否命中cache
//  io.addr_ok := state === sIDLE
  state := sIDLE

  index := config.getIndex(addr_r)

  bankIndex := config.getBankIndex(addr_r) //offset去掉尾部2位

  tag := config.getTag(addr_r)


  /**
   * 初始化 ram
   */
  //初始化
  for(way <- 0 until config.wayNum){
    tagvMem(way).io.clka := clock
    tagvMem(way).io.clkb := clock
    tagvMem(way).io.wea := tagvData.wEn(way)
    tagvMem(way).io.ena := true.B
    tagvMem(way).io.dina := 0.U
    dirtyMem(way).io.clka := clock
    dirtyMem(way).io.clkb := clock
    dirtyMem(way).io.wea := false.B
    dirtyMem(way).io.ena := true.B
    for(bank <- 0 until config.bankNum){
      dataMem(way)(bank).io.clka := clock
      dataMem(way)(bank).io.clkb := clock
      dataMem(way)(bank).io.wea := Mux(bData.wEn(way)(bank),wstrb.io.mask,"b0000".U)
      dataMem(way)(bank).io.ena := true.B
    }
  }

  for(way <- 0 until config.wayNum){
    tagvMem(way).io.addrb := 0.U
    tagvMem(way).io.enb := false.B
    tagvMem(way).io.web := false.B
    tagvMem(way).io.dinb := 0.U

    dirtyMem(way).io.addrb := 0.U
    dirtyMem(way).io.enb := false.B
    dirtyMem(way).io.web := false.B
    dirtyMem(way).io.dinb := 0.U
    for(bank <- 0 until config.bankNum){
      dataMem(way)(bank).io.addrb := 0.U
      dataMem(way)(bank).io.enb := false.B
      dataMem(way)(bank).io.web := false.B
      dataMem(way)(bank).io.dinb := 0.U
    }
  }
  /**
   * dataMem
   */

  for(way <- 0 until  config.wayNum){
    tagvMem(way).io.addra := config.getIndexByExpression(state===sIDLE,queue.io.deq.bits.addr,addr_r)
    tagvData.read(way).tag := tagvMem(way).io.douta(config.tagWidth-1,0)
    tagvData.read(way).valid := tagvMem(way).io.douta(config.tagWidth)
    dirtyMem(way).io.addra := config.getIndexByExpression(state===sIDLE,queue.io.deq.bits.addr,addr_r)
    dirtyData.read(way) := dirtyMem(way).io.douta
    for(bank <- 0 until config.bankNum){
      dataMem(way)(bank).io.addra := config.getIndexByExpression(state===sIDLE,queue.io.deq.bits.addr,addr_r)
      bData.read(way)(bank)  := dataMem(way)(bank).io.douta
    }
  }

  dataMem.indices.foreach(way => {
    dataMem(way).indices.foreach(bank => {
      val m = dataMem(way)(bank)
      when(bData.wEn(way)(bank)) {
        when(state === sREFILL) {
          dirtyMem(way).io.wea := true.B
          when(wr_r){
            dirtyMem(way).io.dina := true.B
          }.otherwise {
            dirtyMem(way).io.dina := false.B
          }
          when(bank.U === bDataWtBank){
            when(wr_r) {
              m.io.dina  := wdata_r
            }.otherwise {
              m.io.dina  :=  io.axi.readData.bits.data
            }
          }
        }.elsewhen(state===sLOOKUP&& is_hitWay && cache_hit_way === way.U && wr_r){
          // write hit
          dirtyMem(way).io.wea := true.B
          dirtyMem(way).io.dina := true.B //旧数据
          when(bank.U === bankIndex){
            m.io.dina := wdata_r
          }
        }
      }
    })
  })

  for(way <- 0 until config.wayNum){
    val m = tagvMem(way)
    when(tagvData.wEn(way)){//写使能
      m.io.dina := Cat(true.B,tag)
    }
  }
  for(way<- 0 until config.wayNum){
    for(bank <- 0 until config.bankNum) {
      bData.wEn(way)(bank) := (state===sREFILL && waySelReg === way.U && bDataWtBank ===bank.U && !clock.asBool() )||
        //          (state===sREPLACE && victim.io.find && waySelReg === way.U) ||// 如果victim buffer里找到了
        (state === sLOOKUP && wr_r && cache_hit_way === way.U && bankIndex ===bank.U)|| // 写命中
//        (state === sVictimReplace && waySelReg === way.U && bankIndex ===bank.U)||  //读/写命中victim
        (is_hitWay && state === sREFILL && waySelReg === way.U && bDataWtBank ===bank.U && wr_r === true.B)
    }
  }
  for(way<- 0 until config.wayNum){
    tagvData.wEn(way) := (state === sREFILL && waySelReg===way.U)
//      (state === sVictimReplace  && waySelReg===way.U)
  }

  tagvData.write := Cat(true.B,tag)
  bData.addr := index
  tagvData.addr := index
  cache_hit_way := OHToUInt(cache_hit_onehot)
  // 判断是否命中cache
  cache_hit_onehot.indices.foreach(i=>{
    cache_hit_onehot(i) :=  tagvData.read(i).tag === tag && tagvData.read(i).valid
  })

  /**
   * IO初始化
   */

  wstrb.io.offset := addr_r(1,0)
  wstrb.io.size := size_r
  /**
   * LRU 配置
   */
  lruMem.io.setAddr := index
  lruMem.io.visit := 0.U
  lruMem.io.visitValid := is_hitWay


  /**
   * debug
   */
  val debug_total_count_r = RegInit(0.U(32.W))
  val debug_hit_count_r = RegInit(0.U(32.W))
  val debug_pure_hit_count_r = RegInit(0.U(32.W))
  io.debug_pure_hit_count := debug_pure_hit_count_r
  io.debug_total_count := debug_total_count_r
  io.debug_hit_count := debug_hit_count_r

  when(state===sLOOKUP){
    debug_total_count_r := debug_total_count_r + 1.U
    when(is_hitWay){
      debug_pure_hit_count_r := debug_pure_hit_count_r + 1.U
      debug_hit_count_r := debug_hit_count_r + 1.U
    }
  }
//  when(state === sVictimReplace){
//    debug_hit_count_r := debug_hit_count_r + 1.U
//  }
  /**
   * 驱逐
   */
  val evictionCounter = RegInit(0.U(config.bankNumWidth.W))
  invalidateQueue.io.addr := Cat(tagvData.read(waySelReg).tag,index,0.U(config.offsetWidth.W))
  invalidateQueue.io.wdata := bData.read(waySelReg)(evictionCounter)
  invalidateQueue.io.req := false.B
  /**
   * Cache状态机
   */


  io.axi.readAddr.valid :=  false.B
//  victim.io.qaddr := Cat(addr_r(31,config.offsetWidth),0.U(config.offsetWidth.W))
  switch(state){
    is(sIDLE){
      when(queue.io.count=/=0.U  ){
        state := sLOOKUP
      }
    }
    is(sLOOKUP){
      when(is_hitWay){
        lruMem.io.visit := cache_hit_way // lru记录命中
        state := sIDLE
//        when(io.valid) {
//          // 直接进入下一轮
//          state := sLOOKUP
//          addr_r := io.addr
//          size_r := io.size
//          wr_r := io.wr
//          wdata_r := io.wdata
//        }.otherwise {
//
//        }
        port_valid(port_r) := true.B
        io.data_ok(port_r) := true.B
        queue.io.deq.ready := true.B
        when(!wr_r) {
          io.rdata(port_r) := bData.read(cache_hit_way)(bankIndex)
        }
      }.otherwise {
        //没命中,检查victim
//        state := sCheckVictim
//        victim.io.qvalid := true.B
        io.axi.readAddr.valid := true.B
        state := sREPLACE
        waySelReg := lruMem.io.waySel
      }
    }
    is(sREPLACE){
      //在此阶段完成驱逐
      bDataWtBank := bankIndex
      when(io.axi.readAddr.ready) {
        when(dirtyData.read(waySelReg)===true.B){
          state := sEvictionWaiting
          invalidateQueue.io.req := true.B
        }.otherwise{
          state := sREFILL
        }
      }.otherwise{
        state := sREPLACE
        io.axi.readAddr.valid := true.B
      }
    }
    is(sEvictionWaiting){
      when(invalidateQueue.io.data_start){
        state := sEviction
        evictionCounter := 0.U
      }.otherwise{
        state := sEvictionWaiting
      }
    }
    is(sEviction){
      evictionCounter := evictionCounter + 1.U
      state := sEviction
      when(evictionCounter===(config.bankNum -  1).U){
        state := sREFILL
      }
    }
    is(sREFILL){
      // 取数据，重写TAGV
      state := sREFILL
      when(io.axi.readData.valid && io.axi.readData.bits.id === io.axi.readAddr.bits.id){
        bDataWtBank := bDataWtBank+1.U
        when(io.axi.readData.bits.last){
          state := sWaiting
          when(!wr_r){
            io.rdata(port_r) := bData.read(waySelReg)(bankIndex)
            io.data_ok(port_r) := true.B
          }
        }
      }
    }
    is(sWaiting){
      state := sIDLE
      io.rdata(port_r) := bData.read(waySelReg)(bankIndex)
      io.data_ok(port_r) := true.B
      port_valid(port_r) := true.B
      queue.io.deq.ready := true.B
    }
  }

  /**
   * 驱逐写控制信号
   */
//  debug_counter := debug_counter + 1.U
//  fetch_ready_go := victim.io.fill_valid
//  when(state ===sEviction) {
//    // 替换掉数据到victim中
//    victim.io.wdata := bData.read(waySelReg)(victimEvictioncounter)
//  }.elsewhen(state === sCheckVictim && victim.io.find){
//    // sCheckVictim只会有一拍，因此不用担心lruMem.io.waySel会变化
//    // 从victim中取数据并替换
//    victim.io.wdata := DontCare
//  }.elsewhen(state === sVictimReplace){
//    // 从victim中取数据并替换
//    victim.io.waddr := DontCare
//    victim.io.wdata := RegNext(bData.read(waySelReg)(victimEvictioncounter))
//  }.otherwise{
//    victim.io.wdata := DontCare
//    victim.io.waddr := DontCare
//    //    eviction := false.B
//    victim.io.wvalid := false.B
//    victim.io.dirty := false.B
//    victim.io.wdata:=DontCare
//  }
//  victim.io.waddr := DontCare

  /**
   * axi访问设置
   */
  io.axi.readAddr.bits.id := 1.U
//  io.axi.readAddr.bits.len := 6.U
  io.axi.readAddr.bits.len := (config.bankNum - 1).U
  io.axi.readAddr.bits.size := 2.U // 4B
  io.axi.readAddr.bits.addr := addr_r
  io.axi.readAddr.bits.cache := 0.U
  io.axi.readAddr.bits.lock := 0.U
  io.axi.readAddr.bits.prot := 0.U
  io.axi.readAddr.bits.burst := 2.U //突发模式2

  io.axi.readData.ready := state ===  sREFILL


}

object DCache extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new DCache(new CacheConfig()))))
}