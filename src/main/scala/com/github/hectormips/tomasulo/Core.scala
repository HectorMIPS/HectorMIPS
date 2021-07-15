package com.github.hectormips.tomasulo

import chisel3._
import com.github.hectormips.pipeline.RegFile
import com.github.hectormips.tomasulo.qi.RegQi
import com.github.hectormips.tomasulo.rob.{Rob, RobInsIn}
import com.github.hectormips.tomasulo.station.{Station, StationIn}

class Core(config:Config) extends Module{
  class CoreIO extends Bundle {
    val valid: Bool = Input(Bool())

    val ins: UInt = Input(UInt(32.W))
    val pc: UInt = Input(UInt(32.W))
    val srcA: UInt = Input(UInt(5.W))
    val srcB: UInt = Input(UInt(5.W))
    val dest: UInt = Input(UInt(5.W))
    val A: UInt = Input(UInt(32.W))
    val station_target: UInt = Input(UInt(config.station_size.W))

    val ready: Bool = Output(Bool())
  }
  val io: CoreIO = IO(new CoreIO)


  val station_in :StationIn = Wire(new StationIn(config))
  val station: Station = Module(new Station(config))

  val rob_ins_in : RobInsIn = Wire(new RobInsIn)
  val rob: Rob = Module(new Rob(config))
  val qi: RegQi = Module(new RegQi(config))

  val regfile: RegFile = Module(new RegFile(0))

  station_in.ins := io.ins
  station_in.vj := Mux(qi.io.src_1_is_busy, rob.io.src_1_value, regfile.io.rdata1)
  station_in.vk := Mux(qi.io.src_2_is_busy, rob.io.src_2_value, regfile.io.rdata2)
  station_in.qj := Mux(qi.io.src_1_is_busy && !rob.io.src_1_is_valid, qi.io.src_1_rob_target, 0.U)
  station_in.qk := Mux(qi.io.src_2_is_busy && !rob.io.src_2_is_valid, qi.io.src_2_rob_target, 0.U)
  station_in.dest := rob.io.ins_rob_target
  station_in.A := io.A

  station.io.ins_valid := io.valid
  station.io.ins_target := io.station_target
  station.io.ins_in := station_in

  rob_ins_in.target := io.dest
  rob_ins_in.pc := io.pc
  rob_ins_in.ins := io.ins

  rob.io.ins_in := rob_ins_in
  rob.io.ins_in_valid := io.valid
  rob.io.src_1 := qi.io.src_1_rob_target
  rob.io.src_2 := qi.io.src_2_rob_target

  qi.io.ins_rob_valid := io.ready
  qi.io.ins_in := rob_ins_in
  qi.io.rob_target := rob.io.ins_rob_target
  qi.io.src_1 := io.srcA
  qi.io.src_2 := io.srcB
  qi.io.finished_ins := rob.io.finished_ins
  qi.io.finished_ins_target := rob.io.finished_ins_target
  qi.io.finished_ins_valid := rob.io.finished_ins_valid

  regfile.io.raddr1 := io.srcA
  regfile.io.raddr2 := io.srcB

  io.ready := station.io.ins_enable && rob.io.ins_enable
}
