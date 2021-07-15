package com.github.hectormips.tomasulo

import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage
import com.github.hectormips.pipeline.RegFile
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.ComponentInOperationWidth
import com.github.hectormips.tomasulo.qi.RegQi
import com.github.hectormips.tomasulo.rob.{Rob, RobInsIn}
import com.github.hectormips.tomasulo.station.{Station, StationIn}

class Core(config: Config) extends Module {
  val io: CoreIO = IO(new CoreIO)

  val station_in: StationIn = Wire(new StationIn(config))
  val station: Station = Module(new Station(config))

  val rob_ins_in: RobInsIn = Wire(new RobInsIn)
  val rob: Rob = Module(new Rob(config))
  val qi: RegQi = Module(new RegQi(config))

  val regfile: RegFile = Module(new RegFile(7))

  val hilo: UInt = RegInit(UInt(64.W), 0.U)

  val is_jump :Bool = RegInit(Bool(), 0.B)
  val pred_success: Bool = RegInit(Bool(), 0.B)

  class CoreIO extends Bundle {
    val valid: Bool = Input(Bool())




    val operation: UInt = Input(UInt(ComponentInOperationWidth.Width.W))
    val station_target: UInt = Input(UInt(config.station_size.W))
    val exception_flag: UInt = Input(UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W))

    val pc: UInt = Input(UInt(32.W))
    val srcA: UInt = Input(UInt(5.W))
    val srcB: UInt = Input(UInt(5.W))

    val need_valA: Bool = Input(Bool())
    val need_valB: Bool = Input(Bool())
    val valA: UInt = Input(UInt(32.W))
    val valB: UInt = Input(UInt(32.W))

    val target_pc: UInt = Input(UInt(32.W))
    val dest: UInt = Input(UInt(5.W))
    val A: UInt = Input(UInt(32.W))

    // HILO 标志位
    val writeHI: Bool = Input(Bool())
    val writeLO: Bool = Input(Bool())
    val readHI: Bool = Input(Bool())
    val readLO: Bool = Input(Bool())



    val ready: Bool = Output(Bool())

    val clear: Bool = Output(Bool())
    val new_pc: UInt = Output(UInt(32.W))

    val debug_wb_pc: UInt = Output(UInt(32.W))
    val debug_wb_rf_wen: UInt = Output(UInt(4.W))
    val debug_wb_rf_wnum: UInt = Output(UInt(5.W))
    val debug_wb_rf_wdata: UInt = Output(UInt(32.W))
  }

  station_in.operation := io.operation
  station_in.vj := Mux(qi.io.src_1_is_busy, rob.io.src_1_value, regfile.io.rdata1)
  station_in.vk := Mux(qi.io.src_2_is_busy, rob.io.src_2_value, regfile.io.rdata2)
  station_in.qj := qi.io.src_1_rob_target
  station_in.qk := qi.io.src_2_rob_target
  station_in.wait_qj := qi.io.src_1_is_busy && !rob.io.src_1_is_valid
  station_in.wait_qk := qi.io.src_2_is_busy && !rob.io.src_2_is_valid
  station_in.dest := rob.io.ins_rob_target
  station_in.A := io.A

  station.io.ins_valid := io.valid
  station.io.ins_target := io.station_target
  station.io.ins_in := station_in
  station.io.out.ready := 1.B

  rob_ins_in.target := io.dest
  rob_ins_in.pc := io.pc
  rob_ins_in.operation := io.operation

  rob.io.ins_in := rob_ins_in
  rob.io.ins_in_valid := io.valid
  rob.io.src_1 := qi.io.src_1_rob_target
  rob.io.src_2 := qi.io.src_2_rob_target
  rob.io.result_in_valid := station.io.out.valid
  rob.io.result_in := station.io.out.bits

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


  regfile.io.we := rob.io.finished_ins_valid && !rob.io.finished_ins_writeHILO
  regfile.io.waddr := rob.io.finished_ins_target
  regfile.io.wdata := rob.io.finished_ins_value(31, 0)


  io.debug_wb_pc := rob.io.finished_ins_pc
  io.debug_wb_rf_wen := rob.io.finished_ins_valid && !rob.io.finished_ins_target === 0.U
  io.debug_wb_rf_wdata := MuxCase(
    rob.io.finished_ins_value(31, 0),
    Seq(
      (rob.io.finished_ins_readIO, hilo(31, 0)),
      (rob.io.finished_ins_readHI, hilo(63, 32))
    )
  )
  io.debug_wb_rf_wnum := rob.io.finished_ins_target

  io.ready := station.io.ins_enable && rob.io.ins_enable

  when(rob.io.finished_ins_valid && rob.io.finished_ins_writeHILO) {
    hilo := rob.io.finished_ins_value
  }
}

object Core extends App {
  val v_content = (new ChiselStage).emitVerilog(new Core(new Config(4, 4)))
}
