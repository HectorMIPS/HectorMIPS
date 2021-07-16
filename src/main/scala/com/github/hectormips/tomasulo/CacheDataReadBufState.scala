package com.github.hectormips.tomasulo

import chisel3.experimental.ChiselEnum

object CacheDataReadBufState extends ChiselEnum {
  val waiting_for_input, // 等待给cache输入地址
  waiting_for_reading, // cache的输出已经存入buf，等待被读出
  read_done, // buf的内容已经被读出
  waiting_for_cache_output, // cache已经确认了地址，等待cache输出
  waiting_for_canceling // 请求已经发出，但是需要更换地址，等待取消
  = Value

}