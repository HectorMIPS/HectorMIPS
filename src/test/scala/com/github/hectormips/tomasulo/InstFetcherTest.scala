package com.github.hectormips.tomasulo

import chisel3._
import chiseltest._
import org.scalatest._


class InstFetcherTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "MemoryComponent"

  it should "fetch memory content correctly" in {
    test(new InstFetcher(0xbfc00000L)) { c =>
      c.io.jumpFromCommit.jumpEn.poke(0.B)
      c.io.jumpFromExecute.jumpEn.poke(0.B)
      c.clock.step()
      c.icacheReadIO.valid.expect(1.B)
      c.icacheReadIO.addr.expect(0xbfc00000L.U)
      c.clock.step(2)
      c.icacheReadIO.addr_ok.poke(1.B)
      c.clock.step()
      c.icacheReadIO.valid.expect(0.B)
      c.icacheReadIO.rdata.poke(114514.U)
      c.clock.step(2)
      c.icacheReadIO.data_ok.poke(1.B)
      c.io.out.valid.expect(0.B)
      c.clock.step()
      c.io.out.valid.expect(1.B)
      c.io.out.bits.inst.expect(114514.U)
      c.io.out.bits.pc.expect(0xbfc00000L.U)

    }
  }

}
