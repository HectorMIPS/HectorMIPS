package com.github.hectormips.tomasulo.station

import chisel3._
import chisel3.util.{isPow2, log2Ceil}
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.rob.RobResultIn

// 保留站
class Station(config:Config) extends Module {
  class StationIO extends Bundle {
    // 指令进入保留站
    val ins_valid: Bool = Input(Bool())
    // 指令的索引
    val ins_target: UInt = Input(UInt(config.station_width.W))
    val ins_in: StationIn = Input(new StationIn(config))
    // 指令是否能够进入保留站
    val ins_enable: Bool = Output(Bool())

    val rob_write_valid: Bool = Input(Bool())
    val rob_write: RobResultIn = Input(new RobResultIn(config))
  }

  val io: StationIO = IO(new StationIO)

  val station_data: Mem[StationData] = Mem(config.station_size, new StationData(config))
  val station_valid: Vec[Bool] = Wire(Vec(config.station_size, Bool))

  for (i <- 0 until config.station_size) {
    station_valid(i) := station_data(i).busy && !station_data(i).qj.andR() && !station_data(i).qk.andR()
  }

  io.ins_enable := io.ins_valid && (!station_data(io.ins_target).busy || station_valid(io.ins_target))

  when(io.ins_enable) {
    val station_item = station_data(io.ins_target)
    station_item.busy := 1.B
    station_item.qj := io.ins_in.qj
    station_item.qk := io.ins_in.qk
    station_item.vj := io.ins_in.vj
    station_item.vk := io.ins_in.vk
    station_item.dest := io.ins_in.dest
    station_item.A := io.ins_in.A
  }

  when (io.rob_write_valid){
    for (i <- 0 until config.station_size) {
      val item = station_data(i)
      when(item.qj === io.rob_write.rob_target){
        item.qj := 0.U
        item.vj := io.rob_write.value
      }
      when(item.qk === io.rob_write.rob_target){
        item.qk := 0.U
        item.vk := io.rob_write.value
      }
    }
  }
}
