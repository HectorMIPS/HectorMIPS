package com.github.hectormips.cache.dcache

import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chisel3.util._
import com.github.hectormips.amba._
import com.github.hectormips.cache.access_judge.physical_addr
import com.github.hectormips.cache.lru.LruMem
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.tlb.SearchPort

class QueueItem extends Bundle {
  val port = UInt(1.W)
  val addr = UInt(32.W)
}


class DCache(val config: CacheConfig)
  extends Module {
  var io = IO(new Bundle {
    val valid = Input(Vec(2, Bool()))
    val addr = Input(Vec(2, UInt(32.W)))
    val asid = Input(UInt(8.W)) //进程ID
    val addr_ok = Output(Vec(2, Bool()))

    val wr = Input(Vec(2, Bool()))
    val size = Input(Vec(2, UInt(2.W)))
    val data_ok = Output(Vec(2, Bool()))
    val ex = Output(UInt(3.W)) // 例外
    val rdata = Output(Vec(2, UInt(32.W)))

    val wdata = Input(Vec(2, UInt(32.W)))

    val tlb = Flipped(new SearchPort(config.tlbnum)) // 读端口TLB

    val axi = new Bundle {
      val readAddr = Decoupled(new AXIAddr(32, 4))
      val readData = Flipped(Decoupled(new AXIReadData(32, 4)))
      val writeAddr = Decoupled(new AXIAddr(32, 4))
      val writeData = Decoupled(new AXIWriteData(32, 4))
      val writeResp = Flipped(Decoupled(new AXIWriteResponse(4)))
    }
    val debug_total_count = Output(UInt(32.W)) // cache总查询次数
    //        val debug_pure_hit_count = Output(UInt(32.W))
    val debug_hit_count = Output(UInt(32.W)) // cache命中数

  })
  val sIDLE :: sLOOKUP :: sREPLACE :: sREFILL :: sWaiting :: sEviction :: sEvictionWaiting :: sFetchHandshake :: sFetchRecv :: Nil = Enum(9)
  val state = RegInit(VecInit(Seq.fill(2)(0.U(4.W))))
  val is_hitWay          = Wire(Vec(2, Bool()))
  val write_failed: Bool = RegInit(false.B)
  //  val queue = Module(new Queue(new QueueItem, 3))
  //  val port_valid = RegInit(VecInit(Seq.fill(2)(true.B)))
  val storeBuffer        = Module(new StoreBuffer(config))

  //  val read_can_fire = Wire(Bool()) //允许读 且 有读请求
  //  read_can_fire := io.valid(0) && io.addr_ok(0) && !io.wr(0) || io.valid(1) && io.addr_ok(1) && !io.wr(1)
  val doWrite = RegInit(false.B)


  /**
   * cache的数据
   */
  val dataMem = List.fill(config.wayNum) {
    List.fill(config.bankNum) { // 4字节长就有4个
      Module(new dcache_data_bank(config))
    }
  }
  val tagvMem = List.fill(config.wayNum) {
    Module(new dcache_tagv(config))
  }
  val dirtyMem = List.fill(config.wayNum) {
    Module(new dcache_dirty(config))
  }


  val lruMem = Module(new LruMem(config.wayNumWidth, config.indexWidth)) // lru
  //  val victim = Module(new Victim(config)) // 写代理
  val invalidateQueue = Module(new InvalidateQueue(config))
  io.axi.writeAddr <> invalidateQueue.io.writeAddr
  io.axi.writeData <> invalidateQueue.io.writeData
  io.axi.writeResp <> invalidateQueue.io.writeResp

  val cache_hit_onehot = Wire(Vec(2, Vec(config.wayNum, Bool()))) // 命中的路
  val cache_hit_way = Wire(Vec(2, UInt(config.wayNumWidth.W)))

  //  val addr_r = RegInit(0.U(32.W)) //地址寄存器
  val addr_r_0 = Reg(UInt(32.W)) //地址寄存器
  val addr_r = Wire(Vec(2, UInt(32.W))) //地址寄存器
  addr_r(0) := Mux(io.valid(0) && io.addr_ok(0), io.addr(0), addr_r_0)

  addr_r(1) := storeBuffer.io.cache_write_addr
  val wdata_r = Wire(UInt(32.W))
  //  wdata_r(0) := queue.io.deq.bits.wdata
  wdata_r := storeBuffer.io.cache_write_wdata

  val port_r = Wire(UInt(1.W))
  port_r := 0.U

  val bData = new BankData(config)
  val tagvData = new TAGVData(config)
  val dirtyData = new DirtyData(config)

  val bDataWtBank = RegInit(VecInit(Seq.fill(2)((0.U((config.offsetWidth - 2).W)))))
  //  val AXI_readyReg = RegInit(VecInit(Seq.fill(2)((false.B))))

  //  val addrokReg = RegInit(false.B)
  val index = Wire(Vec(2, UInt(config.indexWidth.W)))
  val bankIndex = Wire(Vec(2, UInt((config.offsetWidth - 2).W)))
  val tag = Wire(Vec(2, UInt(config.tagWidth.W)))

  val waySelReg = RegInit(VecInit(Seq.fill(2)(0.U(config.wayNumWidth.W))))
  //  val eviction = Wire(Bool()) //出现驱逐

  val storeBuffer_reverse_mask = Wire(UInt(32.W))
  for (i <- 0 to 1) {
    is_hitWay(i) := cache_hit_onehot(i).asUInt().orR() // 判断是否命中cache
    state(i) := sIDLE
    index(i) := config.getIndex(addr_r(i))
  }
  tag(0) := config.getTag(addr_r_0)
  tag(1) := config.getTag(addr_r(1))
  bankIndex(0) := config.getBankIndex(addr_r_0)
  bankIndex(1) := config.getBankIndex(addr_r(1))

  /**
   * 与ram 交互
   */
  //初始化
  for (way <- 0 until config.wayNum) {
    tagvMem(way).io.clka := clock
    tagvMem(way).io.clkb := clock
    tagvMem(way).io.wea := tagvData.wEn(0)(way)
    tagvMem(way).io.web := tagvData.wEn(1)(way)
    tagvMem(way).io.ena := true.B
    tagvMem(way).io.enb := true.B
    tagvMem(way).io.dina := 0.U
    tagvMem(way).io.dinb := 0.U
    dirtyMem(way).io.clka := clock
    dirtyMem(way).io.clkb := clock
    dirtyMem(way).io.wea := false.B
    dirtyMem(way).io.web := false.B
    dirtyMem(way).io.ena := true.B
    dirtyMem(way).io.enb := true.B
    for (bank <- 0 until config.bankNum) {
      dataMem(way)(bank).io.clka := clock
      dataMem(way)(bank).io.clkb := clock
      dataMem(way)(bank).io.wea := Mux(bData.wEn(0)(way)(bank), "b1111".U(4.W), "b0000".U(4.W))
      dataMem(way)(bank).io.web := Mux(bData.wEn(1)(way)(bank), Mux(bank.U === bankIndex(1), storeBuffer.io.cache_write_wstrb, "b1111".U(4.W)), "b0000".U(4.W))
      dataMem(way)(bank).io.ena := true.B
      dataMem(way)(bank).io.enb := true.B
    }
  }

  for (way <- 0 until config.wayNum) {
    tagvMem(way).io.addra := config.getIndex(addr_r(0))
    tagvData.read(0)(way).tag := tagvMem(way).io.douta(config.tagWidth - 1, 0)
    tagvData.read(0)(way).valid := tagvMem(way).io.douta(config.tagWidth)


    dirtyMem(way).io.addra := config.getIndex(addr_r(0))
    dirtyData.read(0)(way) := dirtyMem(way).io.douta
    for (bank <- 0 until config.bankNum) {
      dataMem(way)(bank).io.addra := config.getIndex(addr_r(0))
      bData.read(0)(way)(bank) := dataMem(way)(bank).io.douta
    }
  }
  for (way <- 0 until config.wayNum) {
    tagvMem(way).io.addrb := config.getIndex(addr_r(1))
    tagvData.read(1)(way).tag := tagvMem(way).io.doutb(config.tagWidth - 1, 0)
    tagvData.read(1)(way).valid := tagvMem(way).io.doutb(config.tagWidth)
    dirtyMem(way).io.addrb := config.getIndex(addr_r(1))
    dirtyData.read(1)(way) := dirtyMem(way).io.doutb
    for (bank <- 0 until config.bankNum) {
      dataMem(way)(bank).io.addrb := config.getIndex(addr_r(1))
      bData.read(1)(way)(bank) := dataMem(way)(bank).io.doutb //现在
    }
  }

  dataMem.indices.foreach(way => {
    dataMem(way).indices.foreach(bank => {
      val m = dataMem(way)(bank)

      /**
       * 读口 控制
       */
      when(bData.wEn(0)(way)(bank)) {
        when(state(0) === sREFILL) {
          dirtyMem(way).io.wea := true.B
          dirtyMem(way).io.dina := false.B
          when(bank.U === bDataWtBank(0)) {
            when(storeBuffer.io.cache_query_mask =/= 0.U && bank.U === config.getBankIndex(storeBuffer.io.cache_query_addr)) {
              m.io.dina := io.axi.readData.bits.data & storeBuffer_reverse_mask | storeBuffer.io.cache_query_data & storeBuffer.io.cache_query_mask
            }.otherwise {
              m.io.dina := io.axi.readData.bits.data
            }
          }
        }
      }

      /**
       * 写 口控制
       */
      when(bData.wEn(1)(way)(bank)) {
        when(state(1) === sREFILL) {
          dirtyMem(way).io.web := true.B
          dirtyMem(way).io.dinb := true.B
          when(bank.U === bDataWtBank(1)) {
            when(bank.U === bankIndex(1)) {
              m.io.dinb := wdata_r
            }.otherwise {
              m.io.dinb := io.axi.readData.bits.data
            }
          }.otherwise {
            m.io.dinb := wdata_r
          }
        }.elsewhen(state(1) === sLOOKUP && is_hitWay(1) && cache_hit_way(1) === way.U) {
          // write hit
          dirtyMem(way).io.web := true.B
          dirtyMem(way).io.dinb := true.B //旧数据
          when(bank.U === bankIndex(1)) {
            m.io.dinb := wdata_r
          }
        }
      }
    })
  })


  for (way <- 0 until config.wayNum) {
    val m = tagvMem(way)
    when(tagvData.wEn(0)(way)) { //写使能
      m.io.dina := Cat(true.B, tag(0))
    }
    when(tagvData.wEn(1)(way)) { //写使能
      m.io.dinb := Cat(true.B, tag(1))
    }
  }

  for (way <- 0 until config.wayNum) {
    for (bank <- 0 until config.bankNum) {
      // 读端口的数据写使能
      bData.wEn(0)(way)(bank) := (state(0) === sREFILL && waySelReg(0) === way.U && bDataWtBank(0) === bank.U)
      //          (state===sREPLACE && victim.io.find && waySelReg === way.U) ||// 如果victim buffer里找到了
      //        (state === sVictimReplace && waySelReg === way.U && bankIndex ===bank.U)||  //读/写命中victim
      // 写端口数据写使能
      bData.wEn(1)(way)(bank) := (state(1) === sLOOKUP && is_hitWay(1) && cache_hit_way(1) === way.U && bankIndex(1) === bank.U) || // 写命中
        (state(1) === sREFILL && waySelReg(1) === way.U && (bDataWtBank(1) === bank.U || bank.U === bankIndex(1)))
    }
  }
  for (way <- 0 until config.wayNum) {
    tagvData.wEn(0)(way) := (state(0) === sREFILL && waySelReg(0) === way.U)
    //      (state === sVictimReplace  && waySelReg===way.U)
    tagvData.wEn(1)(way) := (state(1) === sREFILL && waySelReg(1) === way.U)

  }

  tagvData.write(0) := Cat(true.B, tag(0))
  tagvData.write(1) := Cat(true.B, tag(1))

  //  bData(0).addr := index(0)
  //  bData(1).addr := index(1)
  //  tagvData.addr := index
  for (worker <- 0 to 1) {
    cache_hit_way(worker) := OHToUInt(cache_hit_onehot(worker))
    // 判断是否命中cache
    cache_hit_onehot(worker).indices.foreach(i => {
      cache_hit_onehot(worker)(i) := tagvData.read(worker)(i).tag === tag(worker) && tagvData.read(worker)(i).valid
    })
  }


  /**
   * LRU 配置
   */
  lruMem.io.setAddr := Mux(state(0) === sLOOKUP, index(0), index(1))
  lruMem.io.visit := 0.U
  lruMem.io.visitValid := (state(0) === sLOOKUP && is_hitWay(0) || state(1) === sLOOKUP && is_hitWay(1))

  /**
   * store buffer配置
   */
  storeBuffer.io.cache_query_addr := addr_r(0)
  storeBuffer_reverse_mask := ~storeBuffer.io.cache_query_mask
  storeBuffer.io.cache_response := false.B

  /**
   * AXI
   */

  io.axi.readAddr.bits.id := Mux(state(0)===sFetchHandshake,0.U,1.U) // 未填
  val worker_id = Wire(Vec(2, UInt(4.W)))
  worker_id(0) := 1.U
  worker_id(1) := 3.U
  //  io.axi.readAddr.bits.len := 6.U
  io.axi.readAddr.bits.len := Mux(state(0)===sFetchHandshake,0.U,(config.bankNum - 1).U)
  io.axi.readAddr.bits.size := 2.U // 4B
  io.axi.readAddr.bits.addr := Mux(state(0)===sREPLACE || state(0)===sFetchHandshake, addr_r(0), addr_r(1))
  io.axi.readAddr.bits.cache := 0.U
  io.axi.readAddr.bits.lock := 0.U
  io.axi.readAddr.bits.prot := 0.U
  io.axi.readAddr.bits.burst := 2.U //突发模式2
  io.axi.readData.ready := state(0) === sREFILL || state(1) === sREFILL || state(0) === sFetchRecv
  io.axi.readAddr.valid := state(0) === sREPLACE || state(1) === sREPLACE || state(0) === sFetchHandshake

  //  /**
  //   * debug
  //   */
  val debug_total_count_r = RegInit(0.U(32.W))
  val debug_hit_count_r = RegInit(0.U(32.W))
  dontTouch(io.debug_total_count)
  dontTouch(io.debug_hit_count)
  //    val debug_pure_hit_count_r = RegInit(0.U(32.W))
  //    io.debug_pure_hit_count := debug_pure_hit_count_r
  io.debug_total_count := debug_total_count_r
  io.debug_hit_count := debug_hit_count_r

  when(state(0) === sLOOKUP) {
    debug_total_count_r := debug_total_count_r + 1.U
    when(is_hitWay(0)) {
      //        debug_pure_hit_count_r := debug_pure_hit_count_r + 1.U
      debug_hit_count_r := debug_hit_count_r + 1.U
    }
  }
  when(state(1) === sLOOKUP) {
    debug_total_count_r := debug_total_count_r + 1.U
    when(is_hitWay(1)) {
      //      debug_pure_hit_count_r := debug_pure_hit_count_r + 1.U
      debug_hit_count_r := debug_hit_count_r + 1.U
    }
  }

  /**
   * invalidateQueue
   */
  val evictionCounter = RegInit(VecInit(Seq.fill(2)(0.U(config.bankNumWidth.W))))
  invalidateQueue.io.uncache_req := false.B
  invalidateQueue.io.uncache_addr := get_physical_addr(Cat(io.tlb.pfn, io.addr(0)(11, 0)))
  invalidateQueue.io.uncache_data := io.wdata(0)
  invalidateQueue.io.uncache_size := io.size(0)

  for (i <- 0 to 1) {
    invalidateQueue.io.addr(i) := Cat(tagvData.read(i)(waySelReg(i)).tag, index(i), 0.U(config.offsetWidth.W))
    invalidateQueue.io.wdata(i) := bData.read(i)(waySelReg(i))(evictionCounter(i))
    invalidateQueue.io.req(i) := false.B
  }
  /**
   * TLB配置
   */

  io.ex :=  Cat(io.wr(0) && !io.tlb.ex(2) && !io.tlb.ex(1) && !io.tlb.ex(0),io.tlb.ex(1,0))
  //如果为写，并且找到了，且本页valid=1，d=0,最高位才会置1
  io.tlb.vpn2 := io.addr(0)(31, 13) //31,13
  io.tlb.odd_page := io.addr(0)(12) //12
  io.tlb.asid := io.asid

  /**
   * 处理请求
   */
  io.data_ok(1) := false.B
  io.data_ok(0) := false.B
  io.rdata(1) := 0.U
  io.rdata(0) := 0.U

  io.addr_ok(0) := !doWrite && storeBuffer.io.cpu_ok && state(0) === sIDLE //读写都准备完成
  io.addr_ok(1) := false.B

  storeBuffer.io.cpu_wdata := 0.U
  storeBuffer.io.cpu_req := false.B
  storeBuffer.io.cpu_addr := 0.U
  storeBuffer.io.cpu_size := 0.U
  storeBuffer.io.cpu_port := 0.U
  when(io.wr(0) && io.addr_ok(0) && io.valid(0)) {
    doWrite := true.B
    when(io.tlb.found) {
      when(io.tlb.c === 3.U) { //如果允许cache
        storeBuffer.io.cpu_req := true.B
        storeBuffer.io.cpu_size := io.size(0)
        storeBuffer.io.cpu_addr := get_physical_addr(Cat(io.tlb.pfn, io.addr(0)(11, 0)))
        storeBuffer.io.cpu_wdata := io.wdata(0)
        storeBuffer.io.cpu_port := 0.U
      }.otherwise{
        //若不允许cache，直接向invalidate queue发起请求
        invalidateQueue.io.uncache_req := true.B
      }
    }
  }
  when(invalidateQueue.io.uncahce_ok){
    io.data_ok(0) := true.B
    doWrite := false.B
  }
  when(storeBuffer.io.data_ok) {
    io.data_ok(0) := true.B
    doWrite := false.B
  }

  /**
   * Cache状态机
   */
  //  val lookupReadyGO = Bool()

  //  lookupReadyGO := state(0) === sLOOKUP && state(1)=/=sIDLE && state(1) =/= s

  //  victim.io.qaddr := Cat(addr_r(31,config.offsetWidth),0.U(config.offsetWidth.W))
  for (worker <- 0 to 1) {
    switch(state(worker)) {
      is(sIDLE) {
        when(worker.U === 0.U) { //读端口操作
          when(io.valid(0) && io.addr_ok(0) && !io.wr(0)) { // 如果有新的读请求
            when(io.tlb.found) { //TLB 命中
              when(io.tlb.c === 3.U) { //如果允许cache

                when(index(0) === index(1)) { // 如果冲突，考虑阻塞
                  when(state(1) === sIDLE) {
                    state(0) := sLOOKUP
                  }.otherwise {
                    state(0) := sWaiting // 阻塞状态
                  }
                }.otherwise {
                  state(0) := sLOOKUP
                }
              }.otherwise { //不允许cache
                state(0) := sFetchHandshake // uncache
              }
              addr_r_0 := get_physical_addr(Cat(io.tlb.pfn, io.addr(0)(11, 0)))
            }.otherwise {
              // TLB Miss
              state(0) := sIDLE
//              io.data_ok(0) := true.B
            }
          }
          }.elsewhen(worker.U === 1.U) {
            when(storeBuffer.io.cache_write_valid) {
              when(index(0) === index(1) && (io.valid(0) && io.addr_ok(0) && !io.wr(0) || state(0) === sWaiting)) {
                // 等待直到读端口进入空状态
                state(1) := sIDLE
              }.otherwise {
                state(1) := sLOOKUP
              }
            }.otherwise {
              state(1) := sIDLE
            }
          }.otherwise {
            state(worker) := sIDLE
          }
        }
        is(sLOOKUP) {
          when(is_hitWay(worker)) {
            lruMem.io.visit := cache_hit_way(worker) // lru记录命中
            state(worker) := sIDLE
            //        when(io.valid) {
            //          // 直接进入下一轮
            //          state := sLOOKUP
            //          addr_r := io.addr
            //          size_r := io.size
            //          wr_r := io.wr
            //          wdata_r := io.wdata
            //        }.otherwise {
            //        }
            when(worker.U === 0.U) {
              io.data_ok(port_r) := true.B
              addr_r_0 := 0.U
              io.rdata(port_r) := (bData.read(0)(cache_hit_way(0))(bankIndex(0)) & storeBuffer_reverse_mask) |
                (storeBuffer.io.cache_query_data & storeBuffer.io.cache_query_mask)
            }.elsewhen(worker.U === 1.U) {
              storeBuffer.io.cache_response := true.B
            }
          }.elsewhen(worker.U === 0.U && storeBuffer.io.cache_query_mask === "hffff_ffff".U) {
            // store buffer hit
            //          queue.io.deq.ready := true.B
            state(worker) := sIDLE
            addr_r_0 := 0.U
            io.data_ok(port_r) := true.B
            io.rdata(port_r) := storeBuffer.io.cache_query_data
          }.otherwise {
            //没命中,检查victim
            //        state := sCheckVictim
            //        victim.io.qvalid := true.B
            when(worker.U === 0.U && state(1) === sREPLACE || worker.U === 1.U && state(0) === sREPLACE || state(0)===sFetchHandshake) {
              state(worker) := sLOOKUP
            }.otherwise {
              io.axi.readAddr.bits.id := worker_id(worker)

              state(worker) := sREPLACE
              waySelReg(worker) := lruMem.io.waySel
            }
          }
        }
        is(sREPLACE) {
          //在此阶段完成驱逐
          bDataWtBank(worker) := bankIndex(worker)
          when(io.axi.readAddr.ready) {
            io.axi.readAddr.bits.id := worker_id(worker)
            when(dirtyData.read(worker)(waySelReg(worker)) === true.B) {
              state(worker) := sEvictionWaiting
              invalidateQueue.io.req(worker) := true.B
            }.otherwise {
              state(worker) := sREFILL
            }
          }.otherwise {
            state(worker) := sREPLACE
            io.axi.readAddr.bits.id := worker_id(worker)
          }
        }
        is(sEvictionWaiting) {
          when(invalidateQueue.io.data_start(worker)) {
            state(worker) := sEviction
            evictionCounter(worker) := 0.U
          }.otherwise {
            state(worker) := sEvictionWaiting
            invalidateQueue.io.req(worker) := true.B
          }
        }
        is(sEviction) {
          evictionCounter(worker) := evictionCounter(worker) + 1.U
          state(worker) := sEviction
          when(evictionCounter(worker) === (config.bankNum - 1).U) {
            state(worker) := sREFILL
          }
        }
        is(sREFILL) {
          // 取数据，重写TAGV
          state(worker) := sREFILL
          when(io.axi.readData.valid && io.axi.readData.bits.id === worker_id(worker)) {
            bDataWtBank(worker) := bDataWtBank(worker) + 1.U
            when(bDataWtBank(worker) === (bankIndex(worker) + 1.U)) {
              when(worker.U === 0.U) {
                io.rdata(port_r) := (bData.read(worker)(waySelReg(worker))(bankIndex(worker)) & storeBuffer_reverse_mask) |
                  (storeBuffer.io.cache_query_data & storeBuffer.io.cache_query_mask)
                io.data_ok(port_r) := true.B
              }
            }
            when(io.axi.readData.bits.last) {
              state(worker) := sIDLE
              when(worker.U === 0.U) {
                addr_r_0 := 0.U
                //              queue.io.deq.ready := true.B
              }.otherwise {
                storeBuffer.io.cache_response := true.B
              }
            }
          }
        }
        is(sWaiting) {
          when(index(0) === index(1)) {
            when(state(1) === sIDLE) {
              state(0) := sLOOKUP
            }.otherwise {
              state(0) := sWaiting // 阻塞状态
            }
          }.otherwise {
            state(0) := sLOOKUP
          }
        }
        is(sFetchHandshake){
          //uncache取
          when(io.axi.readAddr.valid && io.axi.readAddr.ready && io.axi.readAddr.bits.id===0.U){
            state(0) := sFetchRecv
          }.otherwise{
            state(0) := sFetchHandshake
          }
        }
        is(sFetchRecv){
          when(io.axi.readData.valid && io.axi.readData.ready && io.axi.readData.bits.id===0.U && io.axi.readData.bits.last){
            state(0) := sIDLE
            io.data_ok(0) := true.B
            io.rdata(0) := io.axi.readData.bits.data
          }.otherwise{
            state(0) := sFetchRecv
          }
        }
      }
    }
  def get_physical_addr(virtual_addr:UInt):UInt= {
    val converter = Module(new physical_addr)
    converter.io.virtual_addr := virtual_addr
    converter.io.physical_addr
  }
}
  object DCache extends App {
    new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
      () =>
        new DCache(new CacheConfig()))))
  }