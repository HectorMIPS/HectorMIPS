package com.github.hectormips.tomasulo.qi

import chisel3._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.rob.RobInsIn

// 这是寄存器站， 用于存储寄存器重命名信息, 以及判断是否发生了RAW冲突
class RegQi(config: Config) extends Module {
  val io: RegQiIO = IO(new RegQiIO)
  // 寄存器状态表
  val qi_data: Mem[UInt] = Mem(32, UInt(config.rob_width.W))
  val qi_data_busy: Vec[Bool] = VecInit(Seq.fill(32)(0.B))

  class RegQiIO extends Bundle {
    // 指令进入ROB
    // ins 是否可以进入ROB
    val ins_rob_valid: Bool = Input(Bool())
    val ins_in: RobInsIn = Input(new RobInsIn)
    // 指令进入ROB的索引
    val rob_target: UInt = Input(UInt(config.rob_width.W))

    // 指令从ROB提交
    val finished_ins: UInt = Input(UInt(config.rob_width.W))
    val finished_ins_valid: Bool = Input(Bool())
    val finished_ins_target: UInt = Input(UInt(5.W))


    // 从RegQI里面查询
    val src_1: UInt = Input(UInt(5.W))
    val src_1_is_busy: Bool = Output(Bool())
    val src_1_rob_target: UInt = Output(UInt(config.rob_width.W))

    val src_2: UInt = Input(UInt(5.W))
    val src_2_is_busy: Bool = Output(Bool())
    val src_2_rob_target: UInt = Output(UInt(config.rob_width.W))
  }

  when(io.ins_rob_valid) {
    qi_data(io.ins_in.target) := io.rob_target
    qi_data_busy(io.ins_in.target) := 1.B
  }

  when(io.finished_ins_valid && qi_data(io.finished_ins_target) === io.finished_ins && !(io.ins_rob_valid && io.finished_ins_target === io.ins_in.target)) {
    qi_data_busy(io.finished_ins_target) := 0.B
  }

  io.src_1_is_busy := qi_data_busy(io.src_1)
  io.src_1_rob_target := qi_data(io.src_1)

  io.src_2_is_busy := qi_data_busy(io.src_2)
  io.src_2_rob_target := qi_data(io.src_2)
}
