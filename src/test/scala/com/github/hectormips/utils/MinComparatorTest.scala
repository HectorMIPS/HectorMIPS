package com.github.hectormips.utils

import chisel3._
import chiseltest._
import org.scalatest._

class MinComparatorTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "MinComparator"

  it should "find min value" in {
    test(new MinComparator(4, 4)) {c =>
      c.io.in(0).poke("b1111".U)
      c.io.in(1).poke("b1101".U)
      c.io.in(2).poke("b0010".U)
      c.io.in(3).poke("b0011".U)
      c.io.out.expect("b0100".U)
    }

    test(new MinComparator(4, 4)) {c =>
      c.io.in(0).poke("b1111".U)
      c.io.in(1).poke("b1001".U)
      c.io.in(2).poke("b1010".U)
      c.io.in(3).poke("b1011".U)
      c.io.out.expect("b0010".U)
    }
  }
  it should "find muti min value" in {
    test(new MinComparator(4, 4)) {c =>
      c.io.in(0).poke("b0000".U)
      c.io.in(1).poke("b0000".U)
      c.io.in(2).poke("b1010".U)
      c.io.in(3).poke("b1011".U)
      c.io.out.expect("b0011".U)
    }
    test(new MinComparator(4, 4)) {c =>
      c.io.in(0).poke("b0000".U)
      c.io.in(1).poke("b0000".U)
      c.io.in(2).poke("b0000".U)
      c.io.in(3).poke("b0000".U)
      c.io.out.expect("b1111".U)
    }
    test(new MinComparator(4, 4)) {c =>
      c.io.in(0).poke("b1111".U)
      c.io.in(1).poke("b1111".U)
      c.io.in(2).poke("b1111".U)
      c.io.in(3).poke("b1111".U)
      c.io.out.expect("b1111".U)
    }
    test(new MinComparator(4, 8)) {c =>
      c.io.in(0).poke("b1111".U)
      c.io.in(1).poke("b1111".U)
      c.io.in(2).poke("b1111".U)
      c.io.in(3).poke("b1111".U)
      c.io.in(4).poke("b1111".U)
      c.io.in(5).poke("b1111".U)
      c.io.in(6).poke("b1111".U)
      c.io.in(7).poke("b1111".U)
      c.io.out.expect("b11111111".U)
    }
  }
}
