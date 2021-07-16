package com.github.hectormips.tomasulo

import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage
import com.github.hectormips.pipeline.RegFile
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.ComponentInOperationWidth
import com.github.hectormips.tomasulo.io.CoreIO
import com.github.hectormips.tomasulo.qi.RegQi
import com.github.hectormips.tomasulo.rob.{Rob, RobInsIn}
import com.github.hectormips.tomasulo.station.{Station, StationIn}

class Core(config: Config) extends Module {
  val io: CoreIO = IO(new CoreIO(config))

  val regfile: RegFile = Module(new RegFile(7))
  val hilo: UInt = RegInit(UInt(64.W), 0.U)

  withReset(reset.asBool() || io.clear) {
    val station_in: StationIn = Wire(new StationIn(config))
    val station: Station = Module(new Station(config))

    val rob_ins_in: RobInsIn = Wire(new RobInsIn)
    val rob: Rob = Module(new Rob(config))
    val qi: RegQi = Module(new RegQi(config))


    val is_jump: Bool = RegNext(next = rob.io.finished_ins_is_jump, 0.B)
    val is_jump_success: Bool = RegNext(next = rob.io.finished_ins_jump_success, 0.B)
    val pred_success: Bool = RegNext(next = rob.io.finished_ins_pred_success, 0.B)
    val target_pc: UInt = RegNext(rob.io.finished_ins_next_pc, 0.U(32.W))


    station_in.operation := io.in.bits.operation
    station_in.vj := Mux(io.in.bits.need_valA, io.in.bits.valA, Mux(qi.io.src_1_is_busy, rob.io.src_1_value, regfile.io.rdata1))
    station_in.vk := Mux(io.in.bits.need_valB, io.in.bits.valB, Mux(qi.io.src_2_is_busy, rob.io.src_2_value, regfile.io.rdata2))
    station_in.qj := qi.io.src_1_rob_target
    station_in.qk := qi.io.src_2_rob_target
    station_in.wait_qj := qi.io.src_1_is_busy && !rob.io.src_1_is_valid && !io.in.bits.need_valA
    station_in.wait_qk := qi.io.src_2_is_busy && !rob.io.src_2_is_valid && !io.in.bits.need_valB
    station_in.dest := rob.io.ins_rob_target
    station_in.pc := io.in.bits.pc
    station_in.target_pc := io.in.bits.target_pc
    station_in.writeHI := io.in.bits.writeHI
    station_in.writeLO := io.in.bits.writeLO
    station_in.readHI := io.in.bits.readHI
    station_in.readLO := io.in.bits.readLO
    station_in.predictJump := io.in.bits.predictJump

    station.io.ins_valid := io.in.valid
    station.io.ins_target := io.in.bits.station_target
    station.io.ins_in := station_in
    station.io.out.ready := 1.B

    rob_ins_in.target := io.in.bits.dest
    rob_ins_in.pc := io.in.bits.pc
    rob_ins_in.operation := io.in.bits.operation

    rob.io.ins_in := rob_ins_in
    rob.io.ins_in_valid := io.in.valid
    rob.io.src_1 := qi.io.src_1_rob_target
    rob.io.src_2 := qi.io.src_2_rob_target
    rob.io.result_in_valid := station.io.out.valid
    rob.io.result_in := station.io.out.bits

    qi.io.ins_rob_valid := io.in.ready
    qi.io.ins_in := rob_ins_in
    qi.io.rob_target := rob.io.ins_rob_target
    qi.io.src_1 := io.in.bits.srcA
    qi.io.src_2 := io.in.bits.srcB

    qi.io.finished_ins := rob.io.finished_ins
    qi.io.finished_ins_target := rob.io.finished_ins_target
    qi.io.finished_ins_valid := rob.io.finished_ins_valid

    regfile.io.raddr1 := io.in.bits.srcA
    regfile.io.raddr2 := io.in.bits.srcB


    val write_hilo: Bool = rob.io.finished_ins_writeLO || rob.io.finished_ins_writeHI || rob.io.finished_ins_writeLO

    regfile.io.we := rob.io.finished_ins_valid && !write_hilo
    regfile.io.waddr := rob.io.finished_ins_target
    regfile.io.wdata := rob.io.finished_ins_value(31, 0)


    io.debug_wb_pc := rob.io.finished_ins_pc
    io.debug_wb_rf_wen := rob.io.finished_ins_valid && !rob.io.finished_ins_target === 0.U && write_hilo
    io.debug_wb_rf_wdata := MuxCase(
      rob.io.finished_ins_value(31, 0),
      Seq(
        (rob.io.finished_ins_readIO, hilo(31, 0)),
        (rob.io.finished_ins_readHI, hilo(63, 32))
      )
    )
    io.debug_wb_rf_wnum := rob.io.finished_ins_target

    io.in.ready := station.io.ins_enable && rob.io.ins_enable

    when(rob.io.finished_ins_valid && write_hilo) {
      hilo := Mux1H(
        Seq(
          (rob.io.finished_ins_writeLO, Cat(rob.io.finished_ins_value(31, 0), hilo(31, 0))),
          (rob.io.finished_ins_writeHI, Cat(hilo(63, 32), rob.io.finished_ins_value(31, 0))),
          (rob.io.finished_ins_writeHILO, rob.io.finished_ins_value)
        )
      )
    }

    when(rob.io.finished_ins_is_jump) {
      is_jump := rob.io.finished_ins_is_jump
    }

    io.clear := is_jump && !pred_success
    io.new_pc := Mux(is_jump_success, target_pc, rob.io.finished_ins_pc + 4.U)
  }
}

object Core extends App {
  val v_content = (new ChiselStage).emitVerilog(new Core(new Config(4, 4)))
}
