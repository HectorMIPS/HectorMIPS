package com.github.hectormips.tomasulo.ex_component

import Chisel.Cat
import chisel3._
import chisel3.util._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.operation.{AluOp, MultiplierOp}

class MultiplierComponent(config: Config) extends Component(config) {

  val multOp   : MultiplierOp.Type = MultiplierOp(io.in.bits.operation(MultiplierOp.getWidth -1, 0))
  val is_signed: Bool              = multOp === MultiplierOp.mult

  def signedExtend(is_signed: Bool, mult1: UInt): SInt = {
    Cat(Mux(is_signed, mult1(31), 0.U(1.W)), mult1).asSInt()
  }

  val val1Extend: SInt = signedExtend(is_signed, io.in.bits.valA)
  val val2Extend: SInt = signedExtend(is_signed, io.in.bits.valB)
  val multRes   : SInt = Wire(SInt(66.W))
  multRes := val1Extend * val2Extend

  io.out.bits.exceptionFlag := io.in.bits.exceptionFlag
  io.out.bits.rob_target := io.in.bits.dest
  io.out.bits.value := multRes(63, 0)

  io.in.ready := io.out.ready
  io.out.valid := io.in.valid
}
