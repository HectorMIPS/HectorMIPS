package com.github.hectormips.cache.dcache
import com.github.hectormips.amba.{AXIAddr, AXIWriteData, AXIWriteResponse}
import chisel3._
import chisel3.util._
import com.github.hectormips.cache.setting.CacheConfig
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}


class VictimBuffer(val config:CacheConfig,val depth:Int) extends Bundle {
  val addr = RegInit(VecInit.tabulate(depth) { _ => 0.U(28.W) })
  val data =RegInit(VecInit(Seq.fill(depth)(VecInit(Seq.fill(config.bankNum)(0.U(32.W))))))
  val valid = RegInit(VecInit.tabulate(depth) { _ => false.B })
}

//class VictimQueueItem(val bankNum:Int) extends Bundle{
//  val addr  = UInt(32.W)
//  val data  = Vec(bankNum, UInt(32.W))
//  val dirty = Bool()
//}

/**
 * victim buffer，用于缓冲被驱逐的数据
 *
 * 后面会改成如果二次驱逐才会写回
 */
class Victim(val config:CacheConfig) extends Module {
  var io = IO(new Bundle {
    val qaddr = Input(UInt(32.W))
    val qdata = Output(UInt(32.W))
    val qvalid = Input(Bool())
    val wvalid   = Input(Bool()) //wvalid=1 驱逐写
    val dirty = Input(Bool())

    val find = Output(Bool()) // 匹配
    val waddr = Input(UInt(32.W)) //驱逐的数据的地址
    val wdata = Input( UInt(32.W))
    val query_valid = Output(Bool()) // 查询允许
    val fill_valid = Output(Bool()) // 输入允许
    val axi = new Bundle{
      val writeAddr  =  Decoupled(new AXIAddr(32,4))
      val writeData  = Decoupled(new AXIWriteData(32,4))
      val writeResp =  Flipped(Decoupled( new AXIWriteResponse(4)))
    }
  })

  val fsIDLE::fsWaitInputData::fsWaitHandShake::fsWriteBack::fsWaitResp::Nil =Enum(5) //填充状态机
  val qsIDLE::qsSend::qsFind::Nil = Enum(3) // 查询状态机
  val buffer = new VictimBuffer(config,config.victimDepth)
  val fstate =  RegInit(0.U(3.W)) // fill state 填充状态
  val qstate =  RegInit(0.U(2.W)) //query state 查询状态


  val wdata_r = RegInit(VecInit(Seq.fill(config.bankNum)(0.U(32.W))))
  val iaddr_r  = RegInit(0.U(32.W))
  val waddr_r  = RegInit(0.U(32.W))
  val hit_victim_onehot = Wire(Vec(config.victimDepth,Bool()))
  val hit_victim = Wire(UInt(log2Ceil(config.victimDepth).W))
  val hit_victim_r = RegInit(0.U(log2Ceil(config.victimDepth).W))
  val is_hit_victim = Wire(Bool())
//  io.full := fstate =/= fsIDLE // 队列已满
  /**
   * LRU 配置
   */
  val randomCounter = RegInit(0.U(log2Ceil(config.victimDepth).W))
  randomCounter := randomCounter + 1.U

  val waySel = RegInit(0.U(log2Ceil(config.victimDepth).W))

  is_hit_victim := hit_victim_onehot.asUInt().orR() =/= 0.U

  hit_victim := OHToUInt(hit_victim_onehot)

  for(item <- 0 until config.victimDepth){
    hit_victim_onehot(item) := config.getVictimTag(buffer.addr(item)) === config.getTag(iaddr_r)&&
      config.getVictimIndex(buffer.addr(item)) === config.getIndex(iaddr_r) &&
      buffer.valid(item)
  }

  io.find := is_hit_victim && qstate === qsSend
  val qdata_counter = RegInit((0.U(config.bankNumWidth.W)))
  val wdata_counter = RegInit((0.U(config.bankNumWidth.W)))

  when(qstate===qsSend){
    io.qdata := buffer.data(hit_victim_r)(qdata_counter)
  }.otherwise{
    io.qdata := DontCare
  }

  /**
   * 查询状态
   * 查询成功后，等待n拍
   */
  switch(qstate){
    is(qsIDLE){
      iaddr_r := io.qaddr
      when(is_hit_victim && io.qvalid){
        qdata_counter := 0.U
        hit_victim_r := hit_victim
        qstate := qsSend
      }
    }
    is(qsSend) {
      qdata_counter := qdata_counter + 1.U
      when(qdata_counter === (config.bankNum-1).U){
        qstate := qsIDLE
        buffer.valid(hit_victim_r) := false.B //取走了，下一拍置false
      }.otherwise{
        qstate := qsSend
      }
    }
  }

  when(fstate === fsWaitInputData){
    buffer.data(waySel)(wdata_counter) := io.wdata
  }

  /**
   * 初始化
   */

  fstate := fsIDLE
  io.fill_valid := fstate === fsIDLE
  io.query_valid := qstate === qsIDLE && (fstate =/= fsWaitInputData && (fstate===fsIDLE && io.wvalid===false.B))
  /**
   * AXI
   * 如果异步写，可能会出现问题
   */
  io.axi.writeAddr.bits.id := 1.U
  io.axi.writeAddr.bits.size := 2.U
  io.axi.writeAddr.bits.len := (config.bankNum -1).U
  io.axi.writeAddr.bits.cache := 0.U
  io.axi.writeAddr.bits.lock := 0.U
  io.axi.writeAddr.bits.prot := 0.U
  io.axi.writeAddr.bits.burst := 2.U
  io.axi.writeAddr.bits.addr := waddr_r
  io.axi.writeAddr.valid := false.B

  io.axi.writeData.bits.wid := 1.U
  io.axi.writeData.bits.strb := "b1111".U
  io.axi.writeData.bits.last := false.B
  io.axi.writeData.bits.data := 0.U
  io.axi.writeData.valid := fstate === fsWriteBack

  io.axi.writeResp.ready := fstate === fsWaitResp


  val WtCounter = RegInit(0.U(config.bankNumWidth.W))


  switch(fstate) {
    is(fsIDLE) {
      when(io.wvalid === true.B) {
        waddr_r := io.waddr
        waySel := randomCounter
        fstate := fsWaitInputData
        wdata_counter := 0.U
        buffer.addr(randomCounter) := io.waddr(31, config.offsetWidth)
        buffer.valid(randomCounter) := true.B
      }
    }
    is(fsWaitInputData){
      wdata_counter := wdata_counter + 1.U
      when(wdata_counter === (config.bankNum-1).U){
        when(io.dirty) {
          fstate := fsWaitHandShake
          io.axi.writeAddr.valid := true.B
        }.otherwise{
          fstate := fsIDLE
        }
      }.otherwise{
        fstate := fsWaitInputData
      }
    }
    is(fsWaitHandShake) {
      fstate := fsWaitHandShake
      when(io.axi.writeAddr.ready){
        io.axi.writeAddr.valid := false.B
        fstate := fsWriteBack
        WtCounter := 0.U
      }.otherwise{
        io.axi.writeAddr.valid := true.B
      }
    }
    is(fsWriteBack){
      fstate := fsWriteBack
      io.axi.writeData.bits.data := wdata_r(WtCounter)
      when(io.axi.writeData.fire()){
        WtCounter := WtCounter + 1.U
        when(WtCounter===(config.bankNum-1).U){
          io.axi.writeData.bits.last := true.B
          fstate := fsWaitResp
        }
      }

    }
    is(fsWaitResp){
      when(io.axi.writeResp.fire() && io.axi.writeResp.bits.id ===io.axi.writeAddr.bits.id){
        // 回复
        fstate := fsIDLE
      }.otherwise{
        fstate := fsWaitResp
      }
    }
  }


}



object Victim extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new Victim(new CacheConfig()))))
}


