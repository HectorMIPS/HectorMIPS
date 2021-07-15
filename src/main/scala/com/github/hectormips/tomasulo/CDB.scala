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

  val mask: UInt  = Wire(UInt(log2Ceil(size).W))

  mask := MuxCase(0.U, (0 until size).map(i => {
    (io.in(i).valid, i.U)
  }))

  for (i <- 0 until size){
    io.in(i).ready := Mux(mask === i.U, io.out.ready, 0.U)
  }
  io.out.valid := io.in(mask).valid
  io.out.bits := io.in(mask).bits
}

object CDB extends App {
  val v_content = (new ChiselStage).emitVerilog(new CDB(new Config(4,4), 4))
}
