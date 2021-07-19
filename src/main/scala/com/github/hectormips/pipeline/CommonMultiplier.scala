package com.github.hectormips.pipeline

import Chisel.{Cat, RegEnable}
import chisel3._
import chisel3.experimental.ChiselEnum
import com.github.hectormips.ip.MultiplierIP

object MultiplierState extends ChiselEnum {
  val waiting_for_input, calculating = Value
}

class CommonMultiplierBundle extends Bundle {
  val mult1         : UInt                 = Input(UInt(32.W))
  val mult2         : UInt                 = Input(UInt(32.W))
  val is_signed     : Bool                 = Input(Bool())
  val mult_res_63_32: UInt                 = Output(UInt(32.W))
  val mult_res_31_0 : UInt                 = Output(UInt(32.W))
  val state         : MultiplierState.Type = Output(MultiplierState())
  val req           : Bool                 = Input(Bool())
  val res_valid     : Bool                 = Output(Bool())
  val flush         : Bool                 = Input(Bool())
}

class CommonMultiplier extends Module {
  val io           : CommonMultiplierBundle = IO(new CommonMultiplierBundle)
  val multiplier_ip: MultiplierIP           = Module(new MultiplierIP)

  val multiplier_stages: Int                  = multiplier_ip.pipeline_stages
  val state            : MultiplierState.Type = RegInit(init = MultiplierState.waiting_for_input)
  val calc_countdown_m1: UInt                 = Wire(UInt(3.W))
  val calc_countdown   : UInt                 = RegEnable(next = calc_countdown_m1, init = multiplier_stages.U,
    enable = state === MultiplierState.calculating)
  calc_countdown_m1 := calc_countdown - 1.U
  when(!io.flush) {
    when(state === MultiplierState.waiting_for_input && io.req) {
      state := MultiplierState.calculating
      calc_countdown := multiplier_stages.U
    }.elsewhen(state === MultiplierState.calculating && calc_countdown === 1.U) {
      state := MultiplierState.waiting_for_input
    }
  }.otherwise {
    state := MultiplierState.waiting_for_input
    calc_countdown := multiplier_stages.U
  }
  io.state := state

  def signedExtend(is_signed: Bool, mult1: UInt): UInt = {
    Cat(Mux(is_signed, mult1(31), 0.U(1.W)), mult1)
  }

  val mult1_e: UInt = signedExtend(io.is_signed, io.mult1)
  val mult2_e: UInt = signedExtend(io.is_signed, io.mult2)
  multiplier_ip.io.a := mult1_e
  multiplier_ip.io.b := mult2_e
  multiplier_ip.io.clk := clock.asBool()
  val mult_res: UInt = multiplier_ip.io.p

  io.mult_res_63_32 := mult_res(63, 32)
  io.mult_res_31_0 := mult_res(31, 0)
  io.res_valid := state === MultiplierState.calculating && calc_countdown === 1.U
}
