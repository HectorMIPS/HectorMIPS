package com.github.hectormips.pipeline

import chisel3._

class DecoderPredictorBundle extends Bundle {
  // 执行端的执行结果
  val en_ex     : Bool = Bool()
  // 分支指令的PC值
  val ex_pc     : UInt = UInt(32.W)
  // 分支是否成功
  val ex_success: Bool = Bool()
  // 分支的目的地址
  val ex_target : UInt = UInt(32.W)
  // 是否为无条件跳转
  val ex_always_jump: UInt = UInt(32.W)

}

class PredictorFetcherBundle extends Bundle {
  // 预测是否分支成功
  val predict: Bool = Bool()
  // 成功后的结果
  val target : UInt = UInt(32.W)
}

class FetcherPredictorBundle extends Bundle {
  val pc: UInt = UInt(32.W)
}