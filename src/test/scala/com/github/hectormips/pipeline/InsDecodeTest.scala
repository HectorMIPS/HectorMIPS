package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class InsDecodeTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "InsDecode"

  it should "decode" in {
    test(new InsDecode()) { c =>
      val addu_ins = "b00000000001000101000000000100001".U

      c.io.if_id_in.ins_if_id.poke(addu_ins)
      c.io.if_id_in.pc_if_id.poke(0.U)
      c.clock.step()

      c.io.id_ex_out.alu_op_id_ex.expect(AluOp.op_add)
      c.io.id_ex_out.regfile_wsrc_sel_id_ex.expect(0.B)
      c.io.id_ex_out.regfile_we_id_ex.expect(1.B)
      c.io.id_ex_out.regfile_waddr_sel_id_ex.expect(RegFileWAddrSel.inst_rd)
      c.io.ins_opcode.expect(addu_ins(31, 26))
      c.io.id_ex_out.inst_rs_id_ex.expect(addu_ins(25, 21))
      c.io.id_ex_out.inst_rt_id_ex.expect(addu_ins(20, 16))
      c.io.id_ex_out.inst_rd_id_ex.expect(addu_ins(15, 11))


      val jal_ins = "b00001111111000001010101010101010".U
      c.io.if_id_in.ins_if_id.poke(jal_ins)
      c.io.if_id_in.pc_if_id.poke(0.U)
      c.clock.step()
      c.io.id_ex_out.alu_src1_sel_id_ex.expect(AluSrc1Sel.pc)
      c.io.id_ex_out.alu_src2_sel_id_ex.expect(AluSrc2Sel.const_31)
      c.io.id_ex_out.alu_op_id_ex.expect(AluOp.op_add)
      c.io.id_ex_out.regfile_we_id_ex.expect(1.B)
      c.io.id_ex_out.regfile_waddr_sel_id_ex.expect(RegFileWAddrSel.const_31)
      c.io.id_ex_out.inst_rt_id_ex.expect(jal_ins(20, 16))
    }
  }


}
