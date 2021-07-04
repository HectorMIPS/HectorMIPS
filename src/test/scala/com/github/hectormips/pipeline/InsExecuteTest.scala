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
      val isrc1 = 0xcafeefacL
      val isrc2 = 0xbabababaL
      c.io.alu_src1_sel.poke(AluSrc1Sel.regfile_read1)
      c.io.alu_src2_sel.poke(AluSrc2Sel.regfile_read2)
      c.io.regfile_read1.poke(src1)
      c.io.regfile_read2.poke(src2)

      c.io.alu_op.poke(AluOp.op_add)
      c.io.alu_out.expect((isrc1 + isrc2).U(64.W)(31, 0))

      c.io.alu_op.poke(AluOp.op_sub)
      c.io.alu_out.expect((isrc1 - isrc2).U(64.W)(31, 0))

    }
  }

}
