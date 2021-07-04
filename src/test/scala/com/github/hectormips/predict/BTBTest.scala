package com.github.hectormips.predict

import chisel3._
import chiseltest._
import org.scalatest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.VerilatorBackendAnnotation

class BTBTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "BTB"

  it should "basic" in {
    test(new BTB(4, 16)).withAnnotations(Seq(VerilatorBackendAnnotation)) { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)

      c.io.pc.poke(0.U)
      c.io.en_ex.poke(1.B)

      c.io.ex_success.poke(1.B)
      c.io.ex_pc.poke(4.U)
      c.io.ex_target.poke(5.U)
      c.clock.step(1)

      c.io.ex_success.poke(0.B)
      c.io.ex_pc.poke(4.U)
      c.io.ex_target.poke(5.U)
      c.clock.step(1)

      c.io.ex_success.poke(1.B)
      c.io.ex_pc.poke(4.U)
      c.io.ex_target.poke(5.U)
      c.clock.step(1)

      c.io.pc.poke(4.U)
      c.io.target.expect(5.U)
      c.io.predict.expect(1.B)
    }
  }
}
