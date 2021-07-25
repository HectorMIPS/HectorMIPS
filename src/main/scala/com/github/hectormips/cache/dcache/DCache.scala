package com.github.hectormips.cache.dcache

import com.github.hectormips.amba._
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.cache.lru.LruMem
import com.github.hectormips.cache.utils.Wstrb
import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

class BankData(val config: CacheConfig) extends Bundle {
  val addr = Wire(UInt(config.indexWidth.W))
  val read = Wire(Vec(config.wayNum, Vec(config.bankNum,UInt(32.W))))
  //  val write = Vec(config.bankNum, UInt(32.W))
  val wEn = Wire(Vec(config.wayNum, Vec(config.bankNum,Bool())))
}
class TAGVData(val config: CacheConfig) extends Bundle {
  val addr = Wire(UInt(config.indexWidth.W))
  val read = Wire(Vec(config.wayNum, UInt((config.tagWidth).W))) //一次读n个
  val write = Wire(UInt((config.tagWidth).W)) //一次写1个
  val wEn = Wire(Vec(config.wayNum, Bool()))
}
class DirtyData(val config:CacheConfig) extends Bundle{
  val addr = UInt(config.indexWidth.W)
  val read = Vec(config.wayNum, Bool())
  val write = Bool()
  val wEn = Vec(config.wayNum, Bool())
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
  val sIDLE::sLOOKUP::sCheckVictim::sREPLACE::sREFILL::sWaiting::sVictimReplace::sEviction::Nil =Enum(8)
  val state = RegInit(5.U(3.W))
  val debug_counter = RegInit(0.U(10.W))

  /**
   * cache的数据
   */
  val validMem = RegInit(VecInit(Seq.fill(config.wayNum)(VecInit(Seq.fill(config.lineNum)(false.B)))))
  val dirtyMem = RegInit(VecInit(Seq.fill(config.wayNum)(VecInit(Seq.fill(config.lineNum)(false.B)))))

//  val dirtyMem = Mem(config.lineNum,Vec(config.wayNum, Bool())) //注意前面的是way

  val dataMem = List.fill(config.wayNum) {
    List.fill(config.bankNum) { // 4字节长就有4个
      SyncReadMem(config.lineNum,Vec(4, UInt(8.W)))
    }
  }
  val tagvMem = List.fill(config.wayNum) {
    SyncReadMem(config.lineNum, UInt((config.tagWidth+1).W))
  }
//  val selection = Module(new ByteSelection)
  val wstrb = Module(new Wstrb())

  val lruMem = Module(new LruMem(config.wayNumWidth,config.indexWidth))// lru
  val victim = Module(new Victim(config)) // 写代理
  io.axi.writeAddr <> victim.io.axi.writeAddr
  io.axi.writeData <> victim.io.axi.writeData
  io.axi.writeResp <> victim.io.axi.writeResp

  val cache_hit_onehot = Wire(Vec(config.wayNum, Bool())) // 命中的路
  val cache_hit_way = Wire(UInt(config.wayNumWidth.W))

  val addr_r = RegInit(0.U(32.W)) //地址寄存器
  val size_r = RegInit(2.U(2.W))
  val wr_r = RegInit(false.B)
  val wdata_r = RegInit(0.U(32.W))

  val bData = new BankData(config)
  val tagvData = new TAGVData(config)
  val dataMemEn = RegInit(false.B)
  val bDataWtBank = RegInit(0.U((config.offsetWidth-2).W))
  val AXI_readyReg = RegInit(false.B)

  val is_hitWay = Wire(Bool())
//  val addrokReg = RegInit(false.B)
  val index  = Wire(UInt(config.indexWidth.W))
  val bankIndex = Wire(UInt((config.offsetWidth-2).W))
  val tag  = Wire(UInt(config.tagWidth.W))

  val waySelReg = RegInit(0.U(config.wayNumWidth.W))
  val fetch_ready_go = Wire(Bool())
//  val eviction = Wire(Bool()) //出现驱逐
  is_hitWay := cache_hit_onehot.asUInt().orR() // 判断是否命中cache
  io.addr_ok := state === sIDLE
  state := sIDLE

  index := config.getIndex(addr_r)

  bankIndex := config.getBankIndex(addr_r) //offset去掉尾部2位

  tag := config.getTag(addr_r)
  /**
   * victim
   */
  //   用于驱逐的计数器
  val victimEvictioncounter = RegInit((0.U(config.bankNumWidth.W)))
  // 用于接收新数据的计数器
  val victimRestoreCounter = RegInit(0.U(config.bankNumWidth.W))
  victim.io.qvalid := false.B
  victim.io.wvalid := false.B
  victim.io.dirty := dirtyMem(waySelReg)(bData.addr)

  /**
   * dataMem
   */
  //  val writeData
  val temp_victim_odata_vec = Wire(Vec(4,UInt(8.W)))
  when(state===sVictimReplace){
    temp_victim_odata_vec(0) := victim.io.qdata(7,0)
    temp_victim_odata_vec(1) := victim.io.qdata(15,8)
    temp_victim_odata_vec(2) := victim.io.qdata(23,16)
    temp_victim_odata_vec(3) := victim.io.qdata(31,24)
  }.otherwise{
    temp_victim_odata_vec := DontCare
  }
  val _bdata_vec = Wire(Vec(config.bankNum,Vec(4,UInt(8.W))))
  for(bank <- 0 until config.bankNum){
    _bdata_vec(bank)(0) := io.axi.readData.bits.data(7,0)
    _bdata_vec(bank)(1) := io.axi.readData.bits.data(15,8)
    _bdata_vec(bank)(2) := io.axi.readData.bits.data(23,16)
    _bdata_vec(bank)(3) := io.axi.readData.bits.data(31,24)
  }
  val _wdata_vec = Wire(Vec(4,UInt(8.W))) // 外部写数据
  _wdata_vec(0) := wdata_r(7,0)
  _wdata_vec(1) := wdata_r(15,8)
  _wdata_vec(2) := wdata_r(23,16)
  _wdata_vec(3) := wdata_r(31,24)
  val _wstrb_vec = Wire(Vec(4,Bool()))
  _wstrb_vec(0) := wstrb.io.mask(0)
  _wstrb_vec(1) := wstrb.io.mask(1)
  _wstrb_vec(2) := wstrb.io.mask(2)
  _wstrb_vec(3) := wstrb.io.mask(3)
  val _read_data = Wire(Vec(config.wayNum,Vec(config.bankNum,Vec(4,UInt(8.W)))))
  _read_data.indices.foreach(way => {
    _read_data(way).indices.foreach(bank =>{
        bData.read(way)(bank) := Cat(_read_data(way)(bank)(3), _read_data(way)(bank)(2), _read_data(way)(bank)(1),
          _read_data(way)(bank)(0))
    })
  })

  for(way <- 0 until  config.wayNum){
    tagvData.read(way) := tagvMem(way).read(config.getIndex(io.addr))
    for(bank <- 0 until config.bankNum){
      _read_data(way)(bank)  := dataMem(way)(bank).read(config.getIndex(io.addr))
    }
  }

  when(state === sVictimReplace) {
    // 用victim里的数据替换
    dataMem.indices.foreach(way => {
      dataMem(way).indices.foreach(bank => {
        val m = dataMem(way)(bank)
        when(bData.wEn(way)(bank)) {
          for (i <- 0 until config.victim_fetch_every_cycle) {
            when(wr_r && bank.U === bankIndex) {
              m.write(bData.addr, _wdata_vec) // 新数据
            }.elsewhen(bank.U === victimRestoreCounter){
              m.write(bData.addr, temp_victim_odata_vec) //重填
            }
            dirtyMem(way)(bData.addr) := false.B
          }
        }
      })
    })
  }.otherwise {
    // 新取的数据
    dataMem.indices.foreach(way => {
      dataMem(way).indices.foreach(bank => {
        val m = dataMem(way)(bank)
        when(bData.wEn(way)(bank)) {
          when(state === sREFILL) {
            when(wr_r){
              dirtyMem(way)(bData.addr) := true.B //Write miss 的 目标
            }.otherwise {
              dirtyMem(way)(bData.addr) := false.B //read miss
            }
            when(bank.U === bDataWtBank){
              when(wr_r) {
                m.write(bData.addr, _wdata_vec, _wstrb_vec)
              }.otherwise {
                m.write(bData.addr, _bdata_vec(bank))
              }
            }
          }.elsewhen(state===sLOOKUP && cache_hit_way === way.U && wr_r){
            // write hit
            dirtyMem(way)(bData.addr) := true.B //旧数据
            when(bank.U === bankIndex){
              m.write(bData.addr, _wdata_vec,_wstrb_vec)
            }
          }
        }
      })
    })
  }

  for(way <- 0 until config.wayNum){
    val m = tagvMem(way)
    when(tagvData.wEn(way)){//写使能
      m.write(tagvData.addr,tagvData.write)
      validMem(way)(tagvData.addr) := true.B
    }
  }
  for(way<- 0 until config.wayNum){
    for(bank <- 0 until config.bankNum) {
      bData.wEn(way)(bank) := (state===sREFILL && waySelReg === way.U && bDataWtBank ===bank.U && !clock.asBool() )||
        //          (state===sREPLACE && victim.io.find && waySelReg === way.U) ||// 如果victim buffer里找到了
        (state === sLOOKUP && wr_r && cache_hit_way === way.U && bankIndex ===bank.U)|| // 写命中
        (state === sVictimReplace && waySelReg === way.U && bankIndex ===bank.U)||  //读/写命中victim
        (is_hitWay && state === sREFILL && waySelReg === way.U && bDataWtBank ===bank.U && wr_r === true.B)
    }
  }
  for(way<- 0 until config.wayNum){
    tagvData.wEn(way) := (state === sREFILL && waySelReg===way.U) ||
      (state === sVictimReplace  && waySelReg===way.U)
  }

  tagvData.write := Cat(true.B,tag)
  bData.addr := index
  tagvData.addr := index
  cache_hit_way := OHToUInt(cache_hit_onehot)
  // 判断是否命中cache
  cache_hit_onehot.indices.foreach(i=>{
    cache_hit_onehot(i) :=  tagvData.read(i) === tag && validMem(i)(index)
  })

  /**
   * IO初始化
   */
  io.data_ok := false.B
  io.rdata := 0.U
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
  when(state === sVictimReplace){
    debug_hit_count_r := debug_hit_count_r + 1.U
  }

  /**
   * Cache状态机
   */


  io.axi.readAddr.valid :=  false.B
  victim.io.qaddr := Cat(io.addr(31,config.offsetWidth),0.U(config.offsetWidth.W))
  switch(state){
    is(sIDLE){
      when(io.valid){
        state := sLOOKUP
//        addrokReg := true.B
        addr_r := io.addr
        size_r := io.size
        wr_r := io.wr
        wdata_r := io.wdata
      }
    }
    is(sLOOKUP){
      when(is_hitWay){
        lruMem.io.visit := cache_hit_way // lru记录命中
        when(io.valid) {
          // 直接进入下一轮
          state := sLOOKUP
          addr_r := io.addr
          size_r := io.size
          wr_r := io.wr
          wdata_r := io.wdata
        }.otherwise {
          state := sIDLE
        }
        io.data_ok := true.B
        when(!wr_r) {
          // 读
          io.rdata := bData.read(cache_hit_way)(bankIndex)
        }
      }.otherwise {
        //没命中,检查victim
        state := sCheckVictim
        victim.io.qvalid := true.B
        victimEvictioncounter := 0.U
        waySelReg := lruMem.io.waySel
      }
    }
    is(sCheckVictim){
      /**
       * 检查victim buffer
       * 如果有：
       *    * 如果当前填充位置没有有效数据
       *      直接填充，进入IDLE阶段
       *    * 如果有有效数据
       *      缓存victim buffer给的数据，进入sVictimReplace阶段
       * 如果没有：
       *    向axi总线发起请求，进入REPLACE阶段
       */

      when(victim.io.find){
        // 在 victim buffer里找到了
          state := sVictimReplace
          victimRestoreCounter := 0.U
          victimEvictioncounter := victimEvictioncounter + 1.U
          victim.io.waddr := Cat(tagvData.read(waySelReg) ,index,0.U(config.offsetWidth.W))
          victim.io.wvalid := true.B
          victim.io.dirty := dirtyMem(waySelReg)(index)
      }.otherwise{
        io.axi.readAddr.valid := true.B
        state := sREPLACE
      }
    }
    is(sVictimReplace){
      // 从victim取出
        // 存储计数器
        victimRestoreCounter := victimRestoreCounter + 1.U
        // 驱逐计数器
        when(victimEvictioncounter=/=0.U) {
          // 等于0 时说明驱逐完成
          victimEvictioncounter := victimEvictioncounter + 1.U
        }
      when(victimRestoreCounter === (config.bankNum-1).U){
        state := sIDLE
        io.data_ok := true.B
        when(!wr_r) {
          // 读
          io.rdata := bData.read(waySelReg)(bankIndex)
        }
      }.otherwise{
        state := sVictimReplace
      }
    }
    is(sREPLACE){
      //在此阶段完成驱逐
      bDataWtBank := bankIndex
      when(io.axi.readAddr.ready) {
        when(validMem(waySelReg)(tagvData.addr)){
          // 驱逐已填充数据
          state := sEviction
          victimEvictioncounter := 0.U //提前一拍清理好计数器
          victim.io.waddr := Cat(tagvData.read(waySelReg), index, 0.U(config.offsetWidth.W))
//          eviction := true.B
          victim.io.wvalid := true.B
          victim.io.dirty := dirtyMem(waySelReg)(index)
        }.otherwise{
          state := sREFILL
        }
      }.otherwise{
        state := sREPLACE
        io.axi.readAddr.valid := true.B
      }
    }
    is(sEviction) {
      victimEvictioncounter := victimEvictioncounter + 1.U
      when(victimEvictioncounter === (config.bankNum - 1).U) {
          state := sREFILL
      }.otherwise{
          state := sEviction
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
            io.rdata := bData.read(waySelReg)(bankIndex)
            io.data_ok := true.B
          }
        }
      }
    }
    is(sWaiting){
      state := sIDLE
      io.rdata := bData.read(waySelReg)(bankIndex)
      io.data_ok := true.B
    }
  }

  /**
   * 驱逐写控制信号
   */
//  debug_counter := debug_counter + 1.U
  fetch_ready_go := victim.io.fill_valid
  when(state ===sEviction) {
    // 替换掉数据到victim中
    victim.io.wdata := bData.read(waySelReg)(victimEvictioncounter)
  }.elsewhen(state === sCheckVictim && victim.io.find){
    // sCheckVictim只会有一拍，因此不用担心lruMem.io.waySel会变化
    // 从victim中取数据并替换
    victim.io.wdata := DontCare
  }.elsewhen(state === sVictimReplace){
    // 从victim中取数据并替换
    victim.io.waddr := DontCare
    victim.io.wdata := RegNext(bData.read(waySelReg)(victimEvictioncounter))
  }.otherwise{
    victim.io.wdata := DontCare
    victim.io.waddr := DontCare
    //    eviction := false.B
    victim.io.wvalid := false.B
    victim.io.dirty := false.B
    victim.io.wdata:=DontCare
  }
  victim.io.waddr := DontCare

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