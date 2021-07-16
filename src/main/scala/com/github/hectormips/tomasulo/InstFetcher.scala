package com.github.hectormips.tomasulo

import Chisel.DecoupledIO
import chisel3._
import chisel3.util._
import com.github.hectormips.tomasulo.io.{DecoderIn, ICacheReadIO, InstFetcherIn}

class InstFetcher(pcInit: Long) extends MultiIOModule {
  class InstFetcherIO extends Bundle {
    val jumpFromExecute: InstFetcherIn          = Input(new InstFetcherIn)
    val jumpFromCommit : InstFetcherIn          = (new InstFetcherIn)
    val out            : DecoupledIO[DecoderIn] = DecoupledIO(new DecoderIn)
  }

  val pc          : UInt                       = RegInit(init = pcInit.U(32.W))
  val seqPc       : UInt                       = pc + 4.U
  val io          : InstFetcherIO              = IO(new InstFetcherIO)
  val icacheReadIO: ICacheReadIO               = IO(Flipped(new ICacheReadIO))
  val instBuf     : UInt                       = RegInit(UInt(32.W), init = 0.U(32.W))
  val instBufState: CacheDataReadBufState.Type = RegInit(CacheDataReadBufState(),
    init = CacheDataReadBufState.waiting_for_input)
  // 跳转屏蔽
  val jumpLevel   : UInt                       = RegInit(init = 0.U(2.W))
  val jumpEn      : Bool                       = io.jumpFromExecute.jumpEn ||
    io.jumpFromCommit.jumpEn
  val jumpDest    : UInt                       = MuxCase(seqPc, Seq(
    io.jumpFromCommit.jumpEn -> io.jumpFromCommit.jumpDest,
    io.jumpFromExecute.jumpEn -> io.jumpFromExecute.jumpDest
  ))

  when(instBufState === CacheDataReadBufState.waiting_for_input) {
    when(icacheReadIO.addr_ok) {
      when(!jumpEn) {
        instBufState := CacheDataReadBufState.waiting_for_cache_output
      }.otherwise {
        instBufState := CacheDataReadBufState.waiting_for_canceling
      }
    }
  }
  when(icacheReadIO.data_ok) {
    when(instBufState === CacheDataReadBufState.waiting_for_cache_output) {
      instBufState := CacheDataReadBufState.waiting_for_reading
      instBuf := icacheReadIO.rdata
    }.elsewhen(instBufState === CacheDataReadBufState.waiting_for_canceling) {
      instBufState := CacheDataReadBufState.waiting_for_input
    }
  }
  when(jumpEn) {
    // 当请求跳转时，确认地址之后的所有操作都应该被取消
    when(instBufState === CacheDataReadBufState.waiting_for_cache_output) {
      instBufState := CacheDataReadBufState.waiting_for_canceling
    }.elsewhen(instBufState === CacheDataReadBufState.waiting_for_reading) {
      instBufState := CacheDataReadBufState.waiting_for_input
    }
    pc := jumpDest
  }
  // 下一个阶段已经准备好阅读并且buf也已经准备完毕
  when(io.out.ready && instBufState === CacheDataReadBufState.waiting_for_reading) {
    instBufState := CacheDataReadBufState.waiting_for_input
    pc := seqPc
  }

  // 缓冲区能写入的时候就向cache发请求
  icacheReadIO.valid := instBufState === CacheDataReadBufState.waiting_for_input
  icacheReadIO.addr := Mux(jumpEn, jumpDest, pc)

  io.out.valid := instBufState === CacheDataReadBufState.waiting_for_reading && !jumpEn
  io.out.bits.pc := pc
  io.out.bits.inst := instBuf
}
