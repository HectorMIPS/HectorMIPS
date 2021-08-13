package com.github.hectormips.cache.access_judge

import com.github.hectormips.{AXIIOWithoutWid, SRamLikeDataIO, SRamLikeInstIO}
import chisel3._
import chisel3.util._
import com.github.hectormips.cache.uncache.UncacheInst




class QueueItem extends Bundle{
  val should_map   = Bool()
  val should_cache = Bool()
  val addr         = UInt(32.W)
  val wdata        = UInt(32.W)
  val size         = UInt(3.W)
  val wr           = Bool()
  val jump         = Vec(2,Bool())
  val target       = Vec(2,UInt(32.W))
  val asid         = UInt(8.W)
}

/**
 * 访存判断逻辑
 *
 * 分为3路
 * 一路作uncache 以后再写
 * 一路访问指令
 * 一路访问cached数据
 */
class MemAccessJudge(cache_all_inst:Bool=false.B) extends Module{
  var io = IO(new Bundle{
    //输入
    val inst = Flipped(new SRamLikeInstIO())
    val data = Vec(2,Flipped(new SRamLikeDataIO()))

    //输出
    val mapped_inst   = new SRamLikeInstIO()
    val unmapped_inst = new SRamLikeInstIO()
    val unmapped_data = Vec(2,new SRamLikeDataIO()) //单口AXI-IO
    val mapped_data   = Vec(2,new SRamLikeDataIO())
    val data_addr_is_mapped = Output(Bool())
    val inst_addr_is_mapped = Output(Bool())
    val data_unmap_should_cache = Output(Bool())
    val inst_unmap_should_cache = Output(Bool())
//    val cache_kseg0 = Input(Bool())
  })
  val data_physical_addr = Wire(Vec(2,UInt(32.W)))
//  val inst_physical_addr = Wire(UInt(32.W))




//  def addr_mapping(vaddr: UInt): UInt = {
//    val physical_addr: UInt = Wire(UInt(32.W))
//    physical_addr := Mux((vaddr >= 0x80000000L.U && vaddr <= 0x9fffffffL.U) ||
//      (vaddr >= 0xa0000000L.U && vaddr <= 0xbfffffffL.U),
//      vaddr & 0x1fffffff.U, vaddr)
//    physical_addr
//  }


  val should_map_data_r = RegInit((false.B))
  val should_map_data_c = Wire(Bool())
  val should_map_data   = Wire(Bool())

  val should_cache_data_r = RegInit((false.B))
  val should_cache_data_c = Wire(Bool())
  val should_cache_data   = Wire(Bool())

  /**
   * cache和map 属性确认
   */
  should_map_data := Mux(io.data(0).req,should_map_data_c,should_map_data_r)
  when(io.data(0).req){
    should_map_data_r := should_map_data_c
  }
  should_map_data_c := should_map(io.data(0).addr)

  should_cache_data := Mux(io.data(0).req,should_cache_data_c,should_cache_data_r)
  when(io.data(0).req){
    should_cache_data_r := should_cache_data_c
  }
  should_cache_data_c := should_cache_d(io.data(0).addr)

  for(i<- 0 until 2) {
    /**
     * 虚地址转换
     */
    when(io.data(i).addr >= "h8000_0000".U && io.data(i).addr <= "hbfff_ffff".U){
      data_physical_addr(i) := io.data(i).addr & "h1fff_ffff".U
    }.otherwise{
      data_physical_addr(i) := io.data(i).addr
    }
  }

//  val should_cache_inst_r = RegInit(false.B)
  val should_map_inst_c = Wire(Bool())
  val should_cache_inst_c = Wire(Bool())
  //  val should_cache_inst   = Wire(Bool())

  val queue = Module(new Queue(new QueueItem, 3))
  val handshake = RegInit(false.B)
  val physical_inst_addr = Wire(UInt(32.W))
  val physical_queue_inst_addr = Wire(UInt(32.W))
  val wait_for_dequeue = RegInit(false.B)



  for(i <- 0 until 2) {
    io.mapped_data(i).req := should_cache_data & io.data(i).req
    io.unmapped_data(i).req := !should_cache_data & io.data(i).req
    io.mapped_data(i).size := io.data(i).size
    io.mapped_data(i).addr := io.data(i).addr
    io.mapped_data(i).wr := io.data(i).wr
    io.mapped_data(i).wdata := io.data(i).wdata
    io.mapped_data(i).asid := io.data(i).asid

    io.unmapped_data(i).size := io.data(i).size
    io.unmapped_data(i).addr := data_physical_addr(i)
    io.unmapped_data(i).wr := io.data(i).wr
    io.unmapped_data(i).wdata := io.data(i).wdata
    io.unmapped_data(i).asid := DontCare

    io.data(i).addr_ok := Mux(should_cache_data,io.mapped_data(i).addr_ok,io.unmapped_data(i).addr_ok)
    io.data(i).data_ok := Mux(should_cache_data_r,io.mapped_data(i).data_ok,io.unmapped_data(i).data_ok)
    io.data(i).rdata := Mux(should_cache_data_r,io.mapped_data(i).rdata,io.unmapped_data(i).rdata)
  }
  io.data(0).ex := Mux(should_cache_data_c && should_map_data_c,io.mapped_data(0).ex,0.U)
  io.data(1).ex := DontCare
  io.data_addr_is_mapped := should_map_data
  io.data_unmap_should_cache := should_cache_data

  io.inst.addr_ok := queue.io.enq.ready && !wait_for_dequeue

  queue.io.enq.bits.addr := io.inst.addr
  queue.io.enq.bits.wr := io.inst.wr
  queue.io.enq.bits.size := io.inst.size
  queue.io.enq.bits.wdata := io.inst.wdata
  queue.io.enq.bits.should_map := should_map_inst_c
  queue.io.enq.bits.should_cache := should_cache_inst_c
  queue.io.enq.valid := io.inst.req && !wait_for_dequeue
  queue.io.enq.bits.jump := io.inst.inst_predict_jump_out
  queue.io.enq.bits.target := io.inst.inst_predict_jump_target_out
  queue.io.enq.bits.asid := io.inst.asid


  physical_inst_addr := get_physical_addr(io.inst.addr)
  physical_queue_inst_addr := get_physical_addr(queue.io.enq.bits.addr)




  should_cache_inst_c := should_cache(io.inst.addr)
  should_map_inst_c := should_map(io.inst.addr)
  when(io.inst.req){
    handshake := false.B
  }

  when(io.mapped_inst.req && io.mapped_inst.addr_ok || io.unmapped_inst.req && io.unmapped_inst.addr_ok){
    handshake :=true.B
  }
  io.mapped_inst.req := Mux(io.inst.req,should_cache_inst_c,queue.io.deq.valid && !handshake && queue.io.deq.bits.should_cache)
  io.mapped_inst.wr := false.B
  io.mapped_inst.size := 2.U
  io.mapped_inst.addr := io.inst.addr
  io.mapped_inst.wdata := 0.U
  io.mapped_inst.asid := io.inst.asid
  io.inst_addr_is_mapped := Mux(io.inst.req,should_map_inst_c,queue.io.deq.bits.should_map)
  io.inst_unmap_should_cache := Mux(io.inst.req,should_cache_inst_c,queue.io.deq.bits.should_cache)

  io.inst_unmap_should_cache

  io.unmapped_inst.req := Mux(io.inst.req,!should_cache_inst_c,queue.io.deq.valid && !queue.io.deq.bits.should_cache && !handshake)
  io.unmapped_inst.wr  := false.B
  io.unmapped_inst.size := 2.U
  io.unmapped_inst.addr := Mux(io.inst.req,physical_inst_addr,physical_queue_inst_addr)
  io.unmapped_inst.wdata := 0.U
  io.unmapped_inst.asid := DontCare


  io.inst.data_ok := Mux(queue.io.deq.bits.should_cache,io.mapped_inst.data_ok,io.unmapped_inst.data_ok)
  io.inst.rdata := Mux(queue.io.deq.bits.should_cache,io.mapped_inst.rdata,io.unmapped_inst.rdata)
  io.inst.inst_valid := Mux(queue.io.deq.bits.should_cache,io.mapped_inst.inst_valid,io.unmapped_inst.inst_valid)
  io.inst.inst_predict_jump_in := queue.io.deq.bits.jump
  io.inst.inst_predict_jump_target_in := queue.io.deq.bits.target
  io.inst.inst_pc := queue.io.deq.bits.addr
  io.inst.ex := Mux(should_cache_inst_c && should_map_inst_c,io.mapped_inst.ex,0.U) //只有cache部分会出例外
  queue.io.deq.ready := io.inst.data_ok || (io.inst.req && should_cache_inst_c && io.inst.ex=/= 0.U) || wait_for_dequeue

  when(io.inst.req && should_cache_inst_c && io.inst.ex=/= 0.U ){//需要出队两个
    wait_for_dequeue := true.B
  }
  when(wait_for_dequeue){
    wait_for_dequeue := false.B
  }
  io.mapped_inst.inst_predict_jump_target_out := DontCare
  io.mapped_inst.inst_predict_jump_out := DontCare
  io.unmapped_inst.inst_predict_jump_target_out := DontCare
  io.unmapped_inst.inst_predict_jump_out := DontCare


  def get_physical_addr(virtual_addr:UInt):UInt= {
    val converter = Module(new physical_addr)
    converter.io.virtual_addr := virtual_addr
    converter.io.physical_addr
  }
  def should_map(virtual_addr:UInt):Bool = {
    !(virtual_addr >= "h8000_0000".U && virtual_addr <= "hbfff_ffff".U)
  }
  def should_cache(virtual_addr:UInt):Bool = {
    Mux(cache_all_inst,true.B,!(virtual_addr >= "ha000_0000".U && virtual_addr <= "hbfff_ffff".U))
  }
  def should_cache_d(virtual_addr:UInt):Bool = {
    !(virtual_addr >= "ha000_0000".U && virtual_addr <= "hbfff_ffff".U)
  }
}
class physical_addr extends Module{
  val io = IO(new Bundle{
    val virtual_addr = Input(UInt(32.W))
    val physical_addr = Output(UInt(32.W))
  })
  when(io.virtual_addr >= "h8000_0000".U && io.virtual_addr <= "hbfff_ffff".U){
    io.physical_addr := io.virtual_addr  & "h1fff_ffff".U
  }.otherwise{
    io.physical_addr := io.virtual_addr
  }
}