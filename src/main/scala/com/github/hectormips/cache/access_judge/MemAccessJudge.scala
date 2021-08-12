package com.github.hectormips.cache.access_judge

import com.github.hectormips.{AXIIOWithoutWid, SRamLikeDataIO, SRamLikeInstIO}
import chisel3._
import chisel3.util._
import com.github.hectormips.cache.uncache.UncacheInst




class QueueItem extends Bundle{
  val should_map = Bool()
  val addr         = UInt(32.W)
  val wdata         = UInt(32.W)
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


  val should_map_data_r = RegInit(VecInit(Seq.fill(2)(false.B)))
  val should_map_data_c = Wire(Vec(2,Bool()))
  val should_map_data   = Wire(Vec(2,Bool()))
  // uncache的地址范围： 0xa000_0000 -- 0xbfff_ffff
  //

  for(i<- 0 until 2) {
    /**
     * cache 属性确认
     */
    should_map_data(i) := Mux(io.data(i).req,should_map_data_c(i),should_map_data_r(i))
    when(io.data(i).req){
      should_map_data_r(i) := should_map_data_c(i)
    }
    when(io.data(i).addr >= "ha000_0000".U && io.data(i).addr <= "hbfff_ffff".U){
      should_map_data_c(i) := false.B
    }.otherwise {
      should_map_data_c(i) := true.B
    }

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
//  val should_cache_inst   = Wire(Bool())

  val queue = Module(new Queue(new QueueItem, 3))
  /**
   * 如果需要快速测试，cache_all_inst 设为true即可
   */
  when(cache_all_inst) {
    should_map_inst_c := true.B
  }.otherwise{
      when(io.inst.addr >= "ha000_0000".U && io.inst.addr <= "hbfff_ffff".U){
        should_map_inst_c := false.B
      }.otherwise {
        should_map_inst_c := true.B
      }
  }


//  when(io.inst.addr >= "h8000_0000".U && io.inst.addr <= "hbfff_ffff".U){
//    inst_physical_addr := queue.io.deq.bits.addr  & "h1fff_ffff".U
//  }.otherwise{
//    inst_physical_addr := queue.io.deq.bits.addr
//  }

//  io.inst.addr :=

  //  should_cache_data := false.B

  for(i <- 0 until 2) {
    io.mapped_data(i).req := should_map_data(i) & io.data(i).req
    io.unmapped_data(i).req := !should_map_data(i) & io.data(i).req
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

    io.data(i).addr_ok := Mux(should_map_data(i),io.mapped_data(i).addr_ok,io.unmapped_data(i).addr_ok)
    io.data(i).data_ok := Mux(should_map_data(i),io.mapped_data(i).data_ok,io.unmapped_data(i).data_ok)
    io.data(i).rdata := Mux(should_map_data(i),io.mapped_data(i).rdata,io.unmapped_data(i).rdata)
  }
  io.data(0).ex := Mux(should_map_data(0),io.mapped_data(0).ex,0.U)
  io.data(1).ex := DontCare

  io.inst.addr_ok := queue.io.enq.ready

  queue.io.enq.bits.addr := io.inst.addr
  queue.io.enq.bits.wr := io.inst.wr
  queue.io.enq.bits.size := io.inst.size
  queue.io.enq.bits.wdata := io.inst.wdata
  queue.io.enq.bits.should_map := should_map_inst_c
  queue.io.enq.valid := io.inst.req
  queue.io.enq.bits.jump := io.inst.inst_predict_jump_out
  queue.io.enq.bits.target := io.inst.inst_predict_jump_target_out
  queue.io.enq.bits.asid := io.inst.asid

  val handshake = RegInit(false.B)
  val physical_inst_addr = Wire(UInt(32.W))
  val physical_queue_inst_addr = Wire(UInt(32.W))
  physical_inst_addr := physical_addr(io.inst.addr)
  physical_queue_inst_addr := physical_addr(queue.io.enq.bits.addr)

  when(io.inst.req){
    handshake := false.B
  }

  when(io.mapped_inst.req && io.mapped_inst.addr_ok || io.unmapped_inst.req && io.unmapped_inst.addr_ok){
    handshake :=true.B
  }
  io.mapped_inst.req := Mux(io.inst.req,should_map_inst_c,queue.io.deq.valid && !handshake && queue.io.deq.bits.should_map)
  io.mapped_inst.wr := false.B
  io.mapped_inst.size := 2.U
  io.mapped_inst.addr := Mux(io.inst.req,physical_inst_addr,physical_queue_inst_addr)
  io.mapped_inst.wdata := 0.U
  io.mapped_inst.asid := io.inst.asid

  io.unmapped_inst.req := Mux(io.inst.req,!should_map_inst_c,queue.io.deq.valid && !queue.io.deq.bits.should_map && !handshake)
  io.unmapped_inst.wr  := false.B
  io.unmapped_inst.size := 2.U
  io.unmapped_inst.addr := Mux(io.inst.req,physical_inst_addr,physical_queue_inst_addr)
  io.unmapped_inst.wdata := 0.U
  io.unmapped_inst.asid := DontCare


  io.inst.data_ok := Mux(queue.io.deq.bits.should_map,io.mapped_inst.data_ok,io.unmapped_inst.data_ok)
  io.inst.rdata := Mux(queue.io.deq.bits.should_map,io.mapped_inst.rdata,io.unmapped_inst.rdata)
  io.inst.inst_valid := Mux(queue.io.deq.bits.should_map,io.mapped_inst.inst_valid,io.unmapped_inst.inst_valid)
  io.inst.inst_predict_jump_in := queue.io.deq.bits.jump
  io.inst.inst_predict_jump_target_in := queue.io.deq.bits.target
  io.inst.inst_pc := queue.io.deq.bits.addr
  io.inst.ex := Mux(queue.io.deq.bits.should_map,io.mapped_inst.ex,0.U) //只有cache部分会出例外
  queue.io.deq.ready := io.inst.data_ok

  io.mapped_inst.inst_predict_jump_target_out := DontCare
  io.mapped_inst.inst_predict_jump_out := DontCare
  io.unmapped_inst.inst_predict_jump_target_out := DontCare
  io.unmapped_inst.inst_predict_jump_out := DontCare


  def physical_addr(virtual_addr:UInt):UInt={
    val physical_addr: UInt = Wire(UInt(32.W))
    when(io.inst.addr >= "h8000_0000".U && io.inst.addr <= "hbfff_ffff".U){
      physical_addr := virtual_addr  & "h1fff_ffff".U
    }.otherwise{
      physical_addr := virtual_addr
    }
    physical_addr
  }
}
