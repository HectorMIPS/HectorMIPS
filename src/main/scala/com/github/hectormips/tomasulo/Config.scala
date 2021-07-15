package com.github.hectormips.tomasulo

import chisel3.util.{isPow2, log2Ceil}

class Config(rob_size_in: Int, station_size_in: Int) {
  require(isPow2(rob_size_in))
  require(isPow2(station_size_in))
  require(rob_size_in > 1)
  require(station_size_in > 1)

  val rob_size: Int = rob_size_in
  val station_size: Int = station_size_in
  val rob_width: Int = log2Ceil(rob_size)
  val station_width: Int = log2Ceil(station_size)
}
