package com.github.hectormips.utils

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

class LruComb(size: Int) extends Module{
  require(size > 0)
  require(isPow2(size))

  val LEN: Int = log2Ceil(size)
  val WIDTH: Int = size * (size - 1) / 2

  class LruMoreIO extends Bundle {
    val current: UInt = Input(UInt(WIDTH.W))
    val access: UInt = Input(UInt(LEN.W))

    val update: UInt = Output(UInt(WIDTH.W))
    val lru_pre: UInt = Output(UInt(size.W))
    val lru_post: UInt = Output(UInt(size.W))
  }

  val io: LruMoreIO = IO(new LruMoreIO)

  val expend_pre: Vec[Vec[Bool]] = Wire(Vec(size, Vec(size, Bool())))

  var offset = 0

  for (i <- 0 until size){
    expend_pre(i)(i) := 1.B

    for (j <- i+1 until size) {
      expend_pre(i)(j) := io.current(offset + j - i - 1)
    }
    for (j <- 0 until i) {
      expend_pre(i)(j) := ~expend_pre(j)(i)
    }

    offset = offset + size - i - 1
  }

  val lru_pre: Vec[Bool] = Wire(Vec(size, Bool()))
  for (i <- 0 until size){
    lru_pre(i) := expend_pre(i).asUInt().andR()
  }
  io.lru_pre := lru_pre.asUInt()

  val expend_post: Vec[Vec[Bool]] = Wire(Vec(size, Vec(size, Bool())))
  for (i <- 0 until size){
    for (j <- 0 until size){
      if (i!= j){
        expend_post(i)(j) := MuxCase(expend_pre(i)(j),Seq(
          (io.access === i.U)  -> 0.B,
          (io.access === j.U)  -> 1.B,
        ))
      }else{
        expend_post(i)(j) := 1.B
      }
    }
  }

  val lru_post: Vec[Bool] = Wire(Vec(size, Bool()))
  for (i <- 0 until size){
    lru_post(i) := expend_post(i).asUInt().andR()
  }
  io.lru_post := lru_pre.asUInt()


  val update: Vec[Bool] = Wire(Vec(WIDTH, Bool()))
  offset = 0
  for (i <- 0 until size){
    for (j <- i+1 until size){
      update(offset + j - i - 1) := expend_post(i)(j)
    }
    offset = offset + size - i - 1
  }
  io.update := update.asUInt()
}

object LruComb extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new LruComb(16))))
}

