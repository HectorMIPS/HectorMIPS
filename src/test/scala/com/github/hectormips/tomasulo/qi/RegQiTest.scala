package com.github.hectormips.tomasulo.qi

import chisel3._
import chiseltest._
import chiseltest.internal.WriteVcdAnnotation
import chiseltest.experimental.TestOptionBuilder._
import com.github.hectormips.tomasulo.Config
import org.scalatest._

class RegQiTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "RegQiTest"

  it should "be true" in {
    test(new RegQi(new Config(4,4))).withAnnotations(Seq(WriteVcdAnnotation))  { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)

      c.io.ins_rob_valid.poke(1.B)

      c.io.ins_in.target.poke(1.U)
      c.io.rob_target.poke(1.U)
      c.clock.step(1)
      c.io.src_1.poke(1.U)
      c.io.src_1_is_busy.expect(1.B)
      c.io.src_1_rob_target.expect(1.U)
      c.io.src_2.poke(2.U)
      c.io.src_2_is_busy.expect(0.B)

      c.io.ins_in.target.poke(1.U)
      c.io.rob_target.poke(3.U)
      c.clock.step(1)
      c.io.src_1.poke(1.U)
      c.io.src_1_is_busy.expect(1.B)
      c.io.src_1_rob_target.expect(3.U)
      c.io.src_2.poke(2.U)
      c.io.src_2_is_busy.expect(0.B)

      c.io.ins_in.target.poke(1.U)
      c.io.rob_target.poke(0.U)
      c.io.finished_ins.poke(3.U)
      c.io.finished_ins_target.poke(1.U)
      c.io.finished_ins_valid.poke(1.B)
      c.clock.step(1)
      c.io.src_1.poke(1.U)
      c.io.src_1_is_busy.expect(1.B)
      c.io.src_1_rob_target.expect(0.U)
      c.io.src_2.poke(2.U)
      c.io.src_2_is_busy.expect(0.B)

      c.io.ins_rob_valid.poke(0.B)
      c.io.finished_ins.poke(0.U)
      c.io.finished_ins_target.poke(1.U)
      c.io.finished_ins_valid.poke(1.B)
      c.clock.step(1)
      c.io.src_1.poke(1.U)
      c.io.src_1_is_busy.expect(0.B)
      c.io.src_2.poke(2.U)
      c.io.src_2_is_busy.expect(0.B)
    }
  }
}
