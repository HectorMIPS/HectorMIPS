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
    test(new Core(new Config(4, 4))).withAnnotations(Seq(VerilatorBackendAnnotation)) { c =>
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

      c.io.in.bits.pc.poke(3.U)
      c.io.in.bits.srcA.poke(0.U)
      c.io.in.bits.srcB.poke(2.U)
      c.clock.step(cycles = 1)

      c.io.in.valid.poke(0.B)

      c.io.debug_wb_pc.expect(1.U)
      c.io.debug_wb_rf_wen.expect(1.U)
      c.io.debug_wb_rf_wdata.expect(14.U)
      c.io.debug_wb_rf_wnum.expect(2.U)
      c.clock.step(cycles = 1)

      c.io.in.valid.poke(0.B)
      c.io.debug_wb_pc.expect(1.U)
      c.io.debug_wb_rf_wen.expect(1.U)
      c.io.debug_wb_rf_wdata.expect(14.U)
      c.io.debug_wb_rf_wnum.expect(2.U)
      c.clock.step(cycles = 2)
      c.io.debug_wb_pc.expect(3.U)
      c.io.debug_wb_rf_wen.expect(1.U)
      c.io.debug_wb_rf_wdata.expect(21.U)
      c.io.debug_wb_rf_wnum.expect(2.U)

      c.clock.step(20)
    }
  }

  it should "jump" in {
    test(new Core(new Config(4, 4))).withAnnotations(Seq(VerilatorBackendAnnotation)) { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)
      c.clock.step(5)

      c.io.in.ready.expect(1.B)

      c.io.in.valid.poke(1.B)
      c.io.in.bits.operation.poke(0.U)
      c.io.in.bits.pc.poke(3.U)
      c.io.in.bits.srcA.poke(0.U)
      c.io.in.bits.srcB.poke(0.U)
      c.io.in.bits.dest.poke(0.U)
      c.io.in.bits.station_target.poke(2.U)
      c.io.in.bits.predictJump.poke(0.B)
      c.io.in.bits.target_pc.poke(5.U)
      c.clock.step(1)
      c.io.in.bits.operation.poke(1.U)
      c.io.in.bits.pc.poke(1.U)
      c.io.in.bits.srcA.poke(0.U)
      c.io.in.bits.srcB.poke(0.U)
      c.io.in.bits.dest.poke(2.U)
      c.io.in.bits.station_target.poke(0.U)

      c.clock.step(1)
      c.io.in.valid.poke(0.B)

      c.io.debug_wb_pc.expect(3.U)
      c.io.debug_wb_rf_wnum.expect(0.U)
      c.clock.step(cycles = 1)

//      c.io.debug_wb_pc.expect(1.U)
//      c.io.debug_wb_rf_wen.expect(1.U)
//      c.io.debug_wb_rf_wdata.expect(14.U)
//      c.io.debug_wb_rf_wnum.expect(2.U)

//      c.io.clear.expect(1.B)
//      c.io.new_pc.expect(5.U)


      c.clock.step(20)
    }
  }
}
