package com.github.hectormips.predict

import chisel3._
import chiseltest._
import org.scalatest._

class BHTTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "BHT"

  it should "be true" in {
    test(new BHT()) { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)

      c.io.predict.expect(0.B)

      c.io.en_visit.poke(1.B)
      c.io.is_visited.poke(1.B)

      c.clock.step(1)
      c.io.predict.expect(1.B)

      c.clock.step(1)
      c.io.predict.expect(1.B)

      c.clock.step(1)
      c.io.predict.expect(1.B)
    }
  }

  it should "be false" in {
    test(new BHT()) { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)

      c.io.predict.expect(0.B)

      c.io.en_visit.poke(1.B)
      c.io.is_visited.poke(1.B)

      c.clock.step(5)

      c.io.is_visited.poke(0.B)

      c.clock.step(1)
      c.io.predict.expect(1.B)

      c.clock.step(1)
      c.io.predict.expect(0.B)

      c.clock.step(1)
      c.io.predict.expect(0.B)
    }
  }
}
