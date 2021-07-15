package com.github.hectormips.cache.setting

import chisel3._
import chisel3.util._

class CacheSetting(val wayNum:Int,val WayWidth:Int,val DataWidth:Int){
  /**
   * WayNum    : 路数
   * WayWidth  : 路大小，大于4KB需要支持重命名寄存器
   * DataWidth : 数据宽度
   */
//  val physicalWidth = 32
  val wayNumWidth   = log2Ceil(wayNum + 1)

  // 地址划分
  val tagWidth      = 32 - wayNumWidth
  val offsetWidth    = log2Ceil(DataWidth + 1)
  val indexWidth     = log2Ceil(WayWidth + 1) - offsetWidth //路大小位数 减去offset位数

  def getTag(x:UInt):UInt = x(31,32 - tagWidth)
  def getIndex(x:UInt):UInt = x(31- tagWidth, 32 - tagWidth-indexWidth)
  def getOffset(x:UInt):UInt = x(31-tagWidth-indexWidth,0)

}

class Setting() {

}
