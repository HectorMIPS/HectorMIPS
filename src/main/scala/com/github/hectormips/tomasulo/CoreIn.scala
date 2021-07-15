package com.github.hectormips.tomasulo

import chisel3._
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.ComponentInOperationWidth

class CoreIn(config: Config) extends Bundle {
  val operation     : UInt = UInt(ComponentInOperationWidth.Width.W)
  val station_target: UInt = UInt(config.station_size.W)
  val exception_flag: UInt = UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W)
  val pc            : UInt = UInt(32.W)
  val srcA          : UInt = UInt(5.W)
  val srcB          : UInt = UInt(5.W)
  val need_valA     : Bool = Bool()
  val need_valB     : Bool = Bool()
  val valA          : UInt = UInt(32.W)
  val valB          : UInt = UInt(32.W)
  val target_pc     : UInt = UInt(32.W)
  val dest          : UInt = UInt(5.W)
  // HILO 标志
  val writeHI       : Bool = Bool()
  val writeLO       : Bool = Bool()
  val readHI        : Bool = Bool()
  val readLO        : Bool = Bool()

}
