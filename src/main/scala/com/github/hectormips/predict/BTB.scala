package com.github.hectormips.predict

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import com.github.hectormips.utils.Lru

class BTB(size: Int, BHT_size: Int) extends Module {
  require(isPow2(size))
  require(isPow2(BHT_size))

  val len: Int = log2Ceil(size)
  val bht_len: Int = log2Ceil(BHT_size)

  class BTB_IO extends Bundle {
    // 分支预测
    val pc: UInt = Input(UInt(32.W))
    // 预测是否分支成功
    val predict: Bool = Output(Bool())
    // 成功后的结果
    val target: UInt = Output(UInt(32.W))

    // 执行端的执行结果
    val en_ex: Bool = Input(Bool())
    val ex_pc: UInt = Input(UInt(32.W))
    // 分支是否成功
    val ex_success: Bool = Input(Bool())
    val ex_target: UInt = Input(UInt(32.W))
  }

  class LookUpResult extends Bundle {
    val is_find: Bool = Bool()
    val result: UInt = UInt(32.W)
  }

  def get_index(pc: UInt): LookUpResult = {
    // 查找是否有记录
    val index: LookUpResult = Wire(new LookUpResult)
    index := MuxCase({
      val default = Wire(new LookUpResult)
      default.is_find := 0.U
      default.result := 0.U
      default
    }, (0 until size).map(i => (location_table(i) === pc, {
      val default = Wire(new LookUpResult)
      default.is_find := 1.U
      default.result := i.U
      default
    })))
    index
  }

  val io: BTB_IO = IO(new BTB_IO)

  val location_table: Vec[UInt] = RegInit(VecInit(Seq.fill(size)(0.U(32.W))))

  val target_table: Vec[UInt] = RegInit(VecInit(Seq.fill(size)(0.U(32.W))))

  val pattern_table: Vec[UInt] = RegInit(VecInit(Seq.fill(size)(0.U(bht_len.W))))

  val BHT_table: Vec[Vec[Bool]] = Wire(Vec(size, Vec(BHT_size, Bool())))

  val valid_table: Vec[Bool] = RegInit(VecInit(Seq.fill(size)(0.B)))

  val find_index: LookUpResult = Wire(new LookUpResult)
  find_index := get_index(io.pc)

  val pattern : UInt = Wire(UInt(bht_len.W))
  pattern := target_table(find_index.result)

  val ex_index: LookUpResult = Wire(new LookUpResult)
  ex_index := get_index(io.ex_pc)

  val ex_pattern : UInt = Wire(UInt(bht_len.W))
  ex_pattern := target_table(ex_index.result)


  io.target := target_table(find_index.result)
  io.predict := find_index.is_find & BHT_table(find_index.result)(pattern)

  val lru: Lru = Module(new Lru(size, 128))
  lru.io.en := 1.B
  lru.io.valid := valid_table
  lru.io.visitor := Mux(ex_index.is_find, ex_index.result, lru.io.next)
  lru.io.en_visitor := io.en_ex & ex_index.is_find

  when(io.en_ex ) {
    when(ex_index.is_find){
      pattern_table(ex_index.result) := (pattern_table(ex_index.result) << 1).asUInt() & io.ex_success
      target_table(ex_index.result) := io.ex_target
    }.otherwise{
      target_table(lru.io.next) := io.ex_pc
      pattern_table(lru.io.next) := 0.U
    }
  }

  for (i <- 0 until size) {
    for (j <- 0 until BHT_size) {
      withReset(reset.asBool() & (!ex_index.is_find & lru.io.next === i.U)){
        val bht = Module(new BHT)
        bht.io.is_visited := io.ex_success
        bht.io.en_visit := ex_index.result === i.U & ex_pattern === j.U
        BHT_table(i)(j) := bht.io.predict
      }
    }
  }
}

object BTB extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new BTB(16, 16))))
}
