package com.github.hectormips.cache.uncache


import chisel3._
import chisel3.util._
import com.github.hectormips.{AXIIO, SRamLikeIO}
import com.github.hectormips.cache.setting._
import com.github.hectormips.cache.utils.Wstrb
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
class QueueItem extends Bundle{
  val port = UInt(1.W)
  val addr = UInt(32.W)
  val wr   = Bool()
  val size = UInt(2.W)
  val wdata = UInt(32.W)
}
/**
 * 从官方的sram桥翻译过来
 */
class Uncache extends Module{
  val io = IO(new Bundle{
    val input = Vec(2,Flipped(new SRamLikeIO()))
    val axi = new AXIIO(1)
  })
  val wstrb = Module(new Wstrb())
  val do_req =  RegInit(false.B)
//  val do_req_or = RegInit(false.B)
  val do_wr_r = RegInit(false.B)
  val do_size_r = RegInit(0.U(2.W))
  val do_addr_r = RegInit(0.U(32.W))
  val do_wdata_r = RegInit(0.U(32.W))
  
  val data_back = Wire(Bool())

  val exe_port = Wire(Bool())
  val exe_port_r =RegInit(false.B)


  val polling = RegInit(false.B)
  val portHandleReq = Wire(Bool())
  io.input(0).ex := 0.U
  io.input(1).ex := 0.U
  portHandleReq := false.B
  polling := ~polling
  io.input(0).addr_ok :=  !do_req && polling
  io.input(1).addr_ok :=  !do_req && !polling

  when( (io.input(0).req || io.input(1).req) && !do_req){
    when(io.input(0).req && io.input(0).addr_ok){
      do_req := true.B
      exe_port := 0.U
      exe_port_r := 0.U
      do_wr_r := io.input(0).wr
      do_size_r := io.input(0).size
      do_addr_r := io.input(0).addr
      do_wdata_r := io.input(0).wdata
    }.elsewhen(io.input(1).req && io.input(1).addr_ok){
      do_req := true.B
      exe_port := 1.U
      exe_port_r := 1.U
      do_wr_r := io.input(1).wr
      do_size_r := io.input(1).size
      do_addr_r := io.input(1).addr
      do_wdata_r := io.input(1).wdata
    }
  }.elsewhen(data_back){
    do_req := false.B
  }
  exe_port := 0.U


  for(i<- 0 to 1) {
    when(exe_port_r === i.U) {
      io.input(i).data_ok := do_req && data_back
      io.input(i).rdata := io.axi.rdata
    }.otherwise{
      io.input(i).data_ok := false.B
      io.input(i).rdata := 0.U
    }
  }
  
  
  //---axi
  val addr_rev = RegInit(false.B)
  val wdata_rev = RegInit(false.B)
  
  data_back := addr_rev && (io.axi.rvalid.asBool() && io.axi.rready.asBool()) ||
    (io.axi.bvalid.asBool() && io.axi.bready.asBool())

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
  io.axi.wid := 2.U
//  dontTouch(io.axi.wid)
  io.axi.wdata := do_wdata_r
  
  wstrb.io.offset := do_addr_r(1,0)
  wstrb.io.size := do_size_r
  io.axi.wstrb := wstrb.io.mask

  io.axi.wlast := true.B
  io.axi.wvalid := do_req && do_wr_r && !wdata_rev
  
  //b
  io.axi.bready := true.B


}
object Uncache extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () => new Uncache())))
}