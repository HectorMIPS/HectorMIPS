package com.github.hectormips.pipeline.cp0

import chisel3._
import chisel3.util.log2Ceil

class TLBPCP0Bundle(n_tlb: Int) extends Bundle {
  val is_tlbp: Bool = Bool()
  val found  : Bool = Bool()
  val index  : UInt = UInt(log2Ceil(n_tlb).W)

  def defaults(): Unit = {
    is_tlbp := 0.B
    found := 0.B
    index := 0.U
  }
}

class TLBWICP0Bundle(n_tlb: Int) extends Bundle {
  val entryhi : UInt = UInt(32.W)
  val entrylo0: UInt = UInt(32.W)
  val entrylo1: UInt = UInt(32.W)
  val index   : UInt = UInt(log2Ceil(n_tlb).W)

  def defaults(): Unit = {
    entryhi := 0.U
    entrylo0 := 0.U
    entrylo1 := 0.U
    index := 0.U
  }
}

// 复用tlbwi的index
class TLBRCP0Bundle extends Bundle {
  val entryhi : UInt = UInt(32.W)
  val entrylo0: UInt = UInt(32.W)
  val entrylo1: UInt = UInt(32.W)
  val is_tlbr : Bool = Bool()
}