package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class InsExecuteTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "InsExecute"

  it should "calculate" in {
    test(new InsExecute()) { c =>
      val src1: UInt = 0xcafeefacL.U
      val src2: UInt = 0xbabababaL.U
      val isrc1      = 0xcafeefacL
      val isrc2      = 0xbabababaL
      c.io.id_ex_in.alu_src1_id_ex.poke(src1)
      c.io.id_ex_in.alu_src2_id_ex.poke(src2)

      c.io.id_ex_in.alu_op_id_ex.poke(AluOp.op_add)
      c.io.ex_ms_out.alu_val_ex_ms.expect((isrc1 + isrc2).U(64.W)(31, 0))

      c.io.id_ex_in.alu_op_id_ex.poke(AluOp.op_sub)
      c.io.ex_ms_out.alu_val_ex_ms.expect((isrc1 - isrc2).U(64.W)(31, 0))


    }
  }

}
