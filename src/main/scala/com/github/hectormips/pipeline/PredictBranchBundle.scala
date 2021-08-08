package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.MuxCase

object BranchCondition extends ChiselEnum {
  val eq, ne, gtz, gez, ltz, lez, always = Value
}

class PredictBranchBundle extends Bundle {
  val jump_sel           : InsJumpSel.Type      = InsJumpSel()
  val jump_val           : Vec[UInt]            = Vec(4, UInt(32.W))
  // 由id_pf_bus.bus_valid控制
  val bus_valid          : Bool                 = Bool()
  val is_jump            : Bool                 = Bool()
  val predict_jump_target: UInt                 = UInt(32.W)
  val predict_jump_taken : Bool                 = Bool()
  val rf1                : SInt                 = SInt(32.W)
  val rf2                : SInt                 = SInt(32.W)
  val branch_condition   : BranchCondition.Type = BranchCondition()

  def defaults(): Unit = {
    jump_sel := InsJumpSel.seq_pc
    jump_val := VecInit(Seq.fill(4)(0.U))
    bus_valid := 0.B
    is_jump := 0.B
    predict_jump_target := 0.U
    predict_jump_taken := 0.B
    rf1 := 0.S
    rf2 := 0.S
    branch_condition := BranchCondition.always
  }

  def jumpTarget: UInt = {
    MuxCase(jump_val(3), Seq(
      (jump_sel === InsJumpSel.seq_pc) -> jump_val(3),
      (jump_sel === InsJumpSel.pc_cat_instr_index) -> jump_val(1),
      (jump_sel === InsJumpSel.regfile_read1) -> jump_val(2),
      (jump_sel === InsJumpSel.pc_add_offset) -> jump_val(0)
    ))
  }

  def hasPredictFail: Bool = {
    def checkBranchCondition(bc: BranchCondition.Type): Bool = {
      bc === branch_condition
    }

    val jump_taken: Bool = (checkBranchCondition(BranchCondition.eq) && rf1 === rf2) ||
      (checkBranchCondition(BranchCondition.ne) && rf1 =/= rf2) ||
      (checkBranchCondition(BranchCondition.gtz) && rf1 > 0.S) ||
      (checkBranchCondition(BranchCondition.gez) && rf1 >= 0.S) ||
      (checkBranchCondition(BranchCondition.ltz) && rf1 < 0.S) ||
      (checkBranchCondition(BranchCondition.lez) && rf1 <= 0.S) ||
      checkBranchCondition(BranchCondition.always)
    bus_valid && Mux(predict_jump_taken,
      !is_jump || !jump_taken || (jump_taken && predict_jump_target =/= jumpTarget),
      jump_taken)
  }
}
