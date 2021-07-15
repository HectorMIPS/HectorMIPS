package com.github.hectormips.utils

import chisel3._

object pow2{
  def apply(in: Int): BigInt = {
    BigInt(2).pow(in)
  }
}

object fill1{
  def apply(width: Int): UInt = {
    (BigInt(2).pow(width) - 1).U
  }
}
