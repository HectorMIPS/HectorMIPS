package com.github.hectormips.pipeline

import chisel3.experimental.ChiselEnum

object BranchState extends ChiselEnum {
  val no_branch, flushing, branching = Value
}
