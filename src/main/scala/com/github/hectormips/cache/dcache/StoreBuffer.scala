package com.github.hectormips.cache.dcache

import chisel3._
import chisel3.util._
import com.github.hectormips.cache.setting.CacheConfig
import com.github.hectormips.cache.utils.Wstrb

import scala.collection.immutable.Nil
import scala.collection.script.Include


class StoreBuffer(config: CacheConfig) extends Module {
  val io = IO(new Bundle {
    // 与请求端交互(由cache代理，组合逻辑)
    val cpu_req = Input(Bool())
    val cpu_size = Input(UInt(3.W))
    val cpu_addr = Input(UInt(32.W))
    val cpu_wdata = Input(UInt(32.W))
    val cpu_port = Input(UInt(1.W))
    val cpu_ok = Output(Bool())
    val data_ok = Output(Bool())
    val data_ok_port = Output(UInt(1.W))
    // 与cache交互
    // 向端口插入数据
    //    val cache_write_ready = Input(Bool())
    val cache_write_valid = Output(Bool())
    val cache_write_wstrb = Output(UInt(4.W))
    val cache_write_addr = Output(UInt(32.W))
    val cache_write_wdata = Output(UInt(32.W))
    val cache_response = Input(Bool())

    // cache的请求
    val cache_query_addr = Input(UInt(32.W))
    val cache_query_data = Output(UInt(32.W)) //这里返回的是移位后的数据
    val cache_query_mask = Output(UInt(32.W)) //如果没找到，mask为0
  })
  val cache_hit_queue_onehot = Wire(Vec(config.storeBufferLength + 1, Bool()))
  val cache_is_hit_queue = Wire(Bool())
  val cache_hit_queue_index = Wire(UInt(log2Ceil(config.storeBufferLength + 1).W))
  cache_is_hit_queue := cache_hit_queue_onehot.asUInt() =/= 0.U
  val cpu_hit_queue_onehot = Wire(Vec(config.storeBufferLength + 1, Bool()))
  val cpu_is_hit_queue = Wire(Bool())
  val cpu_hit_queue_index = Wire(UInt(log2Ceil(config.storeBufferLength + 1).W))
  cpu_is_hit_queue := cpu_hit_queue_onehot.asUInt() =/= 0.U
  cpu_hit_queue_index := OHToUInt(cpu_hit_queue_onehot)
  val buffer = new Buffer(config.storeBufferLength + 1)
  val wstrb = Module(new Wstrb)
  val full_mask = Wire(UInt(32.W))
  val reverse_full_mask = Wire(UInt(32.W))
  val tmp_size = Reg(UInt(3.W))
  val tmp_addr = Reg(UInt(32.W))
  val tmp_wdata = Reg(UInt(32.W))
  val tmp_valid = RegInit(false.B)
  val tmp_port = RegInit(0.U)
  wstrb.io.size := tmp_size
  wstrb.io.offset := tmp_addr(1, 0)
  /**
   * 处理cache请求
   */
  val cache_query_addr_r = RegInit(0.U(32.W))
  cache_query_addr_r := io.cache_query_addr
  for (i <- 0 to config.storeBufferLength) { //0~7都可以填充 但是最多只能放7个
    cache_hit_queue_onehot(i) := buffer.data(i).valid && buffer.data(i).addr(31, 2) === cache_query_addr_r(31, 2)
    cpu_hit_queue_onehot(i) := buffer.data(i).valid && buffer.data(i).addr(31, 2) === tmp_addr(31, 2)
  }
  cache_hit_queue_index := OHToUInt(cache_hit_queue_onehot)
  when(cache_is_hit_queue) {
    io.cache_query_data := buffer.data(cache_hit_queue_index).wdata
    io.cache_query_mask := Cat(Mux(buffer.data(cache_hit_queue_index).wstrb(3), "hff".U(8.W), 0.U(8.W)),
      Mux(buffer.data(cache_hit_queue_index).wstrb(2), "hff".U(8.W), 0.U(8.W)),
      Mux(buffer.data(cache_hit_queue_index).wstrb(1), "hff".U(8.W), 0.U(8.W)),
      Mux(buffer.data(cache_hit_queue_index).wstrb(0), "hff".U(8.W), 0.U(8.W)))
  }.otherwise {
    io.cache_query_data := 0.U
    io.cache_query_mask := 0.U
  }

  /**
   * 处理输入的请求
   */

  io.cpu_ok := !buffer.full()
  io.data_ok_port := 0.U
  when(io.cpu_req && io.cpu_ok) {
    tmp_size := io.cpu_size
    tmp_addr := io.cpu_addr
    tmp_wdata := io.cpu_wdata
    tmp_port := io.cpu_port
    tmp_valid := true.B
  }
  io.data_ok := false.B
  when(tmp_valid) {
    when(cpu_is_hit_queue) {
      // 合并同类项
      buffer.data(cpu_hit_queue_index).wstrb := wstrb.io.mask | buffer.data(cpu_hit_queue_index).wstrb
      buffer.data(cpu_hit_queue_index).wdata := buffer.data(cpu_hit_queue_index).wdata & reverse_full_mask |
        tmp_wdata & full_mask
    }.otherwise {
      buffer.enq_data().wstrb := wstrb.io.mask
      buffer.enq_data().addr := tmp_addr
      buffer.enq_data().wdata := tmp_wdata
      buffer.enq_data().valid := true.B
      buffer.enq()
    }
    //    when(io.cpu_req && io.cpu_ok){
    //      tmp_size  := io.cpu_size
    //      tmp_addr  := io.cpu_addr
    //      tmp_wdata := io.cpu_wdata
    //      tmp_port := io.cpu_port
    //      tmp_valid := true.B
    //    }.otherwise{
    tmp_valid := false.B
    //    }
    io.data_ok := true.B
    io.data_ok_port := tmp_port
  }

  /**
   * 处理向cache写入的数据
   */
  //  val state = RegInit(0.U(1.W))


  full_mask := Cat(Mux(wstrb.io.mask(3), "hff".U(8.W), 0.U(8.W)),
    Mux(wstrb.io.mask(2), "hff".U(8.W), 0.U(8.W)),
    Mux(wstrb.io.mask(1), "hff".U(8.W), 0.U(8.W)),
    Mux(wstrb.io.mask(0), "hff".U(8.W), 0.U(8.W)))
  reverse_full_mask := ~full_mask
  when(!buffer.empty()) {
    io.cache_write_valid := true.B
    io.cache_write_wstrb := buffer.deq_data().wstrb
    io.cache_write_addr := buffer.deq_data().addr
    io.cache_write_wdata := buffer.deq_data().wdata
  }.otherwise {
    io.cache_write_valid := false.B
    io.cache_write_wstrb := "b0000".U
    io.cache_write_addr := 0.U
    io.cache_write_wdata := 0.U
  }
  when(io.cache_response) {
    when(tmp_valid && tmp_addr(31, 2) === buffer.deq_data().addr(31, 2)) {
      //多沿一个事务
      buffer.deq_data().valid := true.B
    }.otherwise {
      buffer.deq()
      buffer.deq_data().valid := false.B
    }
  }


}

class bufferItem() extends Bundle {
  val valid = Bool()
  val addr = UInt(32.W)
  val wdata = UInt(32.W)
  val wstrb = UInt(4.W)
//  val asid = UInt(asidWidth.W)
  //  val port  = UInt(1.W)
}

class Buffer(length: Int) extends Bundle {
  val data = RegInit(VecInit(Seq.fill(length + 1)({
    val bundle = Wire(new bufferItem)
    bundle.valid := 0.U
    bundle.addr := 0.U
    bundle.wstrb := 0.U
    bundle.wdata := 0.U
//    bundle.asid := 0.U
    //    bundle.port := 0.U
    bundle
  })))
  val enq_ptr = RegInit(0.U(log2Ceil(length).W))
  val deq_ptr = RegInit(0.U(log2Ceil(length).W))

  def empty(): Bool = {
    enq_ptr === deq_ptr
  }

  def full(): Bool = {
    enq_ptr === deq_ptr - 2.U
  }


  def count(): UInt = {
    enq_ptr - deq_ptr
  }

  def enq(): Unit = {
    when(enq_ptr === length.U) {
      enq_ptr := 0.U
    }.otherwise {
      enq_ptr := enq_ptr + 1.U
    }
  }

  def deq(): Unit = {
    when(deq_ptr === length.U) {
      deq_ptr := 0.U
    }.otherwise {
      deq_ptr := deq_ptr + 1.U
    }
  }

  def enq_data(): bufferItem = {
    data(enq_ptr)
  }

  def deq_data(): bufferItem = {
    data(deq_ptr)
  }
}