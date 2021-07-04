package com.github.hectormips.pipeline

import chisel3._

class InsWriteBackBundle extends Bundle {

}

class InsWriteBack extends Module {
  val io: InsWriteBackBundle = IO(new InsWriteBackBundle)
}
