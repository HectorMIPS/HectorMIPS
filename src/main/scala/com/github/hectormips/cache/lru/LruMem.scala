package com.github.hectormips.cache.lru

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import com.github.hectormips.cache.setting.CacheConfig

object fact {
  def f(x: Int): Int = if (x <= 0) 1 else x * f(x - 1)
  def apply(x: Int): Int = f(x)
}

object getLruWidth {
  def apply(wayNum: Int): Int = log2Ceil(fact(wayNum))
}

class LruMem(val config:CacheConfig) extends Module {
  def getMask(w: Int, n: UInt): UInt = {
    (1.U << n).asUInt.pad(w)
  }

  def setBit(x: UInt, n: UInt): UInt = {
    val w = x.getWidth
    x | getMask(w, n)
  }

  def clearBit(x: UInt, n: UInt): UInt = {
    val m: UInt = getMask(x.getWidth, n)
    x & (~m).asUInt
  }
  val lruWidth = getLruWidth(config.wayNum)
  val io = IO(new Bundle {
    val setAddr = Input(UInt(config.indexWidth.W))
    val visit = Input(UInt(config.wayNumWidth.W))
    val visitValid = Input(Bool())
    val waySel = Output(UInt(config.wayNumWidth.W))
  })

  val lruMem = Mem(config.lineNum, UInt(lruWidth.W))
  val validMem = RegInit(0.U(config.lineNum.W))

  def readLru(addr: UInt): UInt = Mux(validMem(addr), lruMem(addr), 0.U(lruWidth.W))

  val lruFsm = Module(new LruFsm(config.wayNum))
  lruFsm.io.current := readLru(io.setAddr)
  lruFsm.io.visit := io.visit
  io.waySel := lruFsm.io.sel

  when (io.visitValid) {
    validMem := setBit(validMem, io.setAddr)
    lruMem(io.setAddr) := lruFsm.io.next
  }
}

object LruMem extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new LruMem(new CacheConfig()))))
}
