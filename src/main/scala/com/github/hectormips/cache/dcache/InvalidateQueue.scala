package com.github.hectormips.cache.dcache

import chisel3._
import chisel3.util._
import com.github.hectormips.amba.{AXIAddr, AXIWriteData, AXIWriteResponse}
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.cache.uncache.Uncache
import com.github.hectormips.cache.utils.Wstrb

//class QueueItem(config:CacheConfig) extends Bundle{
//  val addr   = UInt(32.W)
//  val data   = Vec(config.bankNum,UInt(32.W))
//  val port   =
//}

//class Item(bankNum:Int) extends Bundle{
//  val data = Vec(bankNum,UInt(32.W))
//}



class InvalidateQueue(config:CacheConfig) extends Module {
  /**
   * 暂时使用的接口
   */

  val io = IO(new Bundle{
    val wdata = Input(Vec(2,UInt(32.W)))
    val addr  = Input(Vec(2,UInt(32.W)))
    val req   = Input(Vec(2,Bool()))
    val addr_ok = Output(Vec(2,Bool()))
    val data_start    = Output(Vec(2,Bool()))
    val writeAddr  =  Decoupled(new AXIAddr(32,4))
    val writeData  = Decoupled(new AXIWriteData(32,4))
    val writeResp =  Flipped(Decoupled( new AXIWriteResponse(4)))

    val uncache_req  = Input(Bool())
    val uncache_data = Input(UInt(32.W))
    val uncache_addr = Input(UInt(32.W))
    val uncache_size = Input(UInt(2.W))
    val uncahce_ok   = Output(Bool())
  })
  val sIDLE::sHANDSHAKE::sTrans::sWaiting::sUncacheTemporary::Nil = Enum(5)
  val state =  RegInit(VecInit(Seq.fill(2)(0.U(2.W))))
  val mem = RegInit(VecInit(Seq.fill(config.bankNum)(0.U(32.W))))
  val counter = RegInit(VecInit(Seq.fill(2)(0.U(log2Ceil(config.bankNum).W))))

  val addr_r = RegInit(VecInit(Seq.fill(2)(0.U(32.W))))
  val worker_id = Wire(Vec(2,UInt(4.W)))

  val uncache_data = RegInit(0.U(32.W))
  val uncache_addr = RegInit(0.U(32.W))
  val uncache_state = RegInit(0.U(3.W))
  val uncache_busy = RegInit(false.B)
  val uncache_wstrb = RegInit(0.U(4.W))
  val wstrb = Module(new Wstrb)
  wstrb.io.size := io.uncache_size
  wstrb.io.offset := io.uncache_addr(1,0)

  /**
   * 处理uncahce write 请求
   */
  io.uncahce_ok := false.B

  when(io.uncache_req){
    uncache_data := io.uncache_data
    uncache_addr := io.uncache_addr
    uncache_state := sUncacheTemporary
    uncache_wstrb := wstrb.io.mask
    uncache_busy := true.B
  }
  when(uncache_state === sUncacheTemporary){
    when(state(0)=/=sHANDSHAKE && state(1)=/=sHANDSHAKE){
      uncache_state := sHANDSHAKE
      uncache_busy := false.B
    }.otherwise{
      uncache_state := sUncacheTemporary
    }
  }
  when(uncache_state === sHANDSHAKE){
    when(io.writeAddr.valid && io.writeAddr.ready && io.writeAddr.bits.id === 0.U) {
      uncache_state := sTrans
    }.otherwise{
      uncache_state := sHANDSHAKE
    }
  }
  when(uncache_state === sTrans){
    when(io.writeData.valid && io.writeData.ready && io.writeData.bits.wid === 0.U) {
      uncache_state := sWaiting

    }.otherwise{
      uncache_state := sTrans
    }
  }
  when(uncache_state===sWaiting){
    when(io.writeResp.valid &&io.writeResp.ready && io.writeResp.bits.id === 0.U) {
      uncache_state := sIDLE
      io.uncahce_ok := true.B
    }.otherwise{
      uncache_state := sWaiting
    }
  }

  worker_id(0) := 1.U
  worker_id(1) := 3.U


  // 半双工；保证不会同时发送两个请求
  io.addr_ok(0) := state(0) === sIDLE && (state(1) =/=sHANDSHAKE|| state(1)=/=sTrans) && !uncache_busy
  io.addr_ok(1) := state(1) === sIDLE && (state(0) =/=sHANDSHAKE|| state(0)=/=sTrans) && !uncache_busy
  for(i<- 0 to 1) {
    switch(state(i)) {
      is(sIDLE) {
        when(io.req(i) && io.addr_ok(i)) {
          state(i) := sHANDSHAKE
          addr_r(i) := io.addr(i)
          io.writeAddr.bits.id := worker_id(i)
          io.writeAddr.bits.addr := io.addr(i)
        }
      }
      is(sHANDSHAKE) {
        when(io.writeAddr.ready && io.writeAddr.bits.id === worker_id(i)) {
          counter(i) := 0.U
          state(i) := sTrans
        }.otherwise {
          state(i) := sHANDSHAKE
          io.writeAddr.bits.id := worker_id(i)
          io.writeAddr.bits.addr := addr_r(i)
        }
      }
      is(sTrans) {
        when(io.writeData.ready && io.writeData.bits.wid === worker_id(i)) {
          counter(i) := counter(i) + 1.U
          when(counter(i) === (config.bankNum - 1).U) {
            state(i) := sWaiting
          }
        }.otherwise {
          state(i) := sTrans
//          io.writeData.bits.wid := worker_id(i)
        }
      }
      is(sWaiting) {
        when(io.writeResp.valid && io.writeResp.bits.id === worker_id(i)) {
          state(i) := sIDLE
        }.otherwise {
          state(i) := sWaiting
        }
      }
    }
  }
  for(i<- 0 to 1) {
    io.data_start(i) := state(i) === sHANDSHAKE && io.writeAddr.ready && io.writeAddr.bits.id === io.writeAddr.bits.id
  }

//  io.writeAddr.bits.addr := Mux(state(i)===sIDLE,io.addr,addr_r)

  io.writeAddr.bits.id := Mux(state(0)===sHANDSHAKE,worker_id(0),Mux(state(1)===sHANDSHAKE,worker_id(1),0.U))
  io.writeAddr.bits.addr := Mux(state(0)===sHANDSHAKE,addr_r(0),Mux(state(1)===sHANDSHAKE,addr_r(1),Mux(uncache_state===sHANDSHAKE,uncache_addr,0.U)))
  io.writeAddr.bits.size := 2.U
  io.writeAddr.bits.len := Mux(uncache_state===sHANDSHAKE || uncache_state===sTrans,0.U,(config.bankNum -1).U)
  io.writeAddr.bits.cache := 0.U
  io.writeAddr.bits.lock := 0.U
  io.writeAddr.bits.prot := 0.U
  io.writeAddr.bits.burst := 2.U

  io.writeAddr.valid := state(0) === sHANDSHAKE|| state(1) === sHANDSHAKE || uncache_state===sHANDSHAKE

  io.writeData.bits.wid := Mux(state(0)===sTrans,worker_id(0),Mux(state(1)===sTrans,worker_id(1),0.U))
  dontTouch(io.writeData.bits.wid)
  io.writeData.bits.strb := Mux(uncache_state===sHANDSHAKE,uncache_wstrb,"b1111".U)
  io.writeData.bits.last := Mux(uncache_state===sTrans,1.U,counter(0) === (config.bankNum - 1).U || counter(1) === (config.bankNum - 1).U)
  io.writeData.bits.data := Mux(uncache_state===sTrans,uncache_data,Mux(state(0)===sTrans,io.wdata(0),Mux(state(1)===sTrans,io.wdata(1),0.U)))
  io.writeData.valid := state(0) === sTrans || state(1) === sTrans || uncache_state===sTrans

  io.writeResp.ready := state(0) === sWaiting || state(1) === sWaiting || uncache_state === sWaiting
}
