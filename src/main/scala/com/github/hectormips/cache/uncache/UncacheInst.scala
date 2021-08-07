package com.github.hectormips.cache.uncache


import chisel3._
import chisel3.util._
import com.github.hectormips.{AXIIO, SRamLikeInstIO}
import com.github.hectormips.cache.setting._
import com.github.hectormips.cache.utils.Wstrb

import scala.collection.immutable.Nil

/**
 * 指令uncache
 */
class UncacheInst extends Module{
  val io = IO(new Bundle{
    val input = Flipped(new SRamLikeInstIO())
    val axi = new AXIIO(1)
  })
  val sIDLE::s1Handshake::s1Recv::s2Handshake::s2Recv::sWait::Nil = Enum(6)
  val state = RegInit(0.U(3.W))
  val addr_r = RegInit(0.U(32.W))
  val rdata  = Reg(Vec(2,UInt(32.W)))
  io.input.addr_ok := state === sIDLE
  io.input.data_ok := false.B
  io.input.rdata := Cat(rdata(1),rdata(0))
  io.input.inst_valid := Cat(true.B,true.B)
  io.input.inst_pc := addr_r
  io.input.inst_predict_jump_in := DontCare
  io.input.inst_predict_jump_target_in := DontCare
  when(state === sIDLE && io.input.req && io.input.addr_ok){
    state := s1Handshake
    addr_r := io.input.addr
  }
  when(state === s1Handshake){
    when(io.axi.arvalid===true.B && io.axi.arready===true.B && io.axi.arid === 0.U){
      state := s1Recv
    }.otherwise{
      state := s1Handshake
    }
  }
  when(state === s2Handshake){
    when(io.axi.arvalid===true.B && io.axi.arready===true.B && io.axi.arid === 1.U){
      state := s2Recv
    }.otherwise{
      state := s2Handshake
    }
  }
  when(state === s1Recv){
    when(io.axi.rvalid.asBool() && io.axi.rready.asBool() && io.axi.rid ===0.U){
      state := s2Handshake
      rdata(0) :=  io.axi.rdata
    }.otherwise{
      state := s1Recv
    }
  }
  when(state === s2Recv){
    when(io.axi.rvalid.asBool() && io.axi.rready.asBool()  && io.axi.rid ===1.U){
      state := sWait
      rdata(1) :=  io.axi.rdata
    }.otherwise{
      state := s2Recv
    }
  }
  when(state === sWait){
    state := sIDLE
    io.input.data_ok := true.B
  }
  //ar
  io.axi.arid := Mux(state===s2Handshake,1.U,0.U)
  io.axi.araddr := Mux(state===s1Handshake,addr_r,addr_r+4.U)
  io.axi.arlen := 0.U
  io.axi.arsize := 3.U
  io.axi.arburst := 0.U
  io.axi.arlock := 0.U
  io.axi.arcache := 0.U
  io.axi.arprot := 0.U
  io.axi.arvalid := (state === s1Handshake || state === s2Handshake)

  //r
  io.axi.rready := state===s1Recv && io.axi.rid ===0.U || state===s2Recv && io.axi.rid === 1.U



  io.axi.awvalid := 0.U
  io.axi.wstrb := 0.U
  io.axi.awaddr := DontCare
  io.axi.awcache := DontCare
  io.axi.awprot := DontCare
  io.axi.awid := DontCare
  io.axi.awlen := DontCare
  io.axi.wlast := DontCare
  io.axi.bready := DontCare
  io.axi.awlock := DontCare
  io.axi.wdata := DontCare
  io.axi.awburst := DontCare
  io.axi.awsize := DontCare
  io.axi.wvalid := DontCare
  io.axi.wid := DontCare

}