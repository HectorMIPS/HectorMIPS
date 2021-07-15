package com.github.hectormips.tomasulo.ex_component

import chisel3._
import chiseltest._
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.ex_component.operation.MemoryOp
import org.scalatest._


class MemoryComponentTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "MemoryComponent"

  it should "fetch memory content correctly" in {
    test(new MemoryComponent(new Config(4, 4))) { c =>
      c.io.in.valid.poke(1.B)
      c.io.out.ready.poke(0.B)
      c.io.in.bits.valA.poke(0x114000.U)
      c.io.in.bits.valB.poke(0x514.U)
      c.io.in.bits.dest.poke(2.U)
      c.io.in.bits.operation.poke(0x10.U)
      c.io.in.bits.exceptionFlag.poke(0.B)

      c.dcacheReadIO.addr_ok.poke(0.B)
      c.dcacheReadIO.data_ok.poke(0.B)

      c.clock.step()
      c.io.out.valid.expect(0.B)
      c.io.in.ready.expect(0.B)
      c.clock.step()
      c.dcacheReadIO.addr.expect(0x114514.U)
      c.dcacheReadIO.addr_ok.poke(1.B)
      c.clock.step()
      c.io.out.valid.expect(0.B)
      c.io.in.ready.expect(0.B)
      c.dcacheReadIO.data_ok.poke(1.B)
      c.dcacheReadIO.rdata.poke(233.U)
      c.clock.step()
      c.io.out.valid.expect(1.B)
      c.io.out.bits.value.expect(233.U)
      c.io.out.ready.poke(1.B)
      c.clock.step()
      c.io.in.ready.expect(1.B)
      c.io.out.bits.value.expect(233.U)
      c.io.out.valid.expect(0.B)
      c.clock.step()
      c.io.out.valid.expect(0.B)

      c.io.in.bits.valA.poke(0xcafe0000L.U)
      c.io.in.bits.valB.poke(0x0000babeL.U)
      c.io.in.bits.operation.poke(0x1.U)
      c.io.out.ready.poke(1.B)
      c.io.in.valid.poke(1.B)
      c.dcacheReadIO.data_ok.poke(0.B)
      c.dcacheReadIO.addr_ok.poke(0.B)
      c.clock.step()
      c.io.out.valid.expect(0.B)
      c.io.in.ready.expect(0.B)

      c.clock.step()
      c.io.out.valid.expect(0.B)
      c.io.in.ready.expect(0.B)
      c.dcacheReadIO.addr_ok.poke(1.B)
      c.dcacheReadIO.data_ok.poke(1.B)
      c.dcacheReadIO.rdata.poke(0xfe0000.U)
      c.clock.step()
      c.io.out.valid.expect(1.B)
      c.io.out.bits.value.expect(0xfffffffeL.U)
      c.clock.step()
      c.io.in.ready.expect(1.B)
      c.io.out.valid.expect(0.B)
      c.clock.step()
      c.io.out.valid.expect(0.B)
    }
  }

}
