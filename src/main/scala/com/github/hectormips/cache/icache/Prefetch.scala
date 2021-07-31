package com.github.hectormips.cache.icache

import chisel3._
import chisel3.util._
import com.github.hectormips.amba.{AXIAddr, AXIReadData}
import com.github.hectormips.cache.setting.CacheConfig

import scala.collection.immutable.Nil


class BufferItem(bankNum:Int) extends Bundle{
  val valid = Bool()
  val addr  = UInt(32.W)
  val data  = Vec(bankNum,UInt(32.W))
}
class Buffer(config:CacheConfig)extends Bundle{
  val data   = RegInit(VecInit(Seq.fill(config.prefetch_buffer_size)({
    val bundle = Wire(new BufferItem(bankNum = config.bankNum))
    bundle.valid := 0.U
    bundle.addr := 0.U
    for(i <- 0 until config.bankNum){
      bundle.data(i) := 0.U
    }
    bundle
  })))
}
/**
 * N+1行预取
 */
class Prefetch(config:CacheConfig) extends Module{
  val io = IO(new Bundle{
    /**
     * 预取请求
     * 如果预取器正忙，会忽略请求
     */
    val req_valid = Input(Bool())
    val req_addr =Input(UInt(32.W))

    /**
     *  预取查询
     */
    val query_valid  = Input(Bool())
    val query_addr   = Input(UInt(32.W))
    val query_finded = Output(Bool())
    val query_data   = Output(Vec(config.bankNum,UInt(32.W)))

    /**
     * axi
     */
    val readAddr  =  Decoupled(new AXIAddr(32,4))
    val readData  = Decoupled(new AXIReadData(32,4))
  })
  val sIDLE::sHANDSHAKE::sREFILL::Nil = Enum(3)
  val state = RegInit(0.U(2.W))
  val buffer = new Buffer(config)
  val addr_r = RegInit(0.U(32.W))

  when(io.req_valid && state === sIDLE){
    addr_r := io.req_addr
    state := sHANDSHAKE
  }




  /**
   * axi访问设置
   */
  io.readAddr.bits.id := 5.U
  io.readAddr.bits.len := (config.bankNum - 1).U
  io.readAddr.bits.size := 2.U // 4B
  io.readAddr.bits.addr := Cat(addr_r(31,config.offsetWidth),0.U(config.offsetWidth))
  io.readAddr.bits.cache := 0.U
  io.readAddr.bits.lock := 0.U
  io.readAddr.bits.prot := 0.U
  io.readAddr.bits.burst := 1.U //突发模式2
  io.readAddr.valid := state === sHANDSHAKE
  io.readData.ready := true.B  //ready最多持续一拍
}
