package com.github.hectormips.tomasulo.ex_component.util

import chisel3._
import chisel3.stage.ChiselStage

class IPDividerBundle extends Bundle {
  val s_axis_divisor_tdata : UInt = Input(UInt(32.W))
  val s_axis_divisor_tready: Bool = Output(Bool())
  val s_axis_divisor_tvalid: Bool = Input(Bool())

  val s_axis_dividend_tdata : UInt = Input(UInt(32.W))
  val s_axis_dividend_tready: Bool = Output(Bool())
  val s_axis_dividend_tvalid: Bool = Input(Bool())

  val m_axis_dout_tvalid: Bool = Output(Bool())
  val m_axis_dout_tdata : UInt = Output(UInt(64.W))
  val aclk              : Bool = Input(Bool())
}

class div_signed_gen_0 extends BlackBox {
  val io: IPDividerBundle = IO(new IPDividerBundle)
}

class div_unsigned_gen_0 extends BlackBox {
  val io: IPDividerBundle = IO(new IPDividerBundle)
}

class CommonDividerBundle extends Bundle {
  val divisor  : UInt = Input(UInt(32.W))
  val dividend : UInt = Input(UInt(32.W))
  val is_signed: Bool = Input(Bool())
  val quotient : UInt = Output(UInt(32.W))
  val remainder: UInt = Output(UInt(32.W))

  // AXI控制信号
  val tready: Bool = Output(Bool())
  val tvalid: Bool = Input(Bool())

  val out_valid: Bool = Output(Bool())
}

class CommonDivider extends Module {
  val io                 : CommonDividerBundle = IO(new CommonDividerBundle)
  val ip_divider_signed  : div_signed_gen_0    = Module(new div_signed_gen_0)
  val ip_divider_unsigned: div_unsigned_gen_0  = Module(new div_unsigned_gen_0)

  ip_divider_signed.io.s_axis_divisor_tdata := io.divisor
  ip_divider_signed.io.s_axis_divisor_tvalid := io.tvalid

  ip_divider_signed.io.s_axis_dividend_tdata := io.dividend
  ip_divider_signed.io.s_axis_dividend_tvalid := io.tvalid

  ip_divider_unsigned.io.s_axis_divisor_tdata := io.divisor
  ip_divider_unsigned.io.s_axis_divisor_tvalid := io.tvalid

  ip_divider_unsigned.io.s_axis_dividend_tdata := io.dividend
  ip_divider_unsigned.io.s_axis_dividend_tvalid := io.tvalid

  io.tready := Mux(io.is_signed, ip_divider_signed.io.s_axis_divisor_tready && ip_divider_signed.io.s_axis_dividend_tready,
    ip_divider_unsigned.io.s_axis_divisor_tready && ip_divider_unsigned.io.s_axis_dividend_tready)

  io.out_valid := Mux(io.is_signed, ip_divider_signed.io.m_axis_dout_tvalid,
    ip_divider_unsigned.io.m_axis_dout_tvalid)
  io.quotient := Mux(io.is_signed, ip_divider_signed.io.m_axis_dout_tdata(63, 32),
    ip_divider_unsigned.io.m_axis_dout_tdata(63, 32))
  io.remainder := Mux(io.is_signed, ip_divider_signed.io.m_axis_dout_tdata(31, 0),
    ip_divider_unsigned.io.m_axis_dout_tdata(31, 0))

  ip_divider_signed.io.aclk := clock.asBool()
  ip_divider_unsigned.io.aclk := clock.asBool()
}
