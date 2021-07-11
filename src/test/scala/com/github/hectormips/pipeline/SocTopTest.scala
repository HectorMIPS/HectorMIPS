package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation

class SocTopTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SocTop"

  def printPc(pc: UInt): Unit = {
    println(s"pc ===> $pc")
  }

  it should "execute and reset" in {
    test(new SocTop("resource/inst1.hex.txt")) { c =>

      for (i <- 1 to 5) {
        c.clock.step()
        println(s"pc ===> ${c.io.debug_wb_pc.peek()}, we ===> ${c.io.debug_wb_rf_wen.peek()}")
      }
      // addu r1, r2, r3
      c.io.debug_wb_rf_wnum.expect(3.U)
      c.io.debug_wb_rf_wdata.expect(10.U)

      c.clock.step(1)
      println(s"pc ===> ${c.io.debug_wb_pc.peek()}")
      // subu r16, r17, r18
      c.io.debug_wb_rf_wnum.expect("b10010".U)
      c.io.debug_wb_rf_wdata.expect(0.U)

      c.clock.step()
      // slt r1, r2, r4
      c.io.debug_wb_rf_wnum.expect(4.U)
      c.io.debug_wb_rf_wdata.expect(0.U)

      // addu r0, r0, r0
      c.clock.step(2)
      // addu r3, r3, r3
      c.io.debug_wb_rf_wnum.expect(3.U)
      c.io.debug_wb_rf_wdata.expect(20.U)

      c.clock.step()
      // and r4, r3, r6
      c.io.debug_wb_rf_wnum.expect(6.U)
      c.io.debug_wb_rf_wdata.expect(0.U)
      // jr 24
      println(s"pc ===> ${c.io.debug_wb_pc.peek()}")

      c.clock.step()
      // lui 8, 0x0005
      c.io.debug_wb_rf_wnum.expect(8.U)
      c.io.debug_wb_rf_wdata.expect(0x53240000.U)

      println(s"pc ===> ${c.io.debug_wb_pc.peek()}")

      c.clock.step()

      c.reset.poke(1.B)
      c.clock.step(4)
      c.reset.poke(0.B)
      c.clock.step(5)
      println(s"pc ===> ${c.io.debug_wb_pc.peek()}")
      c.io.debug_wb_rf_wnum.expect(3.U)
      c.io.debug_wb_rf_wdata.expect(10.U)

    }
  }
  it should "addu recursively" in {
    test(new SocTop("resource/inst2.hex.txt")).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.clock.step(4)
      for (i <- 0 until 5) {
        c.clock.step()
        // addu r5, r5, r5
        c.io.debug_wb_rf_wnum.expect(5.U)
        // 10, 20, 40, 80, 160
        c.io.debug_wb_rf_wdata.expect((10 * (1 << i)).U)
      }
    }

  }

  it should "stall until lw finish" in {
    test(new SocTop("resource/inst3.hex.txt")).withAnnotations(Seq(WriteVcdAnnotation)) { c => {
      c.clock.step(5)
      printPc(c.io.debug_wb_pc.peek())
      // lw $23, 0x60($0)
      c.io.debug_wb_rf_wen.expect(0xf.U)
      c.io.debug_wb_rf_wdata.expect(0.U)
      c.io.debug_wb_rf_wnum.expect(23.U)

      // addu $23, $5, $23 延迟一个周期
      c.clock.step()
      printPc(c.io.debug_wb_pc.peek())
      c.io.debug_wb_rf_wen.expect(0.U)
      c.clock.step()
      printPc(c.io.debug_wb_pc.peek())
      c.io.debug_wb_rf_wnum.expect(23.U)
      c.io.debug_wb_rf_wdata.expect(5.U)
      c.io.debug_wb_rf_wen.expect(0xf.U)
      // addu $23, $23, $23
      c.clock.step()
      printPc(c.io.debug_wb_pc.peek())
      c.io.debug_wb_rf_wen.expect(0xf.U)
      c.io.debug_wb_rf_wdata.expect(10.U)
      // addu $23, $23, $23
      c.clock.step()
      printPc(c.io.debug_wb_pc.peek())
      c.io.debug_wb_rf_wnum.expect(23.U)
      c.io.debug_wb_rf_wen.expect(0xf.U)
      c.io.debug_wb_rf_wdata.expect(20.U)
      // addu $23, $23, $23
      c.clock.step()
      printPc(c.io.debug_wb_pc.peek())
      c.io.debug_wb_rf_wnum.expect(23.U)
      c.io.debug_wb_rf_wen.expect(0xf.U)
      c.io.debug_wb_rf_wdata.expect(40.U)
    }
    }

  }
  it should "correctly jr after lw" in {
    test(new SocTop("resource/inst4.hex.txt")).withAnnotations(Seq(WriteVcdAnnotation)) { c => {
      c.clock.step(5)
      printPc(c.io.debug_wb_pc.peek())
      // lw $23, 0x60($0)
      c.io.debug_wb_rf_wen.expect(0xf.U)
      c.io.debug_wb_rf_wdata.expect(0.U)
      c.io.debug_wb_rf_wnum.expect(23.U)

      // jr $23
      c.clock.step(2)
      c.io.debug_wb_rf_wen.expect(0x0.U)
      printPc(c.io.debug_wb_pc.peek())
      c.clock.step(2)

      // lw $23, 0x60($0)
      c.io.debug_wb_rf_wen.expect(0xf.U)
      c.io.debug_wb_rf_wdata.expect(0.U)
      c.io.debug_wb_rf_wnum.expect(23.U)
      c.io.debug_wb_pc.expect(0.U)

    }
    }

  }
  it should "run instructions in seq after lw" in {
    test(new SocTop("resource/inst5.hex.txt")).withAnnotations(Seq(WriteVcdAnnotation)) { c => {
      c.clock.step(5)
      printPc(c.io.debug_wb_pc.peek())
      // lw t2, 0(t0)
      c.io.debug_wb_rf_wen.expect(0xf.U)
      c.io.debug_wb_rf_wnum.expect(10.U)

      // xor t2, t2, t1
      c.clock.step(2)
      c.io.debug_wb_rf_wen.expect(0xf.U)
      printPc(c.io.debug_wb_pc.peek())
      c.clock.step(1)

      //  sll t3, t2, 0x9
      c.io.debug_wb_rf_wen.expect(0xf.U)
      c.io.debug_wb_rf_wnum.expect(11.U)
      printPc(c.io.debug_wb_pc.peek())
    }
    }

  }
  it should "run access hi lo correctly" in {

    test(new SocTop("resource/inst6.hex.txt")).withAnnotations(Seq(WriteVcdAnnotation)) { c => {
      c.clock.step(5)
      printPc(c.io.debug_wb_pc.peek())
      // addiu a1, a1, 0x120
      c.io.debug_wb_rf_wen.expect(0xf.U)
      c.io.debug_wb_rf_wdata.expect(0x125.U)

      // mtlo a1
      c.clock.step(1)

      // mflo a1
      c.clock.step(1)
      c.io.debug_wb_rf_wnum.expect(5.U)
      c.io.debug_wb_rf_wen.expect(0xf.U)
      c.io.debug_wb_rf_wdata.expect(0x125.U)
    }
    }

  }
}
