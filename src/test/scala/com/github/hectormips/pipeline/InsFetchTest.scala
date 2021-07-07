package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class InsFetchTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "InsPreFetch"

  it should "prefetch the instruction" in {
    test(new InsPreFetch()) { c =>
      c.io.pc.poke(0x0c123000.U)

      c.io.id_pf_in.jump_val_id_pf(0).poke(0x0a123000.U)
      c.io.id_pf_in.jump_val_id_pf(1).poke(0x0b123000.U)
      c.io.regfile_read1.poke(0x0d123000.U)
      c.io.id_pf_in.jump_sel_id_pf.poke(InsJumpSel.delay_slot_pc)
      c.io.id_pf_in.bus_valid.poke(1.B)

      c.io.next_pc.expect(0x0c123004.U)
      c.io.ins_ram_addr.expect(0x0c123004.U)
      c.io.ins_ram_en.expect(1.B)

      c.io.id_pf_in.jump_sel_id_pf.poke(InsJumpSel.pc_add_offset)
      c.io.next_pc.expect(0x0a123000.U)

      c.io.id_pf_in.jump_sel_id_pf.poke(InsJumpSel.pc_cat_instr_index)
      c.io.next_pc.expect(0x0b123000.U)

      c.io.id_pf_in.jump_sel_id_pf.poke(InsJumpSel.regfile_read1)
      c.io.next_pc.expect(0x0d123000.U)
    }
  }

  behavior of "InsFetch"

  it should "fetch the async RAM data" in {
    test(new InsSufFetch()) { c =>
      c.io.ins_ram_data.poke(0x00aabbcc.U)
      c.io.if_id_out.ins_if_id.expect(0x00aabbcc.U)
    }
  }


}
