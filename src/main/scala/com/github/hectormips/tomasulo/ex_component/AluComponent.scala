package com.github.hectormips.tomasulo.ex_component

import chisel3._
import chisel3.util._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.operation.AluOp

class AluComponent(config: Config) extends Component(config) {
  def doArithmetic(aluOp: AluOp.Type, valA: UInt, valB: UInt): (UInt, Bool) = {
    val aluRes      : UInt = Wire(UInt(32.W))
    val valAExtended: UInt = Cat(valA(31), valA)
    val valBExtended: UInt = Cat(valB(31), valB)
    val overflowFlag: Bool = Wire(Bool())
    overflowFlag := 0.B
    aluRes := 0.U
    switch(aluOp) {
      is(AluOp.op_add) {
        val aluOutExtend: UInt = valAExtended + valBExtended
        overflowFlag := aluOutExtend(32) ^ aluOutExtend(31)
        aluRes := aluOutExtend(31, 0)
      }
      is(AluOp.op_sub) {
        val aluOutExtend: UInt = valAExtended - valBExtended
        overflowFlag := aluOutExtend(32) ^ aluOutExtend(31)
        aluRes := aluOutExtend(31, 0)
      }
      is(AluOp.op_slt) {
        aluRes := valA.asSInt() < valB.asSInt()
      }
      is(AluOp.op_sltu) {
        aluRes := valA < valB
      }
      is(AluOp.op_and) {
        aluRes := valA & valB
      }
      is(AluOp.op_nor) {
        aluRes := ~(valA | valB)
      }
      is(AluOp.op_or) {
        aluRes := valA | valB
      }
      is(AluOp.op_xor) {
        aluRes := valA ^ valB
      }
      is(AluOp.op_sll) {
        aluRes := valB << valA(4, 0) // 由sa指定位移数（src1）
      }
      is(AluOp.op_srl) {
        aluRes := valB >> valA(4, 0) // 由sa指定位移位数（src1）
      }
      is(AluOp.op_sra) {
        aluRes := (valB.asSInt() >> valA(4, 0)).asUInt()
      }
      is(AluOp.op_lui) {
        aluRes := valB << 16.U
      }
    }
    Tuple2(aluRes, overflowFlag)
  }

  val (aluRes, overflowFlag) = doArithmetic(AluOp(io.in.bits.operation(AluOp.getWidth - 1, 0)),
    io.in.bits.valA, io.in.bits.valB)
  io.out.bits.exceptionFlag := io.in.bits.exceptionFlag |
    Mux(overflowFlag, ExceptionConst.EXCEPTION_INT_OVERFLOW, 0.U)
  io.out.bits.value := Cat(0.U(32.W), aluRes)
  io.out.bits.rob_target := io.in.bits.dest

  io.in.ready := io.out.ready
  io.out.valid := io.in.valid

}
