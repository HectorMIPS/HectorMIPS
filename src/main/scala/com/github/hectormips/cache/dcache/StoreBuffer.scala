package com.github.hectormips.cache.dcache
import chisel3._
import chisel3.util._
import com.github.hectormips.cache.utils.Wstrb

import scala.collection.immutable.Nil


class StoreBuffer(length:Int) extends Module{
  val io = IO(new Bundle{
    // 与请求端交互(由cache代理，组合逻辑)
    val cpu_req = Input(Bool())
    val cpu_size = Input(UInt(3.W))
    val cpu_addr = Input(UInt(32.W))
    val cpu_wdata = Input(UInt(32.W))
    val cpu_ok   = Output(Bool())
    // 与cache交互
    // 向端口插入数据
    val cache_write_ready = Input(Bool())
    val cache_write_valid  = Output(Bool())
    val cache_write_size = Output(UInt(3.W))
    val cache_write_addr = Output(UInt(32.W))
    val cache_write_wdata = Output(UInt(32.W))
    val cache_response   = Input(Bool())
    // cache的请求
    val cache_query_addr = Input(UInt(32.W))
    val cache_query_data = Output(UInt(32.W)) //这里返回的是移位后的数据
    val cache_query_mask = Output(UInt(32.W)) //如果没找到，mask为0
  })
  val hit_queue_onehot = Wire(Vec(length,Bool()))
  val is_hit_queue = hit_queue_onehot.asUInt() =/= 0.U
  val hit_queue_index = Wire(UInt(log2Ceil(length).W))
  val buffer = new Buffer(length+1)
  val wstrb = new Wstrb
  wstrb.io.size := buffer.data(hit_queue_index).size
  wstrb.io.offset := buffer.data(hit_queue_index).addr(1,0)

  /**
   * 处理cache请求
   */
  for(i <- 0 until length){
    hit_queue_onehot(i) := buffer.data(i).valid &&  buffer.data(i).addr === io.cache_query_addr
  }
  hit_queue_index := hit_queue_onehot.asUInt()
  when(is_hit_queue){
    io.cache_query_data := buffer.data(hit_queue_index).wdata << buffer.data(hit_queue_index).addr(1,0)
    io.cache_query_mask := Cat(Mux(wstrb.io.mask(0),"hff".U(8.W),0.U(8.W)),
      Mux(wstrb.io.mask(1),"hff".U(8.W),0.U(8.W)),
      Mux(wstrb.io.mask(2),"hff".U(8.W),0.U(8.W)),
      Mux(wstrb.io.mask(3),"hff".U(8.W),0.U(8.W)))
  }.otherwise{
    io.cache_query_data := 0.U
    io.cache_query_mask := 0.U
  }

  /**
   * 处理输入的请求
   */
  io.cpu_ok := !buffer.full()
  when(io.cpu_req && io.cpu_ok){
    buffer.enq_data().size := io.cpu_size
    buffer.enq_data().addr := io.cpu_addr
    buffer.enq_data().wdata := io.cpu_wdata
    buffer.enq()
  }

  /**
   * 处理向cache写入的数据
   */
  val sWork::sWait::Nil = Enum(2)
  val state = RegInit(0.U(1.W))

  when(state === sWork && !buffer.empty() && io.cache_write_ready){
    io.cache_write_valid := true.B
  }
  when(state === sWork && io.cache_write_valid && io.cache_write_ready){
    io.cache_write_size := buffer.deq_data().size
    io.cache_write_addr := buffer.deq_data().addr
    io.cache_write_wdata := buffer.deq_data().wdata
    state := sWait
  }
  when(io.cache_response){
    state := sWork
    buffer.deq()
  }


}
class bufferItem extends Bundle{
  val valid = Bool()
  val addr  = UInt(32.W)
  val wdata = UInt(32.W)
  val size  = UInt(3.W)
}
class Buffer(length:Int) extends  Bundle{
  val data    = Mem(length+1,new bufferItem)
  val enq_ptr = RegInit(0.U(log2Ceil(length+1).W))
  val deq_ptr  = RegInit(0.U(log2Ceil(length+1).W))
  def empty():Bool={
    enq_ptr === deq_ptr
  }
  def full():Bool={
    enq_ptr === deq_ptr - 1.U
  }
  def count():UInt={
    enq_ptr - deq_ptr
  }
  def enq():Unit={
    enq_ptr := enq_ptr + 1.U
  }
  def deq():Unit={
    deq_ptr := deq_ptr + 1.U
  }
  def enq_data():bufferItem={
    data(enq_ptr)
  }
  def deq_data():bufferItem={
    data(enq_ptr)
  }
}