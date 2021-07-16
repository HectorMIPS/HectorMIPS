package com.github.hectormips.tomasulo.ex_component

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._
import com.github.hectormips.tomasulo.{Config, CacheDataReadBufState}
import com.github.hectormips.tomasulo.cp0.ExceptionConst
import com.github.hectormips.tomasulo.ex_component.operation.MemoryOp

class MemoryComponent(config: Config) extends Component(config) {

  final val dcacheReadIO: DCacheReadIO = IO(Flipped(new DCacheReadIO()))
  val memAddr  : UInt          = io.in.bits.valA + io.in.bits.valB
  val memAddr4 : UInt          = Cat(memAddr(31, 2), 0.U(2.W))
  val memOffset: UInt          = (memAddr(1, 0) << 3).asUInt()
  val memOp    : MemoryOp.Type = MemoryOp(io.in.bits.operation(MemoryOp.getWidth - 1, 0))


  val memDataBuf: UInt = RegInit(UInt(32.W), init = 0.U(32.W))



  val memDataBufState: CacheDataReadBufState.Type = RegInit(CacheDataReadBufState(), init = CacheDataReadBufState.waiting_for_input)


  dcacheReadIO.addr := memAddr4
  dcacheReadIO.valid := io.in.valid && memDataBufState === CacheDataReadBufState.waiting_for_reading

  def hasException(memoryOp: MemoryOp.Type): Bool = {
    MuxCase(0.B, Seq(
      (memoryOp === MemoryOp.op_word) -> (memAddr(1, 0) =/= 0.U),
      (memoryOp === MemoryOp.op_hword_signed || memoryOp === MemoryOp.op_hword_unsigned) -> (memAddr(0) =/= 0.U)
    ))
  }

  when(dcacheReadIO.data_ok && memDataBufState === CacheDataReadBufState.waiting_for_input) {
    val memRawData = dcacheReadIO.rdata >> memOffset
    memDataBuf := memRawData
    switch(memOp) {
      is(MemoryOp.op_word) {
        memDataBuf := memRawData
      }
      is(MemoryOp.op_hword_unsigned) {
        memDataBuf := Cat(0.U(16.W), memRawData(15, 0))
      }
      is(MemoryOp.op_hword_signed) {
        memDataBuf := Cat(VecInit(Seq.fill(16)(memRawData(15))).asUInt(), memRawData(15, 0))
      }
      is(MemoryOp.op_byte_unsigned) {
        memDataBuf := Cat(0.U(24.W), memRawData(7, 0))
      }
      is(MemoryOp.op_byte_signed) {
        memDataBuf := Cat(VecInit(Seq.fill(24)(memRawData(7))).asUInt(), memRawData(7, 0))
      }
    }
    memDataBufState := CacheDataReadBufState.waiting_for_reading
  }
  when(io.out.ready && memDataBufState === CacheDataReadBufState.waiting_for_reading) {
    memDataBufState := CacheDataReadBufState.read_done
  }
  when(memDataBufState === CacheDataReadBufState.read_done) {
    memDataBufState := CacheDataReadBufState.waiting_for_input
  }

  io.in.ready := io.out.ready && memDataBufState === CacheDataReadBufState.read_done
  io.out.valid := memDataBufState === CacheDataReadBufState.waiting_for_reading
  io.out.bits.rob_target := io.in.bits.dest
  io.out.bits.value := memDataBuf
  io.out.bits.exceptionFlag := io.in.bits.exceptionFlag |
    Mux(hasException(memoryOp = memOp), ExceptionConst.EXCEPTION_BAD_RAM_ADDR_READ, 0.B)


  io.out.bits.writeLO := 0.B
  io.out.bits.writeHI := 0.B
  io.out.bits.readHI := 0.B
  io.out.bits.readLO := 0.B
  io.out.bits.writeHILO := 0.B

  io.out.bits.is_jump := 0.U
  io.out.bits.jump_success := DontCare
  io.out.bits.pred_success := DontCare
  io.out.bits.next_pc := DontCare
}
