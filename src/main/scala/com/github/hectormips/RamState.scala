package com.github.hectormips

import chisel3.experimental.ChiselEnum


object RamState extends ChiselEnum {
  val waiting_for_request, requesting, waiting_for_response, waiting_for_read, cancel = Value
}
