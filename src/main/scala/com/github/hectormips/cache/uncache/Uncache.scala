package com.github.hectormips.cache.uncache


import chisel3._
import chisel3.util._
import com.github.hectormips.{AXIIOWithoutWid, SRamLikeIO}
import com.github.hectormips.cache.setting._
import com.github.hectormips.cache.utils.Wstrb

/**
 * 从官方的sram桥翻译过来
 */
class Uncache extends Module{
  val io = IO(new Bundle{
    val input = Flipped(new SRamLikeIO())
    val axi = new AXIIOWithoutWid(1)
  })
  val wstrb = Module(new Wstrb())
  val do_req =  RegInit(false.B)
  val do_req_or = RegInit(false.B)
  val do_wr_r = Reg(Bool())
  val do_size_r = Reg(UInt(2.W))
  val do_addr_r = Reg(UInt(32.W))
  val do_wdata_r = Reg(UInt(32.W))
  
  val data_back = Wire(Bool())
  
  io.input.addr_ok := !do_req
  
  when(io.input.req && !do_req){
    do_req := true.B
  }.elsewhen(data_back){
    do_req := 0.U
  }
  
  when(!do_req){
    do_req_or := io.input.req
  }
  
  when(io.input.req && io.input.addr_ok){
    do_wr_r := io.input.wr
    do_size_r := io.input.size
    do_addr_r := io.input.addr
    do_wdata_r := io.input.wdata
  }
  
  io.input.data_ok := do_req && do_req_or && data_back
  io.input.rdata := io.axi.rdata
  
  
  //---axi
  val addr_rev = RegInit(false.B)
  val wdata_rev = RegInit(false.B)
  
  data_back := addr_rev && (io.axi.rvalid.asBool() && io.axi.rready.asBool() 
    || io.axi.bvalid.asBool() && io.axi.bready.asBool())
  
  when(io.axi.arvalid.asBool() && io.axi.arready.asBool()){
    addr_rev := true.B
  }.elsewhen(io.axi.awvalid.asBool() && io.axi.awready.asBool()){
    addr_rev := true.B
  }.elsewhen(data_back){
    addr_rev := false.B
  }
  when(io.axi.wvalid.asBool() && io.axi.wready.asBool()) {
    wdata_rev := true.B
  }.elsewhen(data_back){
    wdata_rev := false.B
  }
  
  //ar
  io.axi.arid := 2.U
  io.axi.araddr := do_addr_r
  io.axi.arlen := 0.U
  io.axi.arsize := do_size_r
  io.axi.arburst := 0.U
  io.axi.arlock := 0.U
  io.axi.arcache := 0.U
  io.axi.arprot := 0.U
  io.axi.arvalid := do_req && !do_wr_r && !addr_rev
  
  //r
  io.axi.rready := 1.U
  
  //aw
  io.axi.awid := 2.U
  io.axi.awaddr := do_addr_r
  io.axi.awlen := 0.U
  io.axi.awsize := do_size_r
  io.axi.awburst := 0.U
  io.axi.awlock := 0.U
  io.axi.awcache := 0.U
  io.axi.awprot := 0.U
  io.axi.awvalid := do_req && do_wr_r && !addr_rev
  
  //w
  io.axi.wdata := do_wdata_r
  
  wstrb.io.offset := do_addr_r(1,0)
  wstrb.io.size := do_size_r
  io.axi.wstrb := wstrb.io.mask

  io.axi.wlast := true.B
  io.axi.wvalid := do_req && do_wr_r && !wdata_rev
  
  //b
  io.axi.bready := true.B


}
