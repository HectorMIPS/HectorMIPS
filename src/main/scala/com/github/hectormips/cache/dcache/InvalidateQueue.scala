package com.github.hectormips.cache.dcache

import chisel3._
import chisel3.util._
import com.github.hectormips.amba.{AXIAddr, AXIWriteData, AXIWriteResponse}
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.cache.uncache.Uncache

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
    val wdata = Input(UInt(32.W))
    val addr  = Input(UInt(32.W))
    val req   = Input(Bool())
    val addr_ok = Output(Bool())
    val data_start    = Output(Bool())
    val writeAddr  =  Decoupled(new AXIAddr(32,4))
    val writeData  = Decoupled(new AXIWriteData(32,4))
    val writeResp =  Flipped(Decoupled( new AXIWriteResponse(4)))
  })
  val sIDLE::sHANDSHAKE::sTrans::sWaiting::Nil = Enum(4)
  val state = RegInit(0.U(2.W))
  val mem = RegInit(VecInit(Seq.fill(config.bankNum)(0.U(32.W))))
  val counter = RegInit(0.U(log2Ceil(config.bankNum).W))
  val addr_r = Reg(UInt(32.W))
  io.addr_ok := state === sIDLE
  switch(state){
    is(sIDLE){
      when(io.req && io.addr_ok){
        state := sHANDSHAKE
        addr_r := io.addr
      }
    }
    is(sHANDSHAKE){
      when(io.writeAddr.ready && io.writeAddr.bits.id === 1.U){
        counter := 0.U
        state := sTrans
      }.otherwise{
        state := sHANDSHAKE
      }
    }
    is(sTrans){
      when(io.writeData.ready && io.writeAddr.bits.id ===1.U){
        counter := counter + 1.U
        when(counter === (config.bankNum - 1).U){
          state := sWaiting
        }
      }.otherwise{
        state := sTrans
      }
    }
    is(sWaiting){
      when(io.writeResp.valid && io.writeResp.bits.id === io.writeAddr.bits.id){
        state := sIDLE
      }.otherwise{
        state := sWaiting
      }
    }
  }
  io.data_start := state === sHANDSHAKE && io.writeAddr.ready && io.writeAddr.bits.id ===io.writeAddr.bits.id
  io.writeAddr.bits.id := 1.U
  io.writeAddr.bits.size := 2.U
  io.writeAddr.bits.len := (config.bankNum -1).U
  io.writeAddr.bits.cache := 0.U
  io.writeAddr.bits.lock := 0.U
  io.writeAddr.bits.prot := 0.U
  io.writeAddr.bits.burst := 2.U
  io.writeAddr.bits.addr := Mux(state===sIDLE,io.addr,addr_r)
  io.writeAddr.valid := state === sHANDSHAKE && !io.writeAddr.ready

  io.writeData.bits.wid := 1.U
  io.writeData.bits.strb := "b1111".U
  io.writeData.bits.last := counter === (config.bankNum - 1).U
  io.writeData.bits.data := io.wdata
  io.writeData.valid := state === sTrans

  io.writeResp.ready := state === sWaiting
}
