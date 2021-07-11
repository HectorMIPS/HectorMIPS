package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class CommonMultiplierTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "CommonMultiplier"

  it should "multiply" in {
    test(new CommonMultiplier()) { c =>
      c.io.mult1.poke(0x12345678L.U(32.W))
      c.io.mult2.poke(0x87654321L.U(32.W))
      c.io.is_signed.poke(0.B)
      c.io.mult_res_63_32.expect(0x09A0CD05L.U)
      c.io.mult_res_31_0.expect(0x70B88D78L.U)
      c.io.is_signed.poke(1.B)
      c.io.mult1.poke(0xFFFFFFFFL.U)
      c.io.mult2.poke(0xFFFFFFFFL.U)
      // 86A1C970B88D78
      c.io.mult_res_63_32.expect(0.U)
      c.io.mult_res_31_0.expect(1.U)
    }
  }
}
