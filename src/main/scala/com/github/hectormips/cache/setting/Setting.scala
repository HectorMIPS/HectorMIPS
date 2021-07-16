package com.github.hectormips.cache.setting

import chisel3._
import chisel3.util._

class CacheConfig(val _wayNum:Int,val WayWidth:Int,val DataWidthByByte:Int){
  /**
   * WayNum    : 路数
   * WayWidth  : 路大小，大于4KB需要支持重命名寄存器
   * DataWidth : 数据宽度
   */
//  val physicalWidth = 32
  val wayNumWidth   = log2Ceil(_wayNum) //1
  val wayNum        = _wayNum //2路
  val bankNum       =  DataWidthByByte / 4 //默认16字节，即4个Bank
  // 地址划分
  val tagWidth      = 32 - wayNumWidth
  val offsetWidth   = log2Ceil(DataWidthByByte*8)
  val indexWidth    = log2Ceil(WayWidth) - offsetWidth //路大小位数 减去offset位数

  def getTag(x:UInt):UInt = x(31,32 - tagWidth)
  def getIndex(x:UInt):UInt = x(31- tagWidth, 32 - tagWidth-indexWidth)
  def getOffset(x:UInt):UInt = x(31-tagWidth-indexWidth,0)
  def getBankIndex(x:UInt):UInt = x(31-tagWidth-indexWidth,2) //除以4
}

class Setting() {

}
