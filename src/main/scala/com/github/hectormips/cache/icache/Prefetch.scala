package com.github.hectormips.cache.icache

import chisel3._
import chisel3.util._
import com.github.hectormips.amba.{AXIAddr, AXIReadData}
import com.github.hectormips.cache.setting.CacheConfig

import scala.collection.immutable.Nil


class Buffer(config: CacheConfig) extends Bundle {
  //
  val data = RegInit(VecInit(Seq.fill(config.prefetch_buffer_size)(VecInit(Seq.fill(config.bankNum)(0.U(32.W))))))
  val addr = RegInit(VecInit(Seq.fill(config.prefetch_buffer_size)(0.U(32.W))))
  val valid = RegInit(VecInit(Seq.fill(config.prefetch_buffer_size)(false.B)))
  val ptr = Counter(config.prefetch_buffer_size)
}

/**
 * N+1行预取
 */
class Prefetch(config: CacheConfig) extends Module {
  val io = IO(new Bundle {
    /**
     * 预取请求
     * 如果预取器正忙，会忽略请求
     */
    val req_valid = Input(Bool())
    val req_ready = Output(Bool())
    val req_addr = Input(UInt(32.W))

    /**
     * 预取查询
     */
    val query_valid = Input(Bool())
    val query_addr = Input(UInt(32.W))
    val query_finded = Output(Bool())
    val query_data = Output(Vec(config.bankNum, UInt(32.W)))
    val query_wait = Output(Bool()) //正在请求当前地址，可以直接等待结果
    /**
     * axi
     */
    val use_axi = Output(Bool())
    val readAddr = Decoupled(new AXIAddr(32, 4))
    val readData = Flipped(Decoupled(new AXIReadData(32, 4)))
  })
  val sIDLE::sCheck :: sHANDSHAKE :: sREFILL :: Nil = Enum(4)
  val state = RegInit(0.U(2.W))
  val buffer = new Buffer(config)
//  dontTouch(buffer.data)
  val addr_r = RegInit(0.U(32.W))
  val bankCounter = RegInit(0.U(config.bankNumWidth.W))

  /**
   * 预取阶段
   */
  io.use_axi := state === sHANDSHAKE || state === sREFILL && io.req_addr(31,config.offsetWidth) === addr_r(31,config.offsetWidth)
  io.req_ready := state === sIDLE
  val req_hit_onehot = Wire(Vec(config.prefetch_buffer_size, Bool()))
  val is_req_hit = Wire(Bool())
  for (i <- 0 until config.prefetch_buffer_size) {
    req_hit_onehot(i) := buffer.valid(i) && buffer.addr(i)(31, config.offsetWidth) === io.req_addr(31, config.offsetWidth)
  }
  is_req_hit := req_hit_onehot.asUInt().orR() //为0表示没有命中
  when(state === sIDLE && io.req_valid && io.req_ready && !is_req_hit) {
    addr_r := io.req_addr
    state := sHANDSHAKE
    buffer.valid(buffer.ptr.value) := false.B
  }

  when(state === sHANDSHAKE) {
    when(io.readAddr.valid && io.readAddr.ready) {
      state := sREFILL
      bankCounter := 0.U
    }.otherwise {
      state := sHANDSHAKE
    }
  }
  when(state === sREFILL) {
    when(io.readData.valid && io.readData.ready && io.readData.bits.id === 1.U) {
      buffer.data(buffer.ptr.value)(bankCounter) := io.readData.bits.data
      bankCounter := bankCounter + 1.U
      when(io.readData.bits.last) {
        state := sIDLE
        buffer.ptr.inc()
        buffer.valid(buffer.ptr.value) := true.B
        buffer.addr(buffer.ptr.value) := addr_r
      }.otherwise {
        state := sREFILL
      }
    }.otherwise {
      state := sREFILL
    }
  }

  /**
   * 查询
   */
  val query_onehot = Wire(Vec(config.prefetch_buffer_size, Bool()))
  val query_hit = Wire(UInt(log2Ceil(config.prefetch_buffer_size).W))
  val query_wait_r = RegInit(false.B)
  query_wait_r := addr_r === io.query_addr && state === sREFILL
  io.query_wait := query_wait_r
  query_hit := OHToUInt(query_onehot)
  for (i <- 0 until config.prefetch_buffer_size) {
    query_onehot(i) := buffer.valid(i) && buffer.addr(i)(31, config.offsetWidth) === io.query_addr(31, config.offsetWidth)
  }
  io.query_finded := query_onehot.asUInt().orR() && io.query_valid
  dontTouch(io.query_finded)
  io.query_data := buffer.data(query_hit)


  /**
   * axi访问设置
   */
  dontTouch(io.readAddr.bits.id)
  //  dontTouch(io.readData.bits.data)
  //  dontTouch(io.readData.valid)
  //  dontTouch(io.readData.ready)
  io.readAddr.bits.id := 1.U
  io.readAddr.bits.len := (config.bankNum - 1).U
  io.readAddr.bits.size := 2.U // 4B
  io.readAddr.bits.addr := addr_r
  io.readAddr.bits.cache := 0.U
  io.readAddr.bits.lock := 0.U
  io.readAddr.bits.prot := 0.U
  io.readAddr.bits.burst := 1.U //突发模式2
  io.readAddr.valid := state === sHANDSHAKE
  io.readData.ready := state === sREFILL //ready最多持续一拍
}
