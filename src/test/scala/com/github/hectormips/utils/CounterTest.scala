package com.github.hectormips.utils

import chisel3._
import chiseltest._
import org.scalatest._


class CounterTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Counter"

  it should "asc Counter" in {
    test(new Counter(128)) { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)
      c.io.en.poke(1.B)
      c.io.value.expect(0.U)

      c.clock.step(126)
      c.io.value.expect(126.U)
      c.clock.step(1)
      c.io.value.expect(127.U)
      c.clock.step(1)
      c.io.value.expect(127.U)
    }
  }

  it should "desc Counter" in {
    test(new Counter(128, desc = true)) { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)
      c.io.en.poke(1.B)
      c.io.value.expect(127.U)

      c.clock.step(126)
      c.io.value.expect(1.U)
      c.clock.step(1)
      c.io.value.expect(0.U)
      c.clock.step(1)
      c.io.value.expect(0.U)
    }
  }
}
