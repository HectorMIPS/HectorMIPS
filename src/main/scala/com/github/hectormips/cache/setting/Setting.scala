package com.github.hectormips.cache.setting

import chisel3._
import chisel3.util._

class CacheConfig(val _wayNum:Int=2,val WayWidth:Int=4*1024,val DataWidthByByte:Int=16, val victimDepth:Int=16){
  /**
   * WayNum    : 路数
   * WayWidth  : 路大小，大于4KB需要支持重命名寄存器
   * DataWidth : 数据宽度
   */
//  val physicalWidth = 32
  val wayNumWidth   = log2Ceil(_wayNum) //1
  val wayNum        = _wayNum //2路
  val bankNum       =  DataWidthByByte >> 2 //默认16字节，即4个Bank
  // 地址划分
  val tagWidth      = 32 - log2Ceil(WayWidth)
  val offsetWidth   = log2Ceil(DataWidthByByte) //4
  val indexWidth    = log2Ceil(WayWidth) - offsetWidth //路大小位数 减去offset位数,8
  val lineNum       = 1 << indexWidth
  val victimDepthWidth   = log2Ceil(victimDepth)
  def getTag(x:UInt):UInt = x(31,32 - tagWidth) //(31,12)
  def getIndex(x:UInt):UInt = x(31- tagWidth, 32 - tagWidth-indexWidth) //(11,4)
  def getOffset(x:UInt):UInt = x(31-tagWidth-indexWidth,0) (3,0)
  def getBankIndex(x:UInt):UInt = x(31-tagWidth-indexWidth,2) //除以4 (3,2)

  def getVictimTag(x:UInt):UInt = x(31-offsetWidth,32-offsetWidth - tagWidth)
  def getVictimIndex(x:UInt):UInt = x(31-offsetWidth-tagWidth,32-offsetWidth - tagWidth-indexWidth)
}

