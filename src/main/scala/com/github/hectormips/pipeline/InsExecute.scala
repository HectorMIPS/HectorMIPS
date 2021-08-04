package com.github.hectormips.pipeline

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.stage.ChiselStage
import chisel3.util.Mux1H
import chisel3.util._
import com.github.hectormips.RamState
import com.github.hectormips.pipeline.cp0.{ExcCodeConst, ExceptionConst, ExecuteCP0Bundle}
import com.github.hectormips.pipeline.issue.{Alu, AluEx, AluIO, AluOut}


// 通过译码阶段传入的参数
class DecodeExecuteBundle extends WithVEI {
  val alu_op_id_ex: AluOp.Type = AluOp()
  val sa_32_id_ex : UInt       = UInt(32.W)
  val imm_32_id_ex: UInt       = UInt(32.W)

  val alu_src1_id_ex                  : UInt                 = UInt(32.W)
  val alu_src2_id_ex                  : UInt                 = UInt(32.W)
  // 直传id_ex_ms
  val mem_en_id_ex                    : Bool                 = Bool()
  val mem_wen_id_ex                   : Bool                 = Bool()
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
  val src_use_hilo                    : Bool                 = Bool()


  val is_delay_slot: Bool = Bool()

  override def defaults(): Unit = {
    alu_op_id_ex := AluOp.op_add
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
    src_use_hilo := 0.B
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
  val status_exl   : Bool = Input(Bool())
  val status_ie    : Bool = Input(Bool())
  val epc          : UInt = Input(UInt(32.W))
  val cp0_status_im: UInt = Input(UInt(8.W))
  val cp0_cause_ip : UInt = Input(UInt(8.W))
}

class ExecuteRamBundle extends Bundle {
  val mem_en   : Bool = Output(Bool())
  val mem_wen  : UInt = Output(Bool())
  val mem_addr : UInt = Output(UInt(32.W))
  val mem_wdata: UInt = Output(UInt(32.W))
  val mem_size : UInt = Output(UInt(2.W))
}

class ExecuteHILOBundle extends Bundle {
  val hi_out: UInt = Output(UInt(32.W))
  val hi_in : UInt = Input(UInt(32.W))
  val hi_wen: Bool = Output(Bool())
  val lo_out: UInt = Output(UInt(32.W))
  val lo_in : UInt = Input(UInt(32.W))
  val lo_wen: Bool = Output(Bool())
}


class InsExecuteBundle extends WithAllowin {
  val id_ex_in               : Vec[DecodeExecuteBundle] = Input(Vec(2, new DecodeExecuteBundle))
  val cp0_hazard_bypass_ms_ex: Vec[CP0HazardBypass]     = Input(Vec(2, new CP0HazardBypass))
  val cp0_hazard_bypass_wb_ex: Vec[CP0HazardBypass]     = Input(Vec(2, new CP0HazardBypass))
  val ex_ram_out             : ExecuteRamBundle         = new ExecuteRamBundle
  val ex_ms_out              : Vec[ExecuteMemoryBundle] = Output(Vec(2, new ExecuteMemoryBundle))
  val ex_cp0_out             : ExecuteCP0Bundle         = Output(new ExecuteCP0Bundle)
  val bypass_ex_id           : Vec[BypassMsgBundle]     = Output(Vec(2, new BypassMsgBundle))
  val cp0_ex_in              : CP0ExecuteBundle         = Input(new CP0ExecuteBundle)
  val ex_hilo                : ExecuteHILOBundle        = new ExecuteHILOBundle
  val ex_pf_out              : ExecutePrefetchBundle    = Output(new ExecutePrefetchBundle)
  val pipeline_flush         : Bool                     = Output(Bool())
  val data_ram_addr_ok       : Bool                     = Input(Bool())
  // 执行阶段是否*至少*完成了其应该完成的任务
  val ready_go               : Bool                     = Output(Bool())
}


class InsExecute extends Module {
  val io                          : InsExecuteBundle = IO(new InsExecuteBundle)
  val flush                       : Bool             = Wire(Bool())
  val this_allowin                : Bool             = Wire(Bool())
  val alu                         : Vec[AluIO]       = VecInit(Seq(Module(new AluEx).io, Module(new Alu).io))
  // alu输出的结果仍然按照指令原本的顺序
  val alu_out                     : Vec[AluOut]      = Wire(Vec(2, new AluOut))
  val ins2_op                     : AluOp.Type       = io.id_ex_in(1).alu_op_id_ex
  // 默认将index=0的送至alu_ex，1的送至alu，如果1有效并且需要使用乘除法器，则反转指令（仅限内部）
  val order_flipped               : Bool             = io.id_ex_in(1).bus_valid && (ins2_op === AluOp.op_div || ins2_op === AluOp.op_divu ||
    ins2_op === AluOp.op_mult || ins2_op === AluOp.op_multu)
  val exception_occur             : Vec[Bool]        = Wire(Vec(2, Bool()))
  val exception_flags             : Vec[UInt]        = Wire(Vec(2, UInt(ExceptionConst.EXCEPTION_FLAG_WIDTH.W)))
  val eret_occur                  : Bool             = io.id_ex_in(0).bus_valid && io.id_ex_in(0).ins_eret
  val interrupt_occur             : Bool             = !eret_occur &&
    (io.cp0_ex_in.cp0_cause_ip & io.cp0_ex_in.cp0_status_im) =/= 0.U
  val interrupt_available         : Bool             = io.cp0_ex_in.status_ie && !io.cp0_ex_in.status_exl
  val exception_index             : UInt             = MuxCase(0.U, Seq(
    ((exception_occur(0) || (interrupt_occur && interrupt_available)) && io.id_ex_in(0).bus_valid) -> 0.U,
    (exception_occur(1) && io.id_ex_in(1).bus_valid) -> 1.U
  ))
  val exception_or_interruption_en: Bool             = exception_occur(0) || exception_occur(1) ||
    (interrupt_occur && interrupt_available)
  val exception_on_inst0          : Bool             = (interrupt_occur && interrupt_available) ||
    (exception_occur(0) && exception_index === 0.U)
  val exception_on_inst1          : Bool             = (interrupt_occur && interrupt_available) ||
    (exception_occur(1) && exception_index === 1.U)
  // 内存请求状态寄存器，当ex阶段需要发起内存请求并且已经发送了请求之后才能继续前进

  val data_ram_index: UInt = Mux(io.id_ex_in(0).bus_valid && io.id_ex_in(0).mem_en_id_ex, 0.U, 1.U)
  // 后方流水线是否写cp0
  val ms_cp0_hazard : Bool = (io.cp0_hazard_bypass_ms_ex(0).bus_valid && io.cp0_hazard_bypass_ms_ex(0).cp0_en) ||
    (io.cp0_hazard_bypass_ms_ex(1).bus_valid && io.cp0_hazard_bypass_ms_ex(1).cp0_en)
  val wb_cp0_hazard : Bool = (io.cp0_hazard_bypass_wb_ex(0).bus_valid && io.cp0_hazard_bypass_wb_ex(0).cp0_en) ||
    (io.cp0_hazard_bypass_wb_ex(1).bus_valid && io.cp0_hazard_bypass_wb_ex(1).cp0_en)
  // 当ms、wb的指令正在写cp0的ip域时，暂停流水线
  val ms_cp0_ip0_wen: Bool = (io.cp0_hazard_bypass_ms_ex(0).bus_valid && io.cp0_hazard_bypass_ms_ex(0).cp0_ip_wen) ||
    (io.cp0_hazard_bypass_ms_ex(1).bus_valid && io.cp0_hazard_bypass_ms_ex(1).cp0_ip_wen)
  val wb_cp0_ip0_wen: Bool = (io.cp0_hazard_bypass_wb_ex(0).bus_valid && io.cp0_hazard_bypass_wb_ex(0).cp0_ip_wen) ||
    (io.cp0_hazard_bypass_wb_ex(1).bus_valid && io.cp0_hazard_bypass_wb_ex(1).cp0_ip_wen)

  val ready_go: Bool = !ms_cp0_ip0_wen && !wb_cp0_ip0_wen &&
    Mux(io.id_ex_in(0).bus_valid && !exceptionShielded(0), alu(0.U ^ order_flipped).out.out_valid, 1.B) &&
    Mux(io.id_ex_in(1).bus_valid && !exceptionShielded(1), alu(1.U ^ order_flipped).out.out_valid, 1.B) &&
    Mux(exception_occur(exception_index) || (interrupt_occur && interrupt_available),
      !ms_cp0_hazard && !wb_cp0_hazard, 1.B) &&
    // 当一条指令返回了addr_ok之后即可以进入下一个阶段
    Mux(io.id_ex_in(data_ram_index).bus_valid && io.id_ex_in(data_ram_index).mem_en_id_ex &&
      Mux(data_ram_index === 0.U, !exception_on_inst0, !exception_on_inst0 && !exception_on_inst1),
      io.data_ram_addr_ok, 1.B)

  val in_bus_valid: Bool = io.id_ex_in(0).bus_valid && !reset.asBool() && (!flush || eret_occur)
  for (i_alu <- 0 to 1) {
    val i_in: UInt = (i_alu.B ^ order_flipped).asUInt()
    alu(i_alu).in.alu_op := io.id_ex_in(i_in).alu_op_id_ex
    alu(i_alu).in.src1 := Mux(io.id_ex_in(i_in).src_use_hilo,
      Mux(io.id_ex_in(i_in).hilo_sel === HiloSel.hi, io.ex_hilo.hi_in, io.ex_hilo.lo_in),
      io.id_ex_in(i_in).alu_src1_id_ex)
    alu(i_alu).in.src2 := io.id_ex_in(i_in).alu_src2_id_ex
    alu(i_alu).in.en := io.id_ex_in(i_in).bus_valid && !exceptionShielded(i_in)
    alu(i_alu).in.flush := flush
    alu(i_alu).in.lo := io.ex_hilo.lo_in
    alu(i_alu).in.ex_allowin := this_allowin
  }
  alu_out(0) := alu(order_flipped).out
  alu_out(1) := alu(order_flipped ^ 1.B).out

  // 判断例外屏蔽
  // 若第一条指令产生例外会屏蔽两条指令
  // 否则只会屏蔽第二条指令
  def exceptionShielded(index: UInt): Bool = {
    io.id_ex_in(index).bus_valid && Mux(exception_occur(0), 1.B, exception_occur(1) && index === 1.U)
  }

  def exceptionShielded(index: Int): Bool = {
    exceptionShielded(index.U)
  }


  // 如果第一条指令发生例外，清空ex阶段的所有指令
  // 如果第二条指令发生例外，仅将第一条无效化
  flush := ((interrupt_occur && interrupt_available) || exception_occur(exception_index) ||
    eret_occur) && ready_go && io.id_ex_in(exception_index).bus_valid
  for (i <- 0 to 1) {
    exception_flags(i) := io.id_ex_in(i).exception_flags |
      Mux(alu_out(i).overflow_flag && io.id_ex_in(i).overflow_detection_en, ExceptionConst.EXCEPTION_INT_OVERFLOW, 0.U) |
      Mux(io.id_ex_in(i).mem_en_id_ex &&
        ((io.id_ex_in(i).mem_data_sel_id_ex === MemDataSel.word && alu_out(i).alu_sum(1, 0) =/= 0.U) ||
          io.id_ex_in(i).mem_data_sel_id_ex === MemDataSel.hword && alu_out(i).alu_sum(0) =/= 0.U),
        // 写使能非零说明是写地址异常
        Mux(io.id_ex_in(i).mem_wen_id_ex =/= 0.U, ExceptionConst.EXCEPTION_BAD_RAM_ADDR_WRITE,
          ExceptionConst.EXCEPTION_BAD_RAM_ADDR_READ), 0.U)
    exception_occur(i) := exception_flags(i) =/= 0.U

  }
  // dram操作
  io.ex_ram_out.mem_en := io.id_ex_in(data_ram_index).bus_valid &&
    io.id_ex_in(data_ram_index).mem_en_id_ex && !exceptionShielded(data_ram_index)
  io.ex_ram_out.mem_addr := alu_out(data_ram_index).alu_sum
  io.ex_ram_out.mem_size := MuxCase(0.U, Seq(
    (io.id_ex_in(data_ram_index).mem_data_sel_id_ex === MemDataSel.word) -> 2.U,
    (io.id_ex_in(data_ram_index).mem_data_sel_id_ex === MemDataSel.hword) -> 1.U,
    (io.id_ex_in(data_ram_index).mem_data_sel_id_ex === MemDataSel.byte) -> 0.U
  ))
  val wdata_offset: UInt = Wire(UInt(5.W))
  wdata_offset := alu_out(data_ram_index).alu_sum(1, 0) << 3.U
  // 实际写入数据与size、addr相关，并非永远都选择低位有效
  io.ex_ram_out.mem_wdata := io.id_ex_in(data_ram_index).mem_wdata_id_ex << wdata_offset
  io.ex_ram_out.mem_wen := io.id_ex_in(data_ram_index).mem_wen_id_ex &&
    !exceptionShielded(data_ram_index) && io.id_ex_in(data_ram_index).bus_valid

  // 送给memory阶段的输出
  for (i <- 0 to 1) {
    io.ex_ms_out(i).alu_val_ex_ms := alu_out(i).alu_res
    io.ex_ms_out(i).regfile_wsrc_sel_ex_ms := io.id_ex_in(i).regfile_wsrc_sel_id_ex
    io.ex_ms_out(i).regfile_waddr_sel_ex_ms := io.id_ex_in(i).regfile_waddr_sel_id_ex
    io.ex_ms_out(i).inst_rd_ex_ms := io.id_ex_in(i).inst_rd_id_ex
    io.ex_ms_out(i).inst_rt_ex_ms := io.id_ex_in(i).inst_rt_id_ex
    io.ex_ms_out(i).regfile_we_ex_ms := io.id_ex_in(i).regfile_we_id_ex
    io.ex_ms_out(i).pc_ex_ms_debug := io.id_ex_in(i).pc_id_ex_debug
    io.ex_ms_out(i).mem_rdata_offset := alu_out(i).alu_sum(1, 0)
    io.ex_ms_out(i).mem_rdata_sel_ex_ms := io.id_ex_in(i).mem_data_sel_id_ex
    io.ex_ms_out(i).mem_rdata_extend_is_signed_ex_ms := io.id_ex_in(i).mem_rdata_extend_is_signed_id_ex
    io.ex_ms_out(i).cp0_addr_ex_ms := io.id_ex_in(i).cp0_addr_id_ex
    io.ex_ms_out(i).cp0_wen_ex_ms := io.id_ex_in(i).cp0_wen_id_ex
    io.ex_ms_out(i).cp0_sel_ex_ms := io.id_ex_in(i).cp0_sel_id_ex
    io.ex_ms_out(i).regfile_wdata_from_cp0_ex_ms := io.id_ex_in(i).regfile_wdata_from_cp0_id_ex
    io.ex_ms_out(i).mem_req := io.id_ex_in(i).mem_en_id_ex
    io.ex_ms_out(i).mem_wen := io.id_ex_in(i).mem_wen_id_ex
    // 如果发生例外或者中断并且报在第二条指令上，那么只发射第一条指令
    io.ex_ms_out(i).issue_num := Mux(exception_or_interruption_en && exception_index === 1.U,
      1.U, io.id_ex_in(i).issue_num)
    io.ex_ms_out(i).exception_flags := DontCare
    // 如果只有第二条指令发生例外，则只无效化第二条指令的输出
    io.ex_ms_out(i).bus_valid := io.id_ex_in(i).bus_valid && !(exception_on_inst1 && i.U === 1.U) &&
      !exception_on_inst0 && !reset.asBool() && !eret_occur && ready_go
  }

  // 送给cp0的输出
  io.ex_cp0_out.is_delay_slot := io.id_ex_in(exception_index).is_delay_slot
  io.ex_cp0_out.exception_occur := (exception_occur(exception_index) || (interrupt_occur && interrupt_available)) &&
    io.id_ex_in(exception_index).bus_valid && ready_go
  // 如果是软中断导致的例外，将pc指向下一条指令
  // 如果是硬中断导致的例外，pc指向最后一条没有生效的指令
  // 由于只有前方流水线会被清空，所以最后一条没有生效的指令就是当前的指令
  io.ex_cp0_out.pc := io.id_ex_in(exception_index).pc_id_ex_debug
  io.ex_cp0_out.badvaddr := MuxCase(io.id_ex_in(exception_index).pc_id_ex_debug, Seq(
    (exception_flags(exception_index)(0) || exception_flags(exception_index)(1) || exception_flags(exception_index)(2) ||
      exception_flags(exception_index)(3) || exception_flags(exception_index)(4)) -> io.id_ex_in(exception_index).pc_id_ex_debug,
    // 只有当内存地址取值出错的时候，badvaddr返回的是内存对应的地址而不是指令地址
    (exception_flags(exception_index)(5) || exception_flags(exception_index)(6)) -> alu_out(exception_index).alu_sum
  ))
  io.ex_cp0_out.eret_occur := eret_occur && ready_go
  io.ex_cp0_out.exc_code := MuxCase(0.U, Seq(
    (interrupt_occur && interrupt_available) -> ExcCodeConst.INT,
    exception_flags(exception_index)(0) -> ExcCodeConst.ADEL,
    exception_flags(exception_index)(1) -> ExcCodeConst.RI,
    exception_flags(exception_index)(2) -> ExcCodeConst.OV,
    exception_flags(exception_index)(3) -> ExcCodeConst.BP,
    exception_flags(exception_index)(4) -> ExcCodeConst.SYS,
    exception_flags(exception_index)(5) -> ExcCodeConst.ADES,
    exception_flags(exception_index)(6) -> ExcCodeConst.ADEL
  ))

  // 给译码的前递
  for (i <- 0 to 1) {
    io.bypass_ex_id(i).bus_valid := io.id_ex_in(i).bus_valid && io.id_ex_in(i).regfile_we_id_ex
    io.bypass_ex_id(i).data_valid := io.id_ex_in(i).bus_valid &&
      // 写寄存器来源为内存或者cp0时，前递的数据尚未准备完成
      (!io.id_ex_in(i).regfile_wsrc_sel_id_ex && !io.id_ex_in(i).regfile_wdata_from_cp0_id_ex)
    io.bypass_ex_id(i).reg_data := alu_out(i).alu_res
    io.bypass_ex_id(i).reg_addr := MuxCase(0.U, Seq(
      (io.id_ex_in(i).regfile_waddr_sel_id_ex === RegFileWAddrSel.inst_rd) -> io.id_ex_in(i).inst_rd_id_ex,
      (io.id_ex_in(i).regfile_waddr_sel_id_ex === RegFileWAddrSel.inst_rt) -> io.id_ex_in(i).inst_rt_id_ex,
      (io.id_ex_in(i).regfile_waddr_sel_id_ex === RegFileWAddrSel.const_31) -> 31.U))
  }

  // 给hilo的输出

  io.ex_pf_out.to_exception_service_en_ex_pf := (exception_occur(exception_index) ||
    (interrupt_occur && interrupt_available)) &&
    io.id_ex_in(exception_index).bus_valid && ready_go
  io.ex_pf_out.to_epc_en_ex_pf := eret_occur && io.id_ex_in(0).bus_valid && ready_go
  io.ready_go := ready_go


  val hilo_from_alu: Bool = io.id_ex_in(order_flipped).alu_op_id_ex === AluOp.op_mult ||
    io.id_ex_in(order_flipped).alu_op_id_ex === AluOp.op_multu ||
    io.id_ex_in(order_flipped).alu_op_id_ex === AluOp.op_div ||
    io.id_ex_in(order_flipped).alu_op_id_ex === AluOp.op_divu
  io.pipeline_flush := flush
  io.ex_hilo.hi_out := Mux(hilo_from_alu, alu(0).out.alu_res(63, 32), alu(0).in.src1)
  io.ex_hilo.hi_wen := io.id_ex_in(order_flipped).hi_wen && alu(0).out.out_valid &&
    io.id_ex_in(order_flipped).bus_valid && !exceptionShielded(order_flipped.asUInt())
  io.ex_hilo.lo_out := Mux(hilo_from_alu, alu(0).out.alu_res(31, 0), alu(0).in.src1)
  io.ex_hilo.lo_wen := io.id_ex_in(order_flipped).lo_wen && alu(0).out.out_valid &&
    io.id_ex_in(order_flipped).bus_valid && !exceptionShielded(order_flipped.asUInt())

  this_allowin := io.next_allowin && !reset.asBool() && ready_go
  io.this_allowin := this_allowin

}

object InsExecute extends App {
  (new ChiselStage).emitVerilog(new InsExecute)
}
