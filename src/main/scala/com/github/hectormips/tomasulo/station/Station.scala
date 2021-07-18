package com.github.hectormips.tomasulo.station

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._
import com.github.hectormips.tomasulo.ex_component.{AluComponent, Component, DividerComponent, JumpComponent, MultiplierComponent}
import com.github.hectormips.tomasulo.rob.RobResultIn
import com.github.hectormips.tomasulo.{CDB, Config, Core}

// 保留站
class Station(config: Config) extends Module {
  val io: StationIO = IO(new StationIO)
  val component: Seq[Component] = Seq(Module(new AluComponent(config)), Module(new MultiplierComponent(config)), Module(new JumpComponent(config)), Module(new AluComponent(config)))
  val cdb: CDB = Module(new CDB(config, config.station_size))

  val station_data: Mem[StationData] = Mem(config.station_size, new StationData(config))
  val station_busy: Vec[Bool] = RegInit(VecInit(Seq.fill(config.station_size)(0.B)))

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
    val valid: Bool = station_busy(i) && !station_data(i).wait_qj && !station_data(i).wait_qk
    station_valid(i) := component(i).io.in.ready & valid
    component(i).io.in.valid := valid
    component(i).io.in.bits := station_data(i).toComponent
    component(i).io.out.ready := cdb.io.in(i).ready
    cdb.io.in(i).bits := component(i).io.out.bits
    cdb.io.in(i).valid := component(i).io.out.valid
  }

  io.out.bits := cdb.io.out.bits
  io.out.valid := cdb.io.out.valid
  cdb.io.out.ready := io.out.ready


  io.ins_enable := (!station_busy(io.ins_target) || station_valid(io.ins_target))

  for (i <- 0 until config.station_size) {
    when(station_valid(i) && !i.U === io.ins_target) {
      station_busy(i) := 0.U
    }
  }

  when(io.ins_valid && io.ins_enable) {
    val station_item = station_data(io.ins_target)
    station_busy(io.ins_target) := 1.B
    station_item.ins := io.ins_in.operation
    station_item.vj := io.ins_in.vj
    station_item.vk := io.ins_in.vk
    station_item.qj := io.ins_in.qj
    station_item.qk := io.ins_in.qk

    station_item.wait_qj := io.ins_in.wait_qj
    station_item.wait_qk := io.ins_in.wait_qk

    station_item.dest := io.ins_in.dest

    station_item.pc := io.ins_in.pc
    station_item.target_pc := io.ins_in.target_pc

    station_item.writeHI := io.ins_in.writeHI
    station_item.writeLO := io.ins_in.writeLO
    station_item.readHI := io.ins_in.readHI
    station_item.readLO := io.ins_in.readLO

    station_item.predictJump := io.ins_in.predictJump
  }

  when(io.out.valid) {
    for (i <- 0 until config.station_size) {
      when(station_data(i).wait_qj && station_data(i).qj === io.out.bits.rob_target) {
        station_data(i).wait_qj := 0.B
        station_data(i).vj := io.out.bits.value(31, 0)
      }
      when(station_data(i).wait_qk && station_data(i).qk === io.out.bits.rob_target) {
        station_data(i).wait_qk := 0.B
        station_data(i).vk := io.out.bits.value(31, 0)
      }
    }
  }
}

object Station extends App {
  val v_content = (new ChiselStage).emitVerilog(new Station(new Config(4, 4)))
}
