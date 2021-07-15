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

      c.io.ready.expect(1.B)

      c.io.valid.poke(1.B)
      c.io.operation.poke(1.U)
      c.io.pc.poke(1.U)
      c.io.srcA.poke(0.U)
      c.io.srcB.poke(0.U)
      c.io.dest.poke(2.U)
      c.io.station_target.poke(0.U)
      c.clock.step(2)
      c.io.valid.poke(0.B)
      c.clock.step(20)
    }
  }
}
