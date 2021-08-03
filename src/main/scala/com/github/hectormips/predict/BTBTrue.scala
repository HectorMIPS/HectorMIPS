package com.github.hectormips.predict

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import com.github.hectormips.utils.Lru

class BTBTrue(size: Int) extends Module {
  require(isPow2(size))

  val predict_size: Int = 2

  val len: Int = log2Ceil(size)

  class PredictIO extends Bundle {
    // 分支预测
    val pc: UInt = Input(UInt(32.W))
    // 预测是否分支成功
    val predict: Bool = Output(Bool())
    // 成功后的结果
    val target: UInt = Output(UInt(32.W))
  }

  class BTB_IO extends Bundle {
    val predicts: Vec[PredictIO] = Vec(predict_size,new PredictIO())

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
    index := Mux1H((0 until size).map(i => (location_table(i) === pc, {
      val default = Wire(new LookUpResult)
      default.is_find := 1.B
      default.result := i.U(len.W)
      default
    })))
    index
  }

  val io: BTB_IO = IO(new BTB_IO)

  // pc表， 用于储存PC地址
  val location_table: Vec[UInt] = RegInit(VecInit(Seq.fill(size)(0.U(32.W))))

  // target表， 用于储存pc的目的地址
  val target_table: Vec[UInt] = RegInit(VecInit(Seq.fill(size)(0.U(32.W))))


  // valid表，用于记录每个pc是否可用
  val valid_table: Vec[Bool] = RegInit(VecInit(Seq.fill(size)(0.B)))
  

  for (i <- 0 until predict_size){
    // 译码段 pc查找结果
    val find_index: LookUpResult = Wire(new LookUpResult)
    find_index := get_index(io.predicts(i).pc)

    // 预测值
    io.predicts(i).target := target_table(find_index.result)
    // 预测是否成功
    io.predicts(i).predict := 1.B
  }

  // ex段 pc查找结果
  val ex_index: LookUpResult = Wire(new LookUpResult)
  ex_index := get_index(io.ex_pc)

  // lru的返回值，下一个应该替换的地址
  val lru_result: UInt = Wire(UInt(len.W))

  // LRU
  val lru: Lru = Module(new Lru(size))
  lru.io.visitor := Mux(ex_index.is_find, ex_index.result, lru_result)
  lru.io.en_visitor := io.en_ex


  lru_result := lru.io.next


  // 更新BTB
  when(io.en_ex) {
    when(ex_index.is_find) {
      target_table(ex_index.result) := io.ex_target
      valid_table(ex_index.result) := 1.B
    }.otherwise {
      location_table(lru_result) := io.ex_pc
      target_table(lru_result) := io.ex_target
      valid_table(lru_result) := 1.B
    }
  }

}

object BTBTrue extends App {
  (new ChiselStage).emitVerilog(new BTBTrue(16))
}
