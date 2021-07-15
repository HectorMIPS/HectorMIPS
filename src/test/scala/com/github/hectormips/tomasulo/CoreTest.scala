package com.github.hectormips.tomasulo

import chisel3._
import chiseltest._
import chiseltest.internal.VerilatorBackendAnnotation
import chiseltest.experimental.TestOptionBuilder._
import com.github.hectormips.tomasulo.ex_component.operation.AluOp
import org.scalatest._

class CoreTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "CoreTest"

  it should "basic" in {
    test(new Core(new Config(4,4))).withAnnotations(Seq(VerilatorBackendAnnotation)) { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)
      c.clock.step(5)

      c.io.in.ready.expect(1.B)

      c.io.in.valid.poke(1.B)
      c.io.in.bits.operation.poke(1.U)
      c.io.in.bits.pc.poke(1.U)
      c.io.in.bits.srcA.poke(0.U)
      c.io.in.bits.srcB.poke(0.U)
      c.io.in.bits.dest.poke(2.U)
      c.io.in.bits.station_target.poke(0.U)
      c.clock.step(2)
      c.io.in.valid.poke(0.B)
      c.clock.step(20)
    }
  }
}
