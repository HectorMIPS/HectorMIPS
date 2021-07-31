package com.github.hectormips.cache.setting

import chisel3._
import chisel3.util._


/**
 *
 * @param _wayNum                   路数
 * @param WayWidth                  路大小，大于4KB需要支持重命名寄存器
 * @param DataWidthByByte           数据宽度
 * @param victimDepth               victim buffer的深度，太深容易影响时序
 * @param victim_fetch_cycles       victim 取数据/写数据周期数
 */
class CacheConfig(val _wayNum: Int = 2, val WayWidth: Int = 4 * 1024, val DataWidthByByte: Int = 16,
                  val victimDepth:Int = 8,val prefetch_buffer_size:Int= 2) {

  //  val physicalWidth = 32
  val wayNumWidth = log2Ceil(_wayNum) //1
  val wayNum = _wayNum //2路
  val bankNum = DataWidthByByte >> 2 //默认16字节，即4个Bank
  val bankNumWidth = log2Ceil(bankNum)

  // 地址划分
  val tagWidth = 32 - log2Ceil(WayWidth)
  val offsetWidth = log2Ceil(DataWidthByByte) //4
  val indexWidth = log2Ceil(WayWidth) - offsetWidth //路大小位数 减去offset位数,8
  val lineNum = 1 << indexWidth
//  val victimDepthWidth = log2Ceil(victimDepth)

  //victim
//  val victim_fetch_cycles_width = log2Ceil(victim_fetch_cycles)
//  val victim_fetch_every_cycle = bankNum / victim_fetch_cycles

  //prefetch

//  val dcache_worker =
  def getTag(x: UInt): UInt = x(31, 32 - tagWidth) //(31,12)

  def getIndex(x: UInt): UInt = x(31 - tagWidth, 32 - tagWidth - indexWidth) //(11,4)
  def getIndexByExpression(b: Bool,x:UInt,y:UInt): UInt ={
    Mux(b,x(31 - tagWidth, 32 - tagWidth - indexWidth),y(31 - tagWidth, 32 - tagWidth - indexWidth))
  } //(11,4)

  def getOffset(x: UInt): UInt = x(31 - tagWidth - indexWidth, 0)(3, 0)

  def getBankIndex(x: UInt): UInt = x(31 - tagWidth - indexWidth, 2) //除以4 (3,2)

  def getVictimTag(x: UInt): UInt = x(31 - offsetWidth, 32 - offsetWidth - tagWidth)

  def getVictimIndex(x: UInt): UInt = x(31 - offsetWidth - tagWidth, 32 - offsetWidth - tagWidth - indexWidth)
}

