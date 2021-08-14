package com.github.hectormips.cache.icache

import com.github.hectormips.amba.{AXIAddr, AXIReadData}
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.cache.lru.LruMem
import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chisel3.util.experimental.forceName
import com.github.hectormips.cache.access_judge.physical_addr
import com.github.hectormips.tlb.SearchPort


class ICache(val config: CacheConfig)
  extends Module {
  var io = IO(new Bundle {
    val valid = Input(Bool())
    val addr = Input(UInt(32.W))
    val asid = Input(UInt(8.W)) //进程ID
    val addr_ok = Output(Bool())
    val is_mapped = Input(Bool()) // 是否是map部分
    val is_unmapped_cached = Input(Bool()) // 是否需要cache

    val inst = Output(UInt(64.W))
    val instOK = Output(Bool())
    val ex    = Output(UInt(3.W)) // 例外
    val instPC = Output(UInt(32.W))
    val instValid = Output(UInt())

    val tlb = Flipped(new SearchPort(config.tlbnum))

    val axi = new Bundle {
      val readAddr = Decoupled(new AXIAddr(32, 4))
      val readData = Flipped(Decoupled(new AXIReadData(32, 4)))
    }

    val debug_total_count = Output(UInt(32.W)) // cache总查询次数
    val debug_hit_count = Output(UInt(32.W)) // cache命中数
  })
  val sIDLE :: sLOOKUP :: sREPLACE :: sREFILL :: sQueryPrefetch :: sWaitPrefetch::sFetchHandshake::sFetchRecv :: Nil = Enum(8)

  val state = RegInit(0.U(4.W)) // 初始化阶段
  val prefetch = Module(new Prefetch(config))


  /**
   * cache的数据
   */
  val dataMem = List.fill(config.wayNum) {
    List.fill(config.bankNum) { // 4字节长就有4个
      Module(new icache_data_bank(config))
    }
  }
  val tagvMem = List.fill(config.wayNum) {
    Module(new icache_tagv(config))
  }

  //  val validMem = RegInit(VecInit(Seq.fill(config.wayNum)(VecInit(Seq.fill(config.lineNum)(false.B)))))

  val lruMem = Module(new LruMem(config.wayNumWidth, config.indexWidth)) // lru

  val cache_hit_onehot = Wire(Vec(config.wayNum, Bool())) // 命中的路
  val cache_hit_way = Wire(UInt(config.wayNumWidth.W))
  val nextline_hit_onehot = Wire(Vec(config.wayNum, Bool())) // 命中的路
//  val nextline_hit_way = Wire(UInt(config.wayNumWidth.W))

  val addr_r = RegInit(0.U(32.W)) //地址寄存器
  val addr = Wire(UInt(32.W))
  val bData = new BankData(config)
  val tagvData = new TAGVData(config)
  val prefetchTagv = new TAGVData(config)

  val dataMemEn = RegInit(false.B)
  val bDataWtBank = RegInit(0.U((config.offsetWidth - 2).W))
  val AXI_readyReg = RegInit(false.B)

  val is_hitWay = Wire(Bool())
  val is_nextline_hitWay = Wire(Bool())
  is_nextline_hitWay := nextline_hit_onehot.asUInt().orR()
  is_hitWay := cache_hit_onehot.asUInt().orR() // 判断是否命中cache


  val addrokReg = RegInit(false.B)

  //  io.addr_ok := state === sIDLE
  io.addr_ok := state === sIDLE || (state === sLOOKUP && is_hitWay)
  io.instPC := addr_r
  addr := Mux(state === sIDLE || state === sLOOKUP && is_hitWay && io.valid, io.addr, addr_r)

  state := sIDLE

  val index = Wire(UInt(config.indexWidth.W))
  val nextline_index = Wire(UInt(config.indexWidth.W))
  val bankIndex = Wire(UInt((config.offsetWidth - 2).W))
  val tag = Wire(UInt(config.tagWidth.W))
  val nextline_tag = Wire(UInt(config.tagWidth.W))
  val waySelReg = RegInit(0.U(config.wayNumWidth.W))
  val prefetch_addr = Wire(UInt(32.W))
  val tmp_inst = RegInit(0.U(32.W))
  val prefetch_delay_display = RegInit(false.B)
  val prefetch_delay_display_data = RegInit(0.U(64.W))
  val prefetch_delay_display_valid =RegInit(false.B)

  prefetch_addr := Cat(addr_r(31, config.offsetWidth), 0.U(config.offsetWidth.W)) + (4 * config.bankNum).U
  index := config.getIndex(addr_r)
  nextline_index := config.getIndex(prefetch_addr)
  bankIndex := config.getBankIndex(addr_r)
  tag := config.getTag(addr_r) // physical tag
  nextline_tag := config.getTag(prefetch_addr)

  //  val dataBankWtMask = WireInit(VecInit.tabulate(4) { _ => true.B })
  //  val writeData =WireInit(VecInit.tabulate(4) { _ => 0.U(8.W) })
  /**
   * dataMem
   */
  for (way <- 0 until config.wayNum) {
    tagvMem(way).io.clka := clock
    tagvMem(way).io.wea := tagvData.wEn(way)
    tagvMem(way).io.ena := true.B

    tagvMem(way).io.clkb := clock
    tagvMem(way).io.web := 0.U //第二端口禁止写
    tagvMem(way).io.enb := true.B
    tagvMem(way).io.dinb := 0.U
    for (bank <- 0 until config.bankNum) {
      dataMem(way)(bank).io.clka := clock
      dataMem(way)(bank).io.wea := bData.wEn(way)(bank)
      dataMem(way)(bank).io.ena := true.B
    }
  }

  for (way <- 0 until config.wayNum) {
    tagvMem(way).io.addra := config.getIndexByExpression(state === sIDLE, io.addr, addr)
    tagvData.read(way).tag := tagvMem(way).io.douta(config.tagWidth - 1, 0)
    tagvData.read(way).valid := tagvMem(way).io.douta(config.tagWidth)

    tagvMem(way).io.addrb := config.getIndex(prefetch_addr)
    prefetchTagv.read(way).tag := tagvMem(way).io.doutb(config.tagWidth - 1, 0)
    prefetchTagv.read(way).valid := tagvMem(way).io.doutb(config.tagWidth)

    for (bank <- 0 until config.bankNum) {
      dataMem(way)(bank).io.addra := config.getIndexByExpression(state === sIDLE, io.addr, addr)
      bData.read(way)(bank) := dataMem(way)(bank).io.douta
    }
  }

  for (way <- 0 until config.wayNum) {
    when(tagvData.wEn(way)) { //写使能
      tagvMem(way).io.dina := Cat(true.B, tag)
      //      tagvMem(way).write(tagvData.addr,tag)
      //      validMem(way)(tagvData.addr) := true.B
    }
    for (bank <- 0 until config.bankNum) {
      dataMem(way)(bank).io.dina := Mux(state === sQueryPrefetch, prefetch.io.query_data(bank), io.axi.readData.bits.data)
    }
  }
  for (way <- 0 until config.wayNum) {
    for (bank <- 0 until config.bankNum) {
      bData.wEn(way)(bank) := (state === sREFILL && waySelReg === way.U && bDataWtBank === bank.U) || //查询axi回填
        (state === sQueryPrefetch && prefetch.io.query_finded && waySelReg === way.U) //预取命中
    }
  }
  for (way <- 0 until config.wayNum) {
    tagvData.wEn(way) := (state === sREFILL && waySelReg === way.U) ||
      (state === sQueryPrefetch && prefetch.io.query_finded && waySelReg === way.U)
    prefetchTagv.wEn(way) := false.B
  }

//  nextline_hit_way := OHToUInt(nextline_hit_onehot)
  cache_hit_way := OHToUInt(cache_hit_onehot)
  // 判断是否命中cache
  for(i <- 0 until config.wayNum){
    cache_hit_onehot(i) := tagvData.read(i).tag === tag & tagvData.read(i).valid
    nextline_hit_onehot(i) := prefetchTagv.read(i).tag === nextline_tag && prefetchTagv.read(i).valid
  }

  /**
   * IO初始化
   */

  io.instValid := Mux(prefetch_delay_display,prefetch_delay_display_valid,Cat(bankIndex =/= ((config.bankNum) - 1).U, true.B)) //==bank-1
  io.instOK := false.B
  //  printf("%d\n",io.instValid)
  io.inst := 0.U

  /**
   * LRU 配置
   */
  lruMem.io.setAddr := index
  lruMem.io.visit := 0.U
  lruMem.io.visitValid := is_hitWay


  /**
   * TLB配置
   */
  io.ex := io.tlb.ex & 3.U //不会触发最高位的例外
  io.tlb.vpn2 := io.addr(31,13) //31,13
  io.tlb.odd_page := io.addr(12) //12
  io.tlb.asid := io.asid

  /**
   * axi访问设置
   */
  io.axi.readAddr.bits.id := Mux(prefetch.io.use_axi, prefetch.io.readAddr.bits.id, 0.U)
  io.axi.readAddr.bits.len := Mux(state===sFetchHandshake,1.U,(config.bankNum - 1).U)
  io.axi.readAddr.bits.size := 2.U // 4B
  io.axi.readAddr.bits.addr := Mux(prefetch.io.use_axi, prefetch.io.readAddr.bits.addr, addr_r)
  io.axi.readAddr.bits.cache := 0.U
  io.axi.readAddr.bits.lock := 0.U
  io.axi.readAddr.bits.prot := 0.U
  io.axi.readAddr.bits.burst := Mux(state===sFetchHandshake,1.U,2.U)
  io.axi.readAddr.valid := Mux(prefetch.io.use_axi, prefetch.io.readAddr.valid, state === sREPLACE || state===sFetchHandshake)

  io.axi.readData.ready := state === sREFILL | prefetch.io.readData.ready | state === sFetchRecv

  /**
   * 预取器
   */
  prefetch.io.req_valid := ( state === sLOOKUP || state === sREFILL) && !is_nextline_hitWay
  prefetch.io.req_addr := prefetch_addr

  prefetch.io.query_addr := addr_r
  prefetch.io.query_valid := state === sQueryPrefetch
  //  prefetch.io.query_valid := false.B
  prefetch.io.readAddr.ready := io.axi.readAddr.ready
  prefetch.io.readData.bits <> io.axi.readData.bits
  prefetch.io.readData.valid := io.axi.readData.valid
  /**
   * debug
   */
  val debug_total_count_r = RegInit(0.U(32.W))
  val debug_hit_count_r = RegInit(0.U(32.W))

  io.debug_total_count := debug_total_count_r
  io.debug_hit_count := debug_hit_count_r
  dontTouch(io.debug_total_count)
  dontTouch(io.debug_hit_count)
  when(state === sLOOKUP) {
    debug_total_count_r := debug_total_count_r + 1.U
    when(is_hitWay) {
      debug_hit_count_r := debug_hit_count_r + 1.U
    }
  }
  when(state === sQueryPrefetch){
    when(prefetch.io.query_finded){
      debug_hit_count_r := debug_hit_count_r + 1.U
    }
  }

  /**
   * 时序优化：延迟prefetch数据给出
   */
  when(prefetch_delay_display){
    prefetch_delay_display := false.B
    io.inst := prefetch_delay_display_data
    io.instOK := true.B
    io.instValid := prefetch_delay_display_valid
  }
  /**
   * Cache状态机
   */
  switch(state) {
    is(sIDLE) {
      when(io.valid) {
        when(io.is_mapped) {//允许映射
          when(io.tlb.found) {
            when(io.tlb.c === 3.U) {
              state := sLOOKUP // cache
            }.otherwise {
              state := sFetchHandshake // uncache
            }
            addr_r := get_physical_addr(Cat(io.tlb.pfn, io.addr(11, 0)))
          }.otherwise {
            state := sIDLE            // TLB Miss
          }
        }.otherwise{
          //不允许映射
          addr_r := get_physical_addr(io.addr)
            when(io.is_unmapped_cached){ // unmap可以cache
            state := sLOOKUP // cache
          }.otherwise{//不能cache
            state := sFetchHandshake // uncache
          }
        }
      }
    }
    is(sLOOKUP) {
      when(is_hitWay) {
        when(bankIndex === (config.bankNum - 1).U) {
          io.inst := Cat(0.U(32.W), bData.read(cache_hit_way)(bankIndex))
        }.otherwise {
          io.inst := Cat(bData.read(cache_hit_way)(bankIndex + 1.U), bData.read(cache_hit_way)(bankIndex))
        }
        io.instOK := true.B
        lruMem.io.visit := cache_hit_way // lru记录命中
        when(io.valid) {
          when(io.is_mapped) {//允许映射
            when(io.tlb.found) {
              when(io.tlb.c === 3.U) {
                state := sLOOKUP // cache
              }.otherwise {
                state := sFetchHandshake // uncache
              }
              addr_r := get_physical_addr(Cat(io.tlb.pfn, io.addr(11, 0)))
            }.otherwise {
              state := sIDLE            // TLB Miss
            }
          }.otherwise{
            //不允许映射
            addr_r := get_physical_addr(io.addr)
            when(io.is_unmapped_cached){ // unmap可以cache
              state := sLOOKUP // cache
            }.otherwise{//不能cache
              state := sFetchHandshake // uncache
            }
          }
        }
      }.otherwise {
        //没命中,尝试请求
        io.inst := 0.U
        io.instOK := false.B
        state := sQueryPrefetch
        //        prefetch.io.query_valid := true.B // 查询预取器;虽然当拍就能得到结果，但是为了缩短路径，这里延长了一拍
        waySelReg := lruMem.io.waySel
        //        io.axi.readAddr.valid := true.B
      }
    }
    is(sQueryPrefetch) {
      // 预取命中
      //      prefetch.io.query_valid := true.B
      when(prefetch.io.query_finded) {
        //如果找到了
        state := sIDLE
        prefetch_delay_display := true.B
        prefetch_delay_display_valid := io.instValid
        when(bankIndex === (config.bankNum - 1).U) {
          prefetch_delay_display_data := Cat(0.U(32.W), prefetch.io.query_data(bankIndex))
        }.otherwise {
          prefetch_delay_display_data := Cat(prefetch.io.query_data(bankIndex + 1.U), prefetch.io.query_data(bankIndex))
        }
      }.elsewhen(prefetch.io.query_wait){
        state := sQueryPrefetch
      }.otherwise {
        when(prefetch.io.use_axi){
          state := sQueryPrefetch
        }.otherwise{
          state := sREPLACE
        }
      }
    }
    is(sREPLACE) {
      // 发起替换请求
      bDataWtBank := bankIndex
      when(io.axi.readAddr.ready) {
        state := sREFILL
      }.otherwise {
        state := sREPLACE
      }
    }
    is(sREFILL) {
      // 接收数据；同时也会发起预取请求，重写TAGV
      state := sREFILL
      when(io.axi.readData.valid && io.axi.readData.bits.id === 0.U) {
        bDataWtBank := bDataWtBank + 1.U
        when(bDataWtBank === (bankIndex + 3.U)) {
          // 关键字优先
          when(bankIndex === (config.bankNum - 1).U) {
            io.inst := Cat(0.U(32.W), bData.read(cache_hit_way)(bankIndex))
          }.otherwise {
            io.inst := Cat(bData.read(cache_hit_way)(bankIndex + 1.U), bData.read(cache_hit_way)(bankIndex))
          }
          io.instOK := true.B
        }
        when(io.axi.readData.bits.last) {
          state := sIDLE
        }
      }
    }
    is(sFetchHandshake){
      //uncache取
      when(io.axi.readAddr.valid && io.axi.readAddr.ready && io.axi.readAddr.bits.id===0.U){
        state := sFetchRecv
      }.otherwise{
        state := sFetchHandshake
      }
    }
    is(sFetchRecv){
      when(io.axi.readData.valid && io.axi.readData.ready && io.axi.readData.bits.id===0.U){
        when(io.axi.readData.bits.last){
          io.inst := Cat(io.axi.readData.bits.data,tmp_inst)
          io.instOK := true.B
          io.instValid := true.B
          state := sIDLE
        }.otherwise{
          tmp_inst := io.axi.readData.bits.data
          state := sFetchRecv
        }
      }.otherwise{
        state := sFetchRecv
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
  def get_physical_addr(virtual_addr:UInt):UInt= {
    val converter = Module(new physical_addr)
    converter.io.virtual_addr := virtual_addr
    converter.io.physical_addr
  }

}


class BankData(val config: CacheConfig) extends Bundle {
  //  val addr = Wire(Vec(2,UInt(config.indexWidth.W)))
  val read = Wire(Vec(config.wayNum, Vec(config.bankNum, UInt(32.W))))
  //  val write = Vec(config.bankNum, UInt(32.W))
  val wEn = Wire(Vec(config.wayNum, Vec(config.bankNum, Bool())))
}

class tagv(tagWidth: Int) extends Bundle {
  val tag = UInt(tagWidth.W)
  val valid = Bool()
}

class TAGVData(val config: CacheConfig) extends Bundle {
  //  val addr = Wire(Vec(2,UInt(config.indexWidth.W)))
  val read = Wire(Vec(config.wayNum, new tagv(config.tagWidth))) //一次读n个)
  //  val write = Wire(UInt((config.tagWidth+1).W)) //一次写1个
  val wEn = Wire(Vec(config.wayNum, Bool()))
}

object ICache extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new ICache(new CacheConfig()))))
}
