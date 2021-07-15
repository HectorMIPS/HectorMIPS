package com.github.hectormips.tomasulo.ex_component

import chisel3._
import chisel3.util.DecoupledIO
import com.github.hectormips.tomasulo.Config
import com.github.hectormips.tomasulo.rob.RobResultIn

abstract class Component(config: Config) extends MultiIOModule {
  class ComponentIO extends Bundle {
    val in : DecoupledIO[ComponentIn] = Flipped(DecoupledIO(new ComponentIn(config)))
    val out: DecoupledIO[RobResultIn] = DecoupledIO(new RobResultIn(config))
  }

  final val io: ComponentIO = IO(new ComponentIO)
}
