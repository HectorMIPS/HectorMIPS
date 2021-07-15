package com.github.hectormips.tomasulo.station

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._
import com.github.hectormips.tomasulo.ex_component.{AluComponent, Component, DividerComponent, MultiplierComponent}
import com.github.hectormips.tomasulo.rob.RobResultIn
import com.github.hectormips.tomasulo.{CDB, Config, Core}

// 保留站
class Station(config: Config) extends Module {
  val io: StationIO = IO(new StationIO)
  val component: Seq[Component] = Seq(Module(new AluComponent(config)), Module(new MultiplierComponent(config)), Module(new AluComponent(config)), Module(new AluComponent(config)))
  val cdb: CDB = Module(new CDB(config, config.station_size))
  val station_data: Mem[StationData] = Mem(config.station_size, new StationData(config))
  val station_valid: Vec[Bool] = Wire(Vec(config.station_size, Bool()))

  class StationIO extends Bundle {
    // 指令进入保留站
    val ins_valid: Bool = Input(Bool())
    // 指令的索引
    val ins_target: UInt = Input(UInt(config.station_width.W))
    val ins_in: StationIn = Input(new StationIn(config))
    // 指令是否能够进入保留站
    val ins_enable: Bool = Output(Bool())

//    val rob_write_valid: Bool = Input(Bool())
//    val rob_write: RobResultIn = Input(new RobResultIn(config))

    val out: DecoupledIO[RobResultIn] = DecoupledIO(new RobResultIn(config))
  }


  for (i <- 0 until config.station_size) {
    val valid: Bool = station_data(i).busy && station_data(i).qj === 0.U && station_data(i).qk === 0.U
    station_valid(i) := component(i).io.in.ready
    component(i).io.in.valid := valid
    component(i).io.in.bits := station_data(i).toComponent
    component(i).io.out.ready := cdb.io.in(i).ready
    cdb.io.in(i).bits := component(i).io.out.bits
    cdb.io.in(i).valid := component(i).io.out.valid
  }

  io.out.bits := cdb.io.out.bits
  io.out.valid := cdb.io.out.valid
  cdb.io.out.ready := io.out.ready


  io.ins_enable := (!station_data(io.ins_target).busy || station_valid(io.ins_target))

  when( io.ins_valid && io.ins_enable) {
    val station_item = station_data(io.ins_target)
    station_item.ins := io.ins_in.operation
    station_item.busy := 1.B
    station_item.qj := io.ins_in.qj
    station_item.qk := io.ins_in.qk
    station_item.vj := io.ins_in.vj
    station_item.vk := io.ins_in.vk
    station_item.wait_qj := io.ins_in.wait_qj
    station_item.wait_qk := io.ins_in.wait_qk
    station_item.dest := io.ins_in.dest
    station_item.A := io.ins_in.A
  }.elsewhen(!io.ins_valid){
    for (i <- 0 until config.station_size) {
      when(component(i).io.in.ready){
        station_data(i).busy := 0.B
      }
    }
  }

  when(io.out.valid) {
    for (i <- 0 until config.station_size) {
      when( station_data(i).wait_qj && station_data(i).qj === io.out.bits.rob_target) {
        station_data(i).qj := 0.U
        station_data(i).vj := io.out.bits.value
      }
      when(station_data(i).wait_qk && station_data(i).qk === io.out.bits.rob_target) {
        station_data(i).qk := 0.U
        station_data(i).vk := io.out.bits.value
      }
    }
  }
}
object Station extends App {
  val v_content = (new ChiselStage).emitVerilog(new Station(new Config(4,4)))
}
