package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.Mux1H
import chisel3.util._
import com.github.hectormips.RamState


// 通过译码阶段传入的参数
class DecodeExecuteBundle extends WithValidAndException {
  val alu_op_id_ex: AluOp.Type = AluOp()
  // 寄存器堆读端口1 2
  val pc_id_ex    : UInt       = UInt(32.W)
  val sa_32_id_ex : UInt       = UInt(32.W)
  val imm_32_id_ex: UInt       = UInt(32.W)

  val alu_src1_id_ex                  : UInt                 = UInt(32.W)
  val alu_src2_id_ex                  : UInt                 = UInt(32.W)
  // 直传id_ex_ms
  val mem_en_id_ex                    : Bool                 = Bool()
  val mem_wen_id_ex                   : UInt                 = UInt(4.W)
  val regfile_wsrc_sel_id_ex          : Bool                 = Bool()
  val regfile_waddr_sel_id_ex         : RegFileWAddrSel.Type = RegFileWAddrSel()
  val inst_rs_id_ex                   : UInt                 = UInt(5.W)
  val inst_rd_id_ex                   : UInt                 = UInt(5.W)
  val inst_rt_id_ex                   : UInt                 = UInt(5.W)
  val regfile_we_id_ex                : Bool                 = Bool()
  val pc_id_ex_debug                  : UInt                 = UInt(32.W)
  val mem_wdata_id_ex                 : UInt                 = UInt(32.W)
  val hi_wen                          : Bool                 = Bool()
  val lo_wen                          : Bool                 = Bool()
  val hilo_sel                        : HiloSel.Type         = HiloSel()
  val mem_data_sel_id_ex              : MemDataSel.Type      = MemDataSel() // 假设数据已经将指定地址对齐到最低位
  val mem_rdata_extend_is_signed_id_ex: Bool                 = Bool()
  val cp0_wen_id_ex                   : Bool                 = Bool()
  val cp0_addr_id_ex                  : UInt                 = UInt(5.W)
  val cp0_sel_id_ex                   : UInt                 = UInt(3.W)
  val regfile_wdata_from_cp0_id_ex    : Bool                 = Bool()
  val overflow_detection_en           : Bool                 = Bool()
  val ins_eret                        : Bool                 = Bool()


  val is_delay_slot: Bool = Bool()

  override def defaults(): Unit = {
    alu_op_id_ex := AluOp.op_add
    pc_id_ex := 0.U
    sa_32_id_ex := 0.U
    imm_32_id_ex := 0.U

    alu_src1_id_ex := 0.U
    alu_src2_id_ex := 0.U
    mem_en_id_ex := 0.B
    mem_wen_id_ex := 0.B
    regfile_wsrc_sel_id_ex := 0.B
    regfile_waddr_sel_id_ex := RegFileWAddrSel.inst_rt

    inst_rs_id_ex := 0.U
    inst_rd_id_ex := 0.U
    inst_rt_id_ex := 0.U
    regfile_we_id_ex := 0.B
    pc_id_ex_debug := 0.U
    mem_wdata_id_ex := 0.U

    hi_wen := 0.U
    lo_wen := 0.U
    hilo_sel := HiloSel.hi

    mem_data_sel_id_ex := MemDataSel.word
    mem_rdata_extend_is_signed_id_ex := 0.B

    cp0_wen_id_ex := 0.B
    cp0_addr_id_ex := 0.U
    cp0_sel_id_ex := 0.U
    regfile_wdata_from_cp0_id_ex := 0.B
    overflow_detection_en := 0.B
    ins_eret := 0.B
    is_delay_slot := 0.B
    super.defaults()
  }
}

// 由于mtc0 mfc0指令均在写回阶段才读、写cp0寄存器，因此如果例外需要读、写cp0寄存器，需要等待后方流水完成写回
class CP0HazardBypass extends Bundle with WithValid {
  val cp0_en    : Bool = Bool() // 是否需要使用cp0寄存器
  val cp0_ip_wen: Bool = Bool() // 写入的域含ip时 强制暂停
}


object DividerState extends ChiselEnum {
  val waiting    : Type = Value(0.U)
  val inputting  : Type = Value(1.U)
  val handshaking: Type = Value(2.U)
  val calculating: Type = Value(4.U)
}

// 由cp0传给执行阶段的信息
class CP0ExecuteBundle extends Bundle {
  val status_exl: Bool = Bool()
  val status_ie : Bool = Bool()
  val epc       : UInt = UInt(32.W)
}

class InsExecuteBundle extends WithAllowin {
  val cp0_ex_in              : CP0ExecuteBundle    = Input(new CP0ExecuteBundle)
  val ex_cp0_out             : ExecuteCP0Bundle    = Output(new ExecuteCP0Bundle)
  val id_ex_in               : DecodeExecuteBundle = Input(new DecodeExecuteBundle)
  val cp0_hazard_bypass_ms_ex: CP0HazardBypass     = Input(new CP0HazardBypass)
  val cp0_hazard_bypass_wb_ex: CP0HazardBypass     = Input(new CP0HazardBypass)

  // 传递给访存的输出
  val ex_ms_out: ExecuteMemoryBundle = Output(new ExecuteMemoryBundle)

  // 传给data ram的使能信号和数据信号
  val mem_en   : Bool = Output(Bool())
  val mem_wen  : UInt = Output(UInt(4.W))
  val mem_addr : UInt = Output(UInt(32.W))
  val mem_wdata: UInt = Output(UInt(32.W))
  val mem_size : UInt = Output(UInt(32.W))

  val bypass_ex_id: BypassMsgBundle = Output(new BypassMsgBundle)

  val hi_out: UInt = Output(UInt(32.W))
  val hi_in : UInt = Input(UInt(32.W))
  val hi_wen: Bool = Output(Bool())
  val lo_out: UInt = Output(UInt(32.W))
  val lo_in : UInt = Input(UInt(32.W))
  val lo_wen: Bool = Output(Bool())

  val divider_required   : Bool          = Output(Bool())
  val multiplier_required: Bool          = Output(Bool())
  val divider_tready     : Bool          = Output(Bool())
  val divider_tvalid     : Bool          = Input(Bool())
  val data_ram_addr_ok   : Bool          = Input(Bool())
  val data_ram_state     : RamState.Type = Input(RamState())

  // 流水线清空使能信号
  val pipeline_flush: Bool = Output(Bool())

  // 回传给预取阶段的例外跳转使能
  val to_exception_service_en_ex_pf: Bool = Output(Bool())
  val to_epc_en_ex_pf              : Bool = Output(Bool())
  val cp0_status_im                : UInt = Input(UInt(8.W))
  val cp0_cause_ip                 : UInt = Input(UInt(8.W))
}


class InsExecute extends Module {
  val io                 : InsExecuteBundle = IO(new InsExecuteBundle)
  val alu_out            : UInt             = Wire(UInt(32.W))
  val src1               : UInt             = Wire(UInt(32.W))
  val src2               : UInt             = Wire(UInt(32.W))
  val divider_required   : Bool             = io.id_ex_in.alu_op_id_ex === AluOp.op_divu ||
    io.id_ex_in.alu_op_id_ex === AluOp.op_div
  val multiplier_required: Bool             = io.id_ex_in.alu_op_id_ex === AluOp.op_mult ||
    io.id_ex_in.alu_op_id_ex === AluOp.op_multu
  val overflow_occurred  : Bool             = Wire(Bool())
  val flush              : Bool             = Wire(Bool())
  src1 := io.id_ex_in.alu_src1_id_ex
  src2 := io.id_ex_in.alu_src2_id_ex

  val calc_done : Bool             = RegInit(init = 0.B)
  val multiplier: CommonMultiplier = Module(new CommonMultiplier)
  multiplier.io.mult1 := src1
  multiplier.io.mult2 := src2
  multiplier.io.req := multiplier_required && multiplier.io.state === MultiplierState.waiting_for_input && !calc_done
  multiplier.io.is_signed := io.id_ex_in.alu_op_id_ex === AluOp.op_mult
  multiplier.io.flush := flush


  val divider: CommonDivider = Module(new CommonDivider)
  divider.io.divisor := src2
  divider.io.dividend := src1
  divider.io.is_signed := io.id_ex_in.alu_op_id_ex === AluOp.op_div
  divider.io.tvalid := io.divider_tvalid && !calc_done
  io.divider_tready := divider.io.tready && !calc_done
  io.divider_required := divider_required
  io.multiplier_required := multiplier_required
  when(io.this_allowin || flush) {
    calc_done := 0.B
  }.elsewhen(divider.io.out_valid || multiplier.io.res_valid) {
    calc_done := 1.B
  }

  def mult_div_sel(mult_res: UInt, div_res: UInt): UInt = {
    MuxCase(src1, Seq(
      (io.id_ex_in.alu_op_id_ex === AluOp.op_mult | io.id_ex_in.alu_op_id_ex === AluOp.op_multu) -> mult_res,
      (io.id_ex_in.alu_op_id_ex === AluOp.op_div | io.id_ex_in.alu_op_id_ex === AluOp.op_divu) -> div_res,
    ))
  }


  io.hi_out := mult_div_sel(multiplier.io.mult_res_63_32, divider.io.remainder)
  io.hi_wen := MuxCase(1.B, Seq(
    divider_required -> divider.io.out_valid,
    multiplier_required -> multiplier.io.res_valid
  )) && io.id_ex_in.hi_wen && !flush
  io.lo_out := mult_div_sel(multiplier.io.mult_res_31_0, divider.io.quotient)
  io.lo_wen := MuxCase(1.B, Seq(
    divider_required -> divider.io.out_valid,
    multiplier_required -> multiplier.io.res_valid
  )) && io.id_ex_in.lo_wen && !flush

  alu_out := Mux(io.id_ex_in.hilo_sel === HiloSel.hi, io.hi_in, io.lo_in)
  // 使用带有保护位的补码加法来实现溢出的检测
  val src_1_e: UInt = Wire(UInt(33.W))
  val src_2_e: UInt = Wire(UInt(33.W))
  src_1_e := Cat(src1(31), src1)
  src_2_e := Cat(src2(31), src2)
  overflow_occurred := 0.B
  switch(io.id_ex_in.alu_op_id_ex) {
    is(AluOp.op_add) {
      val alu_out_e = src_1_e + src_2_e
      overflow_occurred := alu_out_e(32) ^ alu_out_e(31)
      alu_out := alu_out_e(31, 0)
    }
    is(AluOp.op_sub) {
      val src2_neg: UInt = -src2
      src_2_e := Cat(src2_neg(31), src2_neg)
      val alu_out_e = src_1_e + src_2_e
      overflow_occurred := (alu_out_e(32) ^ alu_out_e(31))
      alu_out := alu_out_e(31, 0)
    }
    is(AluOp.op_slt) {
      alu_out := src1.asSInt() < src2.asSInt()
    }
    is(AluOp.op_sltu) {
      alu_out := src1 < src2
    }
    is(AluOp.op_and) {
      alu_out := src1 & src2
    }
    is(AluOp.op_nor) {
      alu_out := ~(src1 | src2)
    }
    is(AluOp.op_or) {
      alu_out := src1 | src2
    }
    is(AluOp.op_xor) {
      alu_out := src1 ^ src2
    }
    is(AluOp.op_sll) {
      alu_out := src2 << src1(4, 0) // 由sa指定位移数（src1）
    }
    is(AluOp.op_srl) {
      alu_out := src2 >> src1(4, 0) // 由sa指定位移位数（src1）
    }
    is(AluOp.op_sra) {
      alu_out := (src2.asSInt() >> src1(4, 0)).asUInt()
    }
    is(AluOp.op_lui) {
      alu_out := src2 << 16.U
    }
    is(AluOp.op_hi_dir) {
      alu_out := io.hi_in
    }
    is(AluOp.op_lo_dir) {
      alu_out := io.lo_in
    }
    is(AluOp.op_mult) {
      alu_out := multiplier.io.mult_res_31_0
    }
  }

  val bus_valid: Bool = Wire(Bool())
  bus_valid := io.id_ex_in.bus_valid && !reset.asBool() && !flush

  io.ex_ms_out.alu_val_ex_ms := alu_out
  val src_sum     : UInt            = src1 + src2
  val mem_wen     : Bool            = io.id_ex_in.bus_valid && io.id_ex_in.mem_wen_id_ex =/= 0.U
  val mem_data_sel: MemDataSel.Type = io.id_ex_in.mem_data_sel_id_ex
  // 直出内存地址，连接到sram上
  io.mem_addr := MuxCase(src_sum & 0xfffffffcL.U, Seq(
    (mem_wen && mem_data_sel === MemDataSel.hword) -> (src_sum & 0xfffffffeL.U),
    (mem_wen && mem_data_sel === MemDataSel.byte) -> src_sum
  ))


  io.ex_ms_out.regfile_wsrc_sel_ex_ms := io.id_ex_in.regfile_wsrc_sel_id_ex
  io.ex_ms_out.regfile_waddr_sel_ex_ms := io.id_ex_in.regfile_waddr_sel_id_ex
  io.ex_ms_out.inst_rd_ex_ms := io.id_ex_in.inst_rd_id_ex
  io.ex_ms_out.inst_rt_ex_ms := io.id_ex_in.inst_rt_id_ex
  io.ex_ms_out.regfile_we_ex_ms := io.id_ex_in.regfile_we_id_ex
  io.ex_ms_out.pc_ex_ms_debug := io.id_ex_in.pc_id_ex_debug
  io.ex_ms_out.mem_rdata_offset := src_sum(1, 0)
  io.ex_ms_out.mem_rdata_sel_ex_ms := io.id_ex_in.mem_data_sel_id_ex
  io.ex_ms_out.mem_rdata_extend_is_signed_ex_ms := io.id_ex_in.mem_rdata_extend_is_signed_id_ex
  // 当指令为从内存中取出存放至寄存器堆中时，ex阶段无法得出结果，前递通路有效，数据无效
  io.bypass_ex_id.bus_valid := bus_valid && io.id_ex_in.regfile_we_id_ex
  io.bypass_ex_id.data_valid := io.id_ex_in.bus_valid && !io.id_ex_in.regfile_wsrc_sel_id_ex
  // 写寄存器来源为内存，并且此时ex阶段有效
  io.bypass_ex_id.reg_data := alu_out
  io.bypass_ex_id.reg_addr := Mux1H(Seq(
    (io.id_ex_in.regfile_waddr_sel_id_ex === RegFileWAddrSel.inst_rd) -> io.id_ex_in.inst_rd_id_ex,
    (io.id_ex_in.regfile_waddr_sel_id_ex === RegFileWAddrSel.inst_rt) -> io.id_ex_in.inst_rt_id_ex,
    (io.id_ex_in.regfile_waddr_sel_id_ex === RegFileWAddrSel.const_31) -> 31.U))
  io.bypass_ex_id.force_stall := io.id_ex_in.regfile_wdata_from_cp0_id_ex
  // 至此所有可能会拉例外的情况都已经发生，我们选择直接在执行级和CP0交互处理这些例外
  val exception_flags: UInt = io.id_ex_in.exception_flags |
    Mux(overflow_occurred && io.id_ex_in.overflow_detection_en, ExceptionConst.EXCEPTION_INT_OVERFLOW, 0.U) |
    Mux(io.id_ex_in.mem_en_id_ex && io.id_ex_in.mem_en_id_ex &&
      ((mem_data_sel === MemDataSel.word && src_sum(1, 0) =/= 0.U) ||
        mem_data_sel === MemDataSel.hword && src_sum(0) =/= 0.U),
      // 写使能非零说明是写地址异常
      Mux(io.id_ex_in.mem_wen_id_ex =/= 0.U, ExceptionConst.EXCEPTION_BAD_RAM_ADDR_WRITE,
        ExceptionConst.EXCEPTION_BAD_RAM_ADDR_READ), 0.U)


  val ms_cp0_hazard: Bool = io.cp0_hazard_bypass_ms_ex.bus_valid && io.cp0_hazard_bypass_ms_ex.cp0_en
  val wb_cp0_hazard: Bool = io.cp0_hazard_bypass_wb_ex.bus_valid && io.cp0_hazard_bypass_wb_ex.cp0_en

  // 当ms、wb的指令正在写cp0的ip域时，暂停流水线
  val ms_cp0_ip0_wen: Bool = io.cp0_hazard_bypass_ms_ex.bus_valid && io.cp0_hazard_bypass_ms_ex.cp0_ip_wen
  val wb_cp0_ip0_wen: Bool = io.cp0_hazard_bypass_wb_ex.bus_valid && io.cp0_hazard_bypass_wb_ex.cp0_ip_wen


  val exception_occur    : Bool = io.id_ex_in.bus_valid && exception_flags =/= 0.U // 当输入有效且例外标识不为0则发生例外
  val interrupt_occur    : Bool = (io.cp0_cause_ip & io.cp0_status_im) =/= 0.U
  val eret_occur         : Bool = io.id_ex_in.bus_valid && io.id_ex_in.ins_eret
  val exception_available: Bool = !io.cp0_ex_in.status_exl // exl为0时才能执行例外程序
  val interrupt_available: Bool = !io.cp0_ex_in.status_exl && io.cp0_ex_in.status_ie

  io.mem_wdata := io.id_ex_in.mem_wdata_id_ex
  io.mem_en := io.id_ex_in.mem_en_id_ex && !flush && io.next_allowin && bus_valid
  io.mem_wen := io.id_ex_in.mem_wen_id_ex &
    VecInit(Seq.fill(4)(!((exception_available && exception_occur) || (interrupt_occur && interrupt_available)) &&
      bus_valid)).asUInt()
  io.mem_size := MuxCase(0.U, Seq(
    (mem_data_sel === MemDataSel.word) -> 2.U,
    (mem_data_sel === MemDataSel.hword) -> 1.U,
    (mem_data_sel === MemDataSel.byte) -> 0.U
  ))

  val ready_go: Bool = !ms_cp0_ip0_wen && !wb_cp0_ip0_wen &&
    Mux(divider_required, divider.io.out_valid, 1.B) &&
    Mux(multiplier_required, multiplier.io.res_valid, 1.B) &&
    Mux((exception_occur && exception_available) || (interrupt_occur && interrupt_available), !ms_cp0_hazard && !wb_cp0_hazard, 1.B) &&
    Mux(io.id_ex_in.bus_valid && io.id_ex_in.mem_en_id_ex,
      Mux(io.id_ex_in.mem_wen_id_ex =/= 0.U, io.data_ram_addr_ok && io.data_ram_state === RamState.requesting, io.data_ram_state === RamState.waiting_for_response),
      1.B)
  io.this_allowin := ready_go && io.next_allowin && !reset.asBool()
  io.ex_ms_out.bus_valid := ready_go && bus_valid

  io.ex_ms_out.cp0_addr_ex_ms := io.id_ex_in.cp0_addr_id_ex
  io.ex_ms_out.cp0_wen_ex_ms := io.id_ex_in.cp0_wen_id_ex
  io.ex_ms_out.cp0_sel_ex_ms := io.id_ex_in.cp0_sel_id_ex
  io.ex_ms_out.regfile_wdata_from_cp0_ex_ms := io.id_ex_in.regfile_wdata_from_cp0_id_ex

  io.ex_cp0_out.is_delay_slot := io.id_ex_in.is_delay_slot // 延迟槽指令是接在分支指令之后一定会执行的那条指令


  io.ex_cp0_out.exception_occur := (exception_occur || interrupt_occur) && ready_go
  // 如果是软中断导致的例外，将pc指向下一条指令
  io.ex_cp0_out.pc := Mux(interrupt_occur && interrupt_available, io.id_ex_in.pc_id_ex_debug + 4.U,
    io.id_ex_in.pc_id_ex_debug)
  io.ex_cp0_out.badvaddr := MuxCase(io.id_ex_in.pc_id_ex_debug, Seq(
    (exception_flags(0) || exception_flags(1) || exception_flags(2) ||
      exception_flags(3) || exception_flags(4)) -> io.id_ex_in.pc_id_ex_debug,
    // 只有当内存地址取值出错的时候，badvaddr返回的是内存对应的地址而不是指令地址
    (exception_flags(5) || exception_flags(6)) -> src_sum
  ))
  io.ex_cp0_out.eret_occur := eret_occur && ready_go
  io.ex_cp0_out.exc_code := MuxCase(0.U, Seq(
    (interrupt_occur && interrupt_available) -> ExcCodeConst.INT,
    exception_flags(0) -> ExcCodeConst.ADEL,
    exception_flags(1) -> ExcCodeConst.RI,
    exception_flags(2) -> ExcCodeConst.OV,
    exception_flags(3) -> ExcCodeConst.BP,
    exception_flags(4) -> ExcCodeConst.SYS,
    exception_flags(5) -> ExcCodeConst.ADES,
    exception_flags(6) -> ExcCodeConst.ADEL
  ))
  // 例外发生且有效（exl != 1）的时候需要流水线执行以下操作
  //  排空执行阶段以及之前的流水线
  //  预取阶段下一条指令位置为中断服务程序
  flush := ((interrupt_occur && interrupt_available) || (exception_available && exception_occur) ||
    (io.id_ex_in.bus_valid && io.id_ex_in.ins_eret)) && ready_go
  io.pipeline_flush := flush
  io.to_exception_service_en_ex_pf := ((exception_available && exception_occur) ||
    (interrupt_occur && interrupt_available)) && ready_go
  io.to_epc_en_ex_pf := (io.id_ex_in.bus_valid && io.id_ex_in.ins_eret) && ready_go

}