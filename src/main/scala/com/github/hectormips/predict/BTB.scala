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
    // 分支指令的PC值
    val ex_pc: UInt = Input(UInt(32.W))
    // 分支是否成功
    val ex_success: Bool = Input(Bool())
    // 分支的目的地址
    val ex_target: UInt = Input(UInt(32.W))
  }

  class LookUpResult extends Bundle {
    val is_find: Bool = Bool()
    val result: UInt = UInt(len.W)
  }

  private def get_index(pc: UInt): LookUpResult = {
    // 查找是否有记录
    val index: LookUpResult = Wire(new LookUpResult)
    index := MuxCase({
      val default = Wire(new LookUpResult)
      default.is_find := 0.B
      default.result := 0.U(len.W)
      default
    }, (0 until size).map(i => (location_table(i) === pc, {
      val default = Wire(new LookUpResult)
      default.is_find := 1.B
      default.result := i.U(len.W)
      default
    })))
    index
  }

  private def update_pattern_table(index: UInt, is_success: Bool): UInt = {
    val result: Bundle {
      val old_pattern: UInt
      val new_pattern: Bool
    } = Wire(new Bundle {
      val old_pattern: UInt = UInt((bht_len - 1).W)
      val new_pattern: Bool = Bool()
    })
    result.old_pattern := index(bht_len - 2, 0)
    result.new_pattern := is_success

    result.asUInt()
  }

  val io: BTB_IO = IO(new BTB_IO)

  // pc表， 用于储存PC地址
  val location_table: Vec[UInt] = RegInit(VecInit(Seq.fill(size)(0.U(32.W))))

  // target表， 用于储存pc的目的地址
  val target_table: Vec[UInt] = RegInit(VecInit(Seq.fill(size)(0.U(32.W))))

  // pattern_table 用于存储每个PC的匹配记录
  val pattern_table: Vec[UInt] = RegInit(VecInit(Seq.fill(size)(0.U(bht_len.W))))

  // BHT 用于存储每个BHT的结果
  val BHT_table: Vec[Vec[Bool]] = Wire(Vec(size, Vec(BHT_size, Bool())))

  // valid表，用于记录每个pc是否可用
  val valid_table: Vec[Bool] = RegInit(VecInit(Seq.fill(size)(0.B)))

  // 译码段 pc查找结果
  val find_index: LookUpResult = Wire(new LookUpResult)
  find_index := get_index(io.pc)

  // 译码段，pattern查找结果
  val pattern: UInt = Wire(UInt(bht_len.W))
  pattern := pattern_table(find_index.result)

  // ex段 pc查找结果
  val ex_index: LookUpResult = Wire(new LookUpResult)
  ex_index := get_index(io.ex_pc)

  // ex段，pattern查找结果
  val ex_pattern: UInt = Wire(UInt(bht_len.W))
  val ex_pattern_next: UInt = Wire(UInt(bht_len.W))
  ex_pattern := pattern_table(ex_index.result)
  ex_pattern_next := update_pattern_table(pattern_table(ex_index.result), io.ex_success)

  // lru的返回值，下一个应该替换的地址
  val lru_result: UInt = Wire(UInt(len.W))

  // LRU
  val lru: Lru = Module(new Lru(size))
  lru.io.visitor := Mux(ex_index.is_find, ex_index.result, lru_result)
  lru.io.en_visitor := io.en_ex

  // 预测值
  io.target := target_table(find_index.result)
  // 预测是否成功
  io.predict := find_index.is_find & BHT_table(find_index.result)(pattern)


  lru_result := lru.io.next


  // 更新BTB
  when(io.en_ex) {
    when(ex_index.is_find) {
      target_table(ex_index.result) := io.ex_target
      pattern_table(ex_index.result) := ex_pattern_next
      valid_table(ex_index.result) := 1.B
    }.otherwise {
      location_table(lru_result) := io.ex_pc
      target_table(lru_result) := io.ex_target
      pattern_table(lru_result) := io.ex_success
      valid_table(lru_result) := 1.B
    }
  }

  // 设置BHT
  for (i <- 0 until size) {
    for (j <- 0 until BHT_size) {
      val is_this_bht = ex_index.result === i.U(len.W) & ex_pattern_next === j.U(bht_len.W)
      val is_this_next = lru_result === i.U(len.W)
      withReset(reset.asBool() | ((~ex_index.is_find).asBool() & is_this_next)) {
        val bht = Module(new BHT)
        bht.io.is_visited := io.ex_success
        if (j == 0){
          bht.io.en_visit := Mux(ex_index.is_find, is_this_bht, is_this_next & ~io.ex_success)
        }else if (j == 1){
          bht.io.en_visit := Mux(ex_index.is_find, is_this_bht, is_this_next & io.ex_success)
        }else{
          bht.io.en_visit := ex_index.is_find & is_this_bht
        }
        BHT_table(i)(j) := bht.io.predict
      }
    }
  }
}

object BTB extends App {
  (new ChiselStage).emitVerilog(new BTB(16, 16))
}
