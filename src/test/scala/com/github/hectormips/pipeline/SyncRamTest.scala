package com.github.hectormips.pipeline

import chisel3._
import chiseltest._
import org.scalatest._
import chisel3.util.experimental.loadMemoryFromFile

class SyncRamTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SyncRam"

  it should "write and read" in {
    test(new SyncRam(1024)) { c =>
      c.io.ram_addr.poke(0.U)
      c.io.ram_en.poke(1.B)
      c.io.ram_wen.poke(1.B)
      c.io.ram_wdata.poke(0x34567891.U)

      c.clock.step() // 经过一个周期写入
      c.io.ram_wen.poke(0.B)
      c.clock.step()

      // 经过一个周期读出
      c.io.ram_rdata.expect(0x34567891.U)
    }
  }


}
