package com.github.hectormips.tomasulo

import chisel3._
import chiseltest._
import org.scalatest._

class CDBTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "CDBTest"

  it should "be true" in {
    test(new CDB(new Config(4,4), 4)) { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)

      c.io.out.ready.poke(1.B)

      c.io.in(0).valid.poke(1.B)
      c.io.in(1).valid.poke(1.B)
      c.io.in(2).valid.poke(1.B)
      c.io.in(3).valid.poke(1.B)

      c.io.out.valid.expect(1.B)
      c.io.in(0).ready.expect(1.B)
      c.io.in(1).ready.expect(0.B)
      c.io.in(2).ready.expect(0.B)
      c.io.in(3).ready.expect(0.B)

      c.io.in(0).valid.poke(0.B)
      c.io.in(1).valid.poke(1.B)
      c.io.in(2).valid.poke(0.B)
      c.io.in(3).valid.poke(1.B)

      c.io.out.valid.expect(1.B)
      c.io.in(0).ready.expect(0.B)
      c.io.in(1).ready.expect(1.B)
      c.io.in(2).ready.expect(0.B)
      c.io.in(3).ready.expect(0.B)

      c.io.in(0).valid.poke(0.B)
      c.io.in(1).valid.poke(0.B)
      c.io.in(2).valid.poke(0.B)
      c.io.in(3).valid.poke(1.B)

      c.io.out.valid.expect(1.B)
      c.io.in(0).ready.expect(0.B)
      c.io.in(1).ready.expect(0.B)
      c.io.in(2).ready.expect(0.B)
      c.io.in(3).ready.expect(1.B)

      c.io.in(0).valid.poke(0.B)
      c.io.in(1).valid.poke(0.B)
      c.io.in(2).valid.poke(0.B)
      c.io.in(3).valid.poke(0.B)

      c.io.out.valid.expect(0.B)
    }
  }
}
