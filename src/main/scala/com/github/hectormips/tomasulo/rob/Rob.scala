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
    val finished_ins_pc: UInt = Output(UInt(32.W))
    val finished_ins_valid: Bool = Output(Bool())
    val finished_ins_target: UInt = Output(UInt(5.W))
    val finished_ins_value: UInt = Output(UInt(64.W))

    // HILO 相关
    val finished_ins_writeHILO: Bool = Output(Bool())
    val finished_ins_writeHI: Bool = Output(Bool())
    val finished_ins_writeLO: Bool = Output(Bool())
    val finished_ins_readHI: Bool = Output(Bool())
    val finished_ins_readIO: Bool = Output(Bool())
    // JUMP 相关
    val finished_ins_is_jump: Bool = Output(Bool())
    val finished_ins_pred_success: Bool = Output(Bool())
    val finished_ins_jump_success: Bool = Output(Bool())
    val finished_ins_next_pc: UInt = Output(UInt(32.W))

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
    rob_data(target).busy := 1.B
    rob_data(target).ins := in.operation
    rob_data(target).pc := in.pc
    rob_data(target).state := RobState.process
    rob_data(target).target := in.target
  }

  val io: RobIO = IO(new RobIO)

  // 指令可以写入ROB
  val enable_1: Bool = !rob_data(end).busy
  io.ins_enable := enable_1
  io.ins_rob_target := end

  // 写入ROB结果
  when(enable_1 && io.ins_in_valid) {
    add_ins(end, io.ins_in)
    end := end + 1.U
  }

  def finish_ins(in: RobResultIn) {
    rob_data(in.rob_target).value := in.value
    rob_data(in.rob_target).state := RobState.write

    rob_data(in.rob_target).writeHILO := in.writeHILO
    rob_data(in.rob_target).readHI := in.readHI
    rob_data(in.rob_target).readLO := in.readLO
    rob_data(in.rob_target).writeLO := in.writeLO
    rob_data(in.rob_target).writeHI := in.writeHI

    rob_data(in.rob_target).is_jump := in.is_jump
    rob_data(in.rob_target).jump_success := in.jump_success
    rob_data(in.rob_target).pred_success := in.pred_success
    rob_data(in.rob_target).next_pc := in.next_pc
  }

  when(io.result_in_valid){
    finish_ins(io.result_in)
  }

  io.finished_ins := start
  io.finished_ins_valid := rob_data(start).busy && rob_data(start).state === RobState.write
  io.finished_ins_target := rob_data(start).target
  io.finished_ins_value := rob_data(start).value
  io.finished_ins_pc := rob_data(start).pc

  io.finished_ins_writeHILO := rob_data(start).writeHILO
  io.finished_ins_readIO := rob_data(start).readLO
  io.finished_ins_readHI := rob_data(start).readHI
  io.finished_ins_writeLO := rob_data(start).writeLO
  io.finished_ins_writeHI := rob_data(start).writeHI
  io.finished_ins_pred_success := rob_data(start).pred_success
  io.finished_ins_jump_success := rob_data(start).jump_success
  io.finished_ins_is_jump := rob_data(start).is_jump
  io.finished_ins_next_pc := rob_data(start).next_pc

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
