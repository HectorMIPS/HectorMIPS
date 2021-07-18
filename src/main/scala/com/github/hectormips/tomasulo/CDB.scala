package com.github.hectormips.tomasulo

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._
import com.github.hectormips.tomasulo.rob.RobResultIn

class CDB(config: Config, size: Int) extends Module {
  val io: CDB_IO = IO(new CDB_IO)

  class CDB_IO extends Bundle {
    val in: Vec[DecoupledIO[RobResultIn]] = Vec(size, Flipped(DecoupledIO(new RobResultIn(config))))
    val out: DecoupledIO[RobResultIn] = DecoupledIO(new RobResultIn(config))
  }

  val queues: Vec[DecoupledIO[RobResultIn]] = VecInit(io.in.map(i => Queue(i)))

  val arbiter: Arbiter[RobResultIn] = Module(new Arbiter(new RobResultIn(config), size))
  arbiter.io.in <> queues
  io.out <> arbiter.io.out
}

object CDB extends App {
  val v_content = (new ChiselStage).emitVerilog(new CDB(new Config(4, 4), 4))
}
