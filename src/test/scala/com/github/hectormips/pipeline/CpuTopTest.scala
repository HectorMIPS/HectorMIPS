package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._

class CpuTopTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "CpuTop"

  it should "execute addu" in {
    test(new CpuTop()) { c =>
      c.io.regfile_rdata1.expect(5.U)
    }
  }


}
