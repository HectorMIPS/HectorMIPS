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
      c.io.if_id_in.pc_delay_slot_if_id.poke(0.U)
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
      c.io.if_id_in.pc_delay_slot_if_id.poke(0.U)
      c.clock.step()
      c.io.id_ex_out.alu_op_id_ex.expect(AluOp.op_add)
      c.io.id_ex_out.regfile_we_id_ex.expect(1.B)
      c.io.id_ex_out.regfile_waddr_sel_id_ex.expect(RegFileWAddrSel.const_31)
      c.io.id_ex_out.inst_rt_id_ex.expect(jal_ins(20, 16))

      // JAL 0x3F1A884
      val jal_ins2 = 0x0ff1a884.U
      c.io.if_id_in.ins_if_id.poke(jal_ins2)
      c.io.if_id_in.pc_delay_slot_if_id.poke(0xbfc00000L.U)
      c.clock.step()
      c.io.id_ex_out.alu_op_id_ex.expect(AluOp.op_add)
      c.io.id_ex_out.regfile_we_id_ex.expect(1.B)
      c.io.id_pf_out.jump_val_id_pf(1).expect("b10111111110001101010001000010000".U)

      val bne = 0x16400002.U
      c.io.if_id_in.ins_if_id.poke(bne)
      c.io.if_id_in.pc_delay_slot_if_id.poke(0xbfc00000L.U)
      c.io.regfile_read1.poke(2.U)
      c.io.regfile_read2.poke(0.U)
      c.io.id_pf_out.jump_sel_id_pf.expect(InsJumpSel.pc_add_offset)
      c.io.id_pf_out.jump_val_id_pf(0).expect(0xbfc00008L.U)

      val sll = 0x00104e00.U
      c.io.if_id_in.ins_if_id.poke(sll)
      c.io.id_ex_out.alu_src1_id_ex.expect(0x18.U)

      val sw = 0xaca566a8L.U
      c.io.if_id_in.ins_if_id.poke(sw)
      c.io.regfile_read1.poke(0x10.U)
      c.io.regfile_read2.poke(0x20.U)
      c.io.id_ex_out.alu_op_id_ex.expect(AluOp.op_add)
      c.io.id_ex_out.alu_src1_id_ex.expect(0x10.U)
      c.io.id_ex_out.alu_src2_id_ex.expect(0x66a8.U)

      val lw = 0x8d0266a8L.U
      c.io.if_id_in.ins_if_id.poke(lw)
      c.io.regfile_read1.poke(0x10.U)
      c.io.regfile_read2.poke(0x20.U)
      c.io.id_ex_out.alu_op_id_ex.expect(AluOp.op_add)
      c.io.id_ex_out.alu_src1_id_ex.expect(0x10.U)
      c.io.id_ex_out.alu_src2_id_ex.expect(0x66a8.U)
    }
  }
}
