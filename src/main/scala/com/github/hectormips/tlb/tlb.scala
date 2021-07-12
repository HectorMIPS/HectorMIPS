package com.github.hectormips.tlb

import chisel3._
import chisel3.util._

class SearchPort(TLBNUM:Int) extends Bundle{
  val vpn2 = Input(UInt(19.W))
  val odd_page = Input(UInt(1.W))
  val asid = Input(UInt(8.W))
  val found = Output(UInt((log2Ceil(TLBNUM)-1).W))
  val pfn = Output(UInt(20.W))
  val c = Output(UInt(3.W))
  val d = Output(UInt(1.W))
  val v = Output(UInt(1.W))
}

class tlb(TLBNUM:Int) extends Module{
  val io = IO(new Bundle{
    val s0 = new SearchPort(TLBNUM)
    val s1 = new SearchPort(TLBNUM)

    val we = Input(UInt(1.W))
    val w_index =  Input(UInt((log2Ceil(TLBNUM)-1).W))
    val w_vpn2 = Input(UInt(19.W))
    val w_asid = Input(UInt(8.W))
    val w_g = Input(UInt(1.W))
    val w_pfn0 = Input(UInt(20.W))
    val w_c0 = Input(UInt(3.W))
    val w_d0 = Input(UInt(1.W))
    val w_v0 = Input(UInt(1.W))
    val w_pfn1 = Input(UInt(20.W))
    val w_c1 = Input(UInt(3.W))
    val w_d1 = Input(UInt(1.W))
    val w_v1 = Input(UInt(1.W))

    val r_index =  Output(UInt((log2Ceil(TLBNUM)-1).W))
    val r_vpn2 = Output(UInt(19.W))
    val r_asid = Output(UInt(8.W))
    val r_g = Output(UInt(1.W))
    val r_pfn0 = Output(UInt(20.W))
    val r_c0 = Output(UInt(3.W))
    val r_d0 = Output(UInt(1.W))
    val r_v0 = Output(UInt(1.W))
    val r_pfn1 = Output(UInt(20.W))
    val r_c1 = Output(UInt(3.W))
    val r_d1 = Output(UInt(1.W))
    val r_v1 = Output(UInt(1.W))
  })
}
