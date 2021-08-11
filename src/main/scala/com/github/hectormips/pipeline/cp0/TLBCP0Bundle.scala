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
