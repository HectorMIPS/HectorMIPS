package com.github.hectormips.tomasulo.rob

import chisel3._
import chisel3.util._
import com.github.hectormips.tomasulo.Config

class Rob(config: Config) extends Module {

  class RobIO extends Bundle {
    // 指令进入ROB
    val ins_in: RobInsIn = Input(new RobInsIn)
    val ins_in_valid: Bool = Input(Bool())

    // 指令是否可以进入ROB
    val ins_enable: Bool = Output(Bool())
    val ins_rob_target: UInt = Output(UInt(config.rob_width.W))

    // 指令写回到ROB
    val result_in: RobResultIn = Input(new RobResultIn(config))
    val result_in_valid: Bool = Input(Bool())

    // 已经提交的指令
    val finished_ins: UInt = Output(UInt(config.rob_width.W))
    val finished_ins_valid: Bool = Output(Bool())
    val finished_ins_target: UInt = Output(UInt(5.W))
    val finished_ins_value: UInt = Output(UInt(32.W))

    // 从ROB里面查询
    val src_1: UInt = Input(UInt(config.rob_width.W))
    val src_1_is_valid: Bool = Output(Bool())
    val src_1_value: UInt = Output(UInt(32.W))

    val src_2: UInt = Input(UInt(config.rob_width.W))
    val src_2_is_valid: Bool = Output(Bool())
    val src_2_value: UInt = Output(UInt(32.W))
  }

  // FIFO 记录
  val start: UInt = RegInit(UInt(config.rob_width.W), 0.U)
  val end: UInt = RegInit(UInt(config.rob_width.W), 0.U)

  // rob表
  val rob_data: Mem[RobData] = Mem(config.rob_size, new RobData)

  def add_ins(target: UInt, in: RobInsIn) {
    val item: RobData = rob_data(target)
    item.busy := 1.B
    item.ins := in.ins
    item.pc := in.pc
    item.state := RobState.process
    item.target := in.target
  }

  val io: RobIO = IO(new RobIO)

  // 指令可以写入ROB
  val enable_1: Bool = io.ins_in_valid && !rob_data(end).busy
  io.ins_enable := enable_1
  io.ins_rob_target := end

  // 写入ROB结果
  when(enable_1) {
    add_ins(end, io.ins_in)
    end := end + 1.U
  }

  def finish_ins(in: RobResultIn) {
    when(in.rob_target.orR()) {
      val item: RobData = rob_data(in.rob_target)
      item.value := in.value
      item.state := RobState.write
    }
  }

  when(io.result_in_valid){
    finish_ins(io.result_in)
  }

  io.finished_ins := start
  io.finished_ins_valid := rob_data(start).busy && rob_data(start).state === RobState.write
  io.finished_ins_target := rob_data(start).target
  io.finished_ins_value := rob_data(start).value

  when(rob_data(start).busy) {
    when(rob_data(start).state === RobState.write) {
      rob_data(start).busy := 0.B
      rob_data(start).state := RobState.confirm
      start := start + 1.U
    }
  }

  io.src_1_value := rob_data(io.src_1).value
  io.src_1_is_valid := rob_data(io.src_1).state === RobState.write
  io.src_2_value := rob_data(io.src_2).value
  io.src_2_is_valid := rob_data(io.src_2).state === RobState.write
}
