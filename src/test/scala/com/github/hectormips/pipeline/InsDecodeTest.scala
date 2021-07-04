package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class InsDecodeTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "InsDecode"

  it should "decode" in {
    test(new InsDecode()) { c =>
      val addu_ins = "b00000000001000101000000000100001".U

      c.io.raw_ins.poke(addu_ins)
      c.io.pc.poke(0.U)
      c.clock.step()
      c.io.alu_src1(0).expect(1.B)
      c.io.alu_src2(0).expect(1.B)
      c.io.alu_op.expect(AluOp.op_add)
      c.io.regfile_we.expect(1.B)
      c.io.regfile_addr(0).expect(1.B)
      c.io.ins_opcode.expect(addu_ins(31, 26))
      c.io.ins_rs.expect(addu_ins(25, 21))
      c.io.ins_rt.expect(addu_ins(20, 16))
      c.io.ins_rd.expect(addu_ins(15, 11))
      c.io.ins_sa.expect(addu_ins(10, 6))
      c.io.ins_imm.expect(addu_ins(15, 0))


      val jal_ins = "b00001111111000001010101010101010".U
      c.io.raw_ins.poke(jal_ins)
      c.io.pc.poke(0.U)
      c.clock.step()
      c.io.alu_src1(1).expect(1.B)
      c.io.alu_src2(2).expect(1.B)
      c.io.alu_op.expect(AluOp.op_add)
      c.io.regfile_we.expect(1.B)
      c.io.regfile_addr(2).expect(1.B)
      c.io.ins_rt.expect(jal_ins(20, 16))
    }
  }


}
