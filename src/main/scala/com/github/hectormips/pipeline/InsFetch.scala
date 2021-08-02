package com.github.hectormips.pipeline

/**
 * 由sidhch于2021/7/3创建
 */

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.stage.ChiselStage
import chisel3.util._
import com.github.hectormips.RamState
import com.github.hectormips.pipeline.cp0.ExceptionConst

object InsJumpSel extends OneHotEnum {
  val seq_pc            : Type = Value(1.U)
  val pc_add_offset     : Type = Value(2.U)
  val pc_cat_instr_index: Type = Value(4.U)
  val regfile_read1     : Type = Value(8.U)
}

class DecodePreFetchBundle extends Bundle {
  val jump_sel_id_pf: InsJumpSel.Type = Output(InsJumpSel())
  val jump_val_id_pf: Vec[UInt]       = Output(Vec(3, UInt(32.W)))
  val is_jump       : Bool            = Output(Bool())
  val bus_valid     : Bool            = Output(Bool())
  val jump_taken    : Bool            = Output(Bool())
  val stall_id_pf   : Bool            = Output(Bool())

  def defaults(): Unit = {
    jump_sel_id_pf := InsJumpSel.seq_pc
    jump_val_id_pf := VecInit(Seq(0.U, 0.U, 0.U))
    is_jump := 0.B
    bus_valid := 1.B
    jump_taken := 0.B
    stall_id_pf := 0.B
  }
}

class ExecutePrefetchBundle extends Bundle {
  val to_exception_service_en_ex_pf: Bool = Bool()
  val to_epc_en_ex_pf              : Bool = Bool()

  def defaults(): Unit = {
    to_exception_service_en_ex_pf := 0.B
    to_epc_en_ex_pf := 0.B
  }
}


class InsPreFetchBundle extends WithAllowin {
  val pc                           : UInt                 = Input(UInt(32.W))
  val id_pf_in                     : DecodePreFetchBundle = Flipped(new DecodePreFetchBundle)
  val ins_ram_addr                 : UInt                 = Output(UInt(32.W))
  val ins_ram_en                   : Bool                 = Output(Bool())
  val next_pc                      : UInt                 = Output(UInt(32.W))
  val pc_wen                       : Bool                 = Output(Bool())
  // 一次读入两条指令
  val pc_debug_pf_if               : UInt                 = Output(UInt(32.W))
  val to_exception_service_en_ex_pf: Bool                 = Input(Bool())
  val to_epc_en_ex_pf              : Bool                 = Input(Bool())
  val flush                        : Bool                 = Input(Bool())
  val cp0_pf_epc                   : UInt                 = Input(UInt(32.W))
  val fetch_state                  : RamState.Type        = Input(RamState())
  // 上一个请求的valid信号，每次data_ok的时候被刷新，用于判断下一次跳转+4/+8
  val inst_sram_ins_valid          : UInt                 = Input(UInt(2.W))
  val data_ok                      : Bool                 = Input(Bool())
  val addr_ok                      : Bool                 = Input(Bool())

  val in_valid: Bool = Input(Bool()) // 传入预取的输入是否有效

}

// 预取阶段，向同步RAM发起请求
class InsPreFetch extends Module {
  val io               : InsPreFetchBundle = IO(new InsPreFetchBundle())
  val pc_jump          : UInt              = Wire(UInt(32.W))
  val seq_pc_4         : UInt              = io.pc + 4.U
  // 如果上次取出的指令只有一个有效，则下一个取的指令只+4
  val seq_pc_8         : UInt              = Mux(!io.inst_sram_ins_valid(1), seq_pc_4, io.pc + 8.U)
  val exception_or_eret: Bool              = io.to_exception_service_en_ex_pf || io.to_epc_en_ex_pf
  val seq_epc_8        : UInt              = io.cp0_pf_epc
  val seq_exception_8  : UInt              = ExceptionConst.EXCEPTION_PROGRAM_ADDR + 4.U
  val feed_back_valid  : Bool              = io.id_pf_in.bus_valid || exception_or_eret
  // 如果有load-to-branch的情况，清空了队列之后还需要等待
  val req              : Bool              = !io.id_pf_in.stall_id_pf && io.next_allowin &&
    (io.fetch_state === RamState.waiting_for_request || io.fetch_state === RamState.requesting || io.data_ok)
  pc_jump := seq_pc_4

  switch(io.id_pf_in.jump_sel_id_pf) {
    is(InsJumpSel.seq_pc) {
      pc_jump := seq_pc_8
    }
    is(InsJumpSel.pc_add_offset) {
      pc_jump := io.id_pf_in.jump_val_id_pf(0)
    }
    is(InsJumpSel.pc_cat_instr_index) {
      pc_jump := io.id_pf_in.jump_val_id_pf(1)
    }
    is(InsJumpSel.regfile_read1) {
      pc_jump := io.id_pf_in.jump_val_id_pf(2)
    }

  }
  val jump_to_target: Bool = io.id_pf_in.bus_valid && io.id_pf_in.jump_taken
  val no_jump       : Bool = !io.id_pf_in.bus_valid || (io.id_pf_in.bus_valid && !io.id_pf_in.jump_taken)
  // 直到当前指令可以被decode接收时才发送新的请求
  val ready_go      : Bool = io.next_allowin
  // 没有特殊情况则继续请求下一条指令
  val pc_out        : UInt = MuxCase(seq_pc_8, Seq(
    io.to_epc_en_ex_pf -> seq_epc_8,
    io.to_exception_service_en_ex_pf -> seq_exception_8,
    // 如果队列已满或者还上一条请求还没有返回则原地踏步
    // 如果发起了请求但是没有addr_ok同样需要原地踏步
    (!io.next_allowin || (io.fetch_state === RamState.waiting_for_response && !io.data_ok) ||
      (req && !io.addr_ok)) -> io.pc,
    // TODO:如果decode阶段要求跳转，则需要清空队列、取消当前请求，并且请求跳转地址的指令
    (ready_go && jump_to_target) -> pc_jump
  ))
  io.next_pc := pc_out
  // 只要fifo没满就继续发请求
  io.ins_ram_en := req
  io.ins_ram_addr := pc_out

  // 发送完一条请求更新一次pc
  io.pc_wen := req
  io.this_allowin := DontCare
  val pc_last_req: UInt = RegInit(init = 0xbfc00000L.U)
  when(req && io.addr_ok) {
    pc_last_req := pc_out
  }
  io.pc_debug_pf_if := pc_last_req

}

class InsSufFetchBundle extends WithAllowin {
  val ins_ram_data: UInt = Input(UInt(64.W))

  val if_id_out         : FetchDecodeBundle = Output(new FetchDecodeBundle)
  val pc_debug_pf_if    : UInt              = Input(UInt(32.W))
  val flush             : Bool              = Input(Bool())
  val ins_ram_data_ok   : Bool              = Input(Bool())
  val ins_ram_data_valid: UInt              = Input(UInt(2.W))
  val fetch_state       : RamState.Type     = Input(RamState())
}

// 获取同步RAM的数据
class InsSufFetch extends Module {
  val io: InsSufFetchBundle = IO(new InsSufFetchBundle())

  // 取消sufetch模块的缓冲，直接放入fifo
  io.if_id_out.ins_if_id := io.ins_ram_data
  io.if_id_out.ins_valid_if_id := io.ins_ram_data_valid
  io.if_id_out.bus_valid := !reset.asBool() && !io.flush &&
    // 由缓冲寄存器读出
    io.ins_ram_data_ok && io.fetch_state === RamState.waiting_for_response
  io.if_id_out.pc_debug_if_id := io.pc_debug_pf_if
  io.this_allowin := !reset.asBool() && io.next_allowin
}

object InsFetch extends App {
  (new ChiselStage).emitVerilog(new InsPreFetch)
  (new ChiselStage).emitVerilog(new InsSufFetch)
}