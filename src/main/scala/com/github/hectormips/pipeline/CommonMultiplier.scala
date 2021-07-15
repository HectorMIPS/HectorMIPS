package com.github.hectormips.pipeline

import Chisel.Cat
import chisel3._

class CommonMultiplierBundle extends Bundle {
  val mult1         : UInt = Input(UInt(32.W))
  val mult2         : UInt = Input(UInt(32.W))
  val is_signed     : Bool = Input(Bool())
  val mult_res_63_32: UInt = Output(UInt(32.W))
  val mult_res_31_0 : UInt = Output(UInt(32.W))
}

class CommonMultiplier extends Module {
  val io: CommonMultiplierBundle = IO(new CommonMultiplierBundle)

  def signedExtend(is_signed: Bool, mult1: UInt): SInt = {
    Cat(Mux(is_signed, mult1(31), 0.U(1.W)), mult1).asSInt()
  }

  val mult1_e : SInt = signedExtend(io.is_signed, io.mult1)
  val mult2_e : SInt = signedExtend(io.is_signed, io.mult2)
  val mult_res: SInt = Wire(SInt(66.W))

  mult_res := mult1_e * mult2_e
  io.mult_res_63_32 := mult_res(63, 32)
  io.mult_res_31_0 := mult_res(31, 0)
}
