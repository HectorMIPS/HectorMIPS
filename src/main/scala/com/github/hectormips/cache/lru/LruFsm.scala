package com.github.hectormips.cache.lru

import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chisel3.util._

import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer

class LruFsm(val wayNum: Int) extends Module {
  val wayNumWidth = log2Ceil(wayNum)
  def fact(n: Int): Int = if (n <= 0) 1 else n * fact(n - 1)
  val lruWidth = log2Ceil(fact(wayNum))

  val io = IO(new Bundle {
    val current = Input(UInt(lruWidth.W))
    val visit = Input(UInt(wayNumWidth.W))
    val next = Output(UInt(lruWidth.W))
    val sel = Output(UInt(wayNumWidth.W))
  })

  val perms = (0 until wayNum).permutations.toList
  def perm2state(p: List[Int]) = perms.indexOf(p)
  def nextState(perm: List[Int], visit: Int) = {
    val idx = perm.indexOf(visit)
    perm2state(perm.take(idx) ++ perm.drop(idx + 1) :+ visit)
  }

  // build sel table
  val selBuf = new ArrayBuffer[UInt]()
  for (p <- perms)
    selBuf += p.head.U(wayNumWidth.W)
  val selTable = VecInit(selBuf)

  // build next table
  val nextBuf = new ArrayBuffer[UInt]()
  for (p <- perms) {
    for (visit <- 0 until wayNum)
      nextBuf += nextState(p.toList, visit).U(lruWidth.W)
  }
  val nextTable = VecInit(nextBuf)

  def fuse(current: UInt, visit: UInt) :UInt={
    (current << wayNumWidth.U) | visit
  }
  io.next := nextTable(fuse(io.current, io.visit))
  io.sel := selTable(io.current)
}

object LruFsm extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new LruFsm(wayNum = 4))))
}
