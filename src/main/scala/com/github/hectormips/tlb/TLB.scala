package com.github.hectormips.tlb

import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chisel3.util._

// 查询接口
class SearchPort(TLBNUM:Int) extends Bundle{
  val vpn2 = Input(UInt(19.W))
  val odd_page = Input(UInt(1.W))
  val asid = Input(UInt(8.W))
  val found = Output(Bool())
  val index = Output(UInt(log2Up(TLBNUM).W))
  val pfn = Output(UInt(20.W))
  val c = Output(UInt(3.W)) // cache标记
  val d = Output(Bool()) // 脏位
  val v = Output(Bool()) // 有效
}

// TLB行
class TLBRow extends Bundle{
  val vpn2 = UInt(19.W)
  val asid = UInt(8.W)
  val g    = UInt(1.W)
  val PFN0 = UInt(20.W)
  val C0   = UInt(3.W)
  val D0   = UInt(1.W)
  val V0   = UInt(1.W)
  val PFN1 = UInt(20.W)
  val C1   = UInt(3.W)
  val D1   = UInt(1.W)
  val V1   = UInt(1.W)
}

class TLBBundle(TLBNUM:Int) extends Bundle {
  val s0 = new SearchPort(TLBNUM) // 查询端口1，供取指使用
  val s1 = new SearchPort(TLBNUM) // 查询端口2，供仿存使用

  // 写相关
  val we = Input(Bool())
  val w_index =  Input(UInt(log2Up(TLBNUM).W)) // TLB表项id
  val w_vpn2 = Input(UInt(19.W))
  val w_asid = Input(UInt(8.W))
  val w_g = Input(Bool())
  val w_pfn0 = Input(UInt(20.W))
  val w_c0 = Input(UInt(3.W))
  val w_d0 = Input(Bool())
  val w_v0 = Input(Bool())
  val w_pfn1 = Input(UInt(20.W))
  val w_c1 = Input(UInt(3.W))
  val w_d1 = Input(Bool())
  val w_v1 = Input(Bool())

  //读相关
  val r_index =  Input(UInt(log2Up(TLBNUM).W)) // TLB表项id
  val r_vpn2 = Output(UInt(19.W))
  val r_asid = Output(UInt(8.W))
  val r_g = Output(Bool())
  val r_pfn0 = Output(UInt(20.W))
  val r_c0 = Output(UInt(3.W))
  val r_d0 = Output(Bool())
  val r_v0 = Output(Bool())
  val r_pfn1 = Output(UInt(20.W))
  val r_c1 = Output(UInt(3.W))
  val r_d1 = Output(Bool())
  val r_v1 = Output(Bool())

//  chisel3.util.experimental.forceName(s0.vpn2,"s0_vpn2")
//  chisel3.util.experimental.forceName(s0.odd_page,"s0_odd_page")
//  chisel3.util.experimental.forceName(s0.asid,"s0_asid")
//  chisel3.util.experimental.forceName(s0.found,"s0_found")
//  chisel3.util.experimental.forceName(s0.index,"s0_index")
//  chisel3.util.experimental.forceName(s0.pfn,"s0_pfn")
//  chisel3.util.experimental.forceName(s0.c,"s0_c")
//  chisel3.util.experimental.forceName(s0.d,"s0_d")
//  chisel3.util.experimental.forceName(s0.v,"s0_v")
//
//  chisel3.util.experimental.forceName(s1.vpn2,"s1_vpn2")
//  chisel3.util.experimental.forceName(s1.odd_page,"s1_odd_page")
//  chisel3.util.experimental.forceName(s1.asid,"s1_asid")
//  chisel3.util.experimental.forceName(s1.found,"s1_found")
//  chisel3.util.experimental.forceName(s1.index,"s1_index")
//  chisel3.util.experimental.forceName(s1.pfn,"s1_pfn")
//  chisel3.util.experimental.forceName(s1.c,"s1_c")
//  chisel3.util.experimental.forceName(s1.d,"s1_d")
//  chisel3.util.experimental.forceName(s1.v,"s1_v")
//
//  chisel3.util.experimental.forceName(we,"we")
//  chisel3.util.experimental.forceName(w_index,"w_index")
//  chisel3.util.experimental.forceName(w_vpn2,"w_vpn2")
//  chisel3.util.experimental.forceName(w_asid,"w_asid")
//  chisel3.util.experimental.forceName(w_g,"w_g")
//  chisel3.util.experimental.forceName(w_pfn0,"w_pfn0")
//  chisel3.util.experimental.forceName(w_c0,"w_c0")
//  chisel3.util.experimental.forceName(w_d0,"w_d0")
//  chisel3.util.experimental.forceName(w_v0,"w_v0")
//  chisel3.util.experimental.forceName(w_pfn1,"w_pfn1")
//  chisel3.util.experimental.forceName(w_c1,"w_c1")
//  chisel3.util.experimental.forceName(w_d1,"w_d1")
//  chisel3.util.experimental.forceName(w_v1,"w_v1")
//  chisel3.util.experimental.forceName(r_index,"r_index")
//  chisel3.util.experimental.forceName(r_vpn2,"r_vpn2")
//  chisel3.util.experimental.forceName(r_asid,"r_asid")
//  chisel3.util.experimental.forceName(r_g,"r_g")
//  chisel3.util.experimental.forceName(r_pfn0,"r_pfn0")
//  chisel3.util.experimental.forceName(r_c0,"r_c0")
//  chisel3.util.experimental.forceName(r_d0,"r_d0")
//  chisel3.util.experimental.forceName(r_v0,"r_v0")
//  chisel3.util.experimental.forceName(r_pfn1,"r_pfn1")
//  chisel3.util.experimental.forceName(r_c1,"r_c1")
//  chisel3.util.experimental.forceName(r_d1,"r_d1")
//  chisel3.util.experimental.forceName(r_v1,"r_v1")

}
class tlb(TLBNUM:Int) extends Module{
  /**
   * 书上压缩了页表(32-log2(4k)=20,而这里只有19)，VPN2低位忽略，表示两个4KB的页
   * --------------------------------------------------------
   * | VPN2 | ASID | G  | PFN0 | C0,D0,V0 | PFN1 | C1,D1,V1 |
   * | 19b  | 8b   |1b  | 20b  | 3  1  1  | 20b  | 3  1  1  |
   * --------------------------------------------------------
   *
   */
//  chisel3.util.experimental.forceName(clock,"clk")
  val io = IO(new TLBBundle(TLBNUM))
  val tlbrow = Reg(Vec(TLBNUM,new TLBRow))

  /**
   * 查询
   */
  def tlb_match(tlbrow: Vec[TLBRow],s:SearchPort):Unit={
    val match0 =  Wire(Vec(TLBNUM, Bool()))
    val index0  = Wire(UInt(log2Up(TLBNUM).W))
    index0 :=  OHToUInt(match0.asUInt())
    for(i<- 0 until TLBNUM)
      // vpn匹配 且 进程id匹配
      match0(i)  := ((s.vpn2 === tlbrow(i).vpn2) && ( (s.asid === tlbrow(i).asid) || (tlbrow(i).g.asBool())))
    when(match0.asUInt()===0.U(1.W)) {
      // 未找到
      s.found := false.B
      s.index := 0.U
      s.pfn   := 0.U
      s.c     := 0.U
      s.d     := 0.U
      s.v     := 0.U
    }.elsewhen( (s.odd_page===false.B && tlbrow(index0).V0=/=true.B)  || (s.odd_page===true.B && tlbrow(index0).V1=/=true.B)){
      // invalid
      s.found := false.B
      s.index := 0.U
      s.pfn   := 0.U
      s.c     := 0.U
      s.d     := 0.U
      s.v     := 0.U
    }.otherwise{
      s.found := true.B
      s.index := index0
      s.pfn   := Mux(s.odd_page.asBool(),tlbrow(index0).PFN1,tlbrow(index0).PFN0)
      s.c     := Mux(s.odd_page.asBool(),tlbrow(index0).C1,tlbrow(index0).C0)
      s.d     := Mux(s.odd_page.asBool(),tlbrow(index0).D1,tlbrow(index0).D0)
      s.v     := Mux(s.odd_page.asBool(),tlbrow(index0).V1,tlbrow(index0).V0)
    }
  }
  tlb_match(tlbrow,io.s0)
  tlb_match(tlbrow,io.s1)

  when(io.we){
    /**
     * 写
     */
    tlbrow(io.w_index).vpn2 := io.w_vpn2
    tlbrow(io.w_index).asid := io.w_asid
    tlbrow(io.w_index).g    := io.w_g
    tlbrow(io.w_index).PFN0 := io.w_pfn0
    tlbrow(io.w_index).C0   := io.w_c0
    tlbrow(io.w_index).D0   := io.w_d0
    tlbrow(io.w_index).V0   := io.w_v0
    tlbrow(io.w_index).PFN1 := io.w_pfn1
    tlbrow(io.w_index).C1   := io.w_c1
    tlbrow(io.w_index).D1   := io.w_d1
    tlbrow(io.w_index).V1   := io.w_v1

  }
    io.r_vpn2 := tlbrow(io.r_index).vpn2
    io.r_asid := tlbrow(io.r_index).asid
    io.r_g    := tlbrow(io.r_index).g
    io.r_pfn0 := tlbrow(io.r_index).PFN0
    io.r_c0   := tlbrow(io.r_index).C0
    io.r_d0   := tlbrow(io.r_index).D0
    io.r_v0   := tlbrow(io.r_index).V0
    io.r_pfn1 := tlbrow(io.r_index).PFN1
    io.r_c1   := tlbrow(io.r_index).C1
    io.r_d1   := tlbrow(io.r_index).D1
    io.r_v1   := tlbrow(io.r_index).V1
}

object tlb extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new tlb(16))))
}


