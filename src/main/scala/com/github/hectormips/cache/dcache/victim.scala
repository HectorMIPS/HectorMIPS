package com.github.hectormips.cache.dcache
import com.github.hectormips.amba.{AXIAddr, AXIWriteData, AXIWriteResponse}
import chisel3._
import chisel3.util._
import com.github.hectormips.cache.setting.CacheConfig
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import com.github.hectormips.cache.lru.LruMem


class VictimBuffer(val config:CacheConfig,val depth:Int) extends Bundle {
  val addr = RegInit(VecInit.tabulate(depth) { _ => 0.U(28.W) })
  val data = RegInit(VecInit.tabulate(depth) { _ => VecInit.tabulate(config.bankNum) { _ => 0.U(32.W) } })
  val valid = RegInit(0.U(depth.W))
}
/**
 * 写回部件
 */
class Victim(val config:CacheConfig) extends Module {
  var io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val idata = Input(Vec(config.bankNum, UInt(32.W)))

    val op   = Input(Bool()) //op=0 读，op=1 驱逐写
    val dirty = Input(Bool())
    val full   = Output(Bool())
    val find = Output(Bool()) // 匹配
    val odata = Output(Vec(config.bankNum, UInt(32.W)))

    val axi = new Bundle{
      val writeAddr  =  Decoupled(new AXIAddr(32,4))
      val writeData  = Decoupled(new AXIWriteData(32,4))
      val writeResp =  Flipped(Decoupled( new AXIWriteResponse(4)))
    }
  })
  val sIDLE::sWaitHandShake::sWriteBack::Nil =Enum(3)
  val buffer = new VictimBuffer(config,config.victimDepth)
  val state =  Wire(UInt(2.W))

  val hit_victim_onehot = Wire(Vec(config.victimDepth,Bool()))
  val hit_victim = Wire(UInt(log2Ceil(config.victimDepth).W))
  val is_hit_victim = Wire(Bool())
  io.full := state =/= sIDLE // 队列已满
  /**
   * LRU 配置
   */
  val lruMem = Module(new LruMem(config.victimDepth,0))
  lruMem.io.setAddr := config.getVictimIndex(io.addr)
  lruMem.io.visit := hit_victim
  lruMem.io.visitValid := is_hit_victim


  is_hit_victim := hit_victim_onehot.asUInt().orR() === 0.U

  hit_victim := OHToUInt(hit_victim_onehot)
  for(item <- 0 until config.victimDepth){
    hit_victim_onehot(item) := config.getVictimTag(buffer.addr(config.victimDepth)) === config.getTag(io.addr)&& config.getVictimIndex(buffer.addr(config.victimDepth)) === config.getIndex(io.addr)
  }
  io.find := is_hit_victim
  when(is_hit_victim){
    io.odata.foreach(bank=>{
      io.odata(bank) := buffer.data(hit_victim)(bank)
    })
//    buffer.valid(hit_victim) := false.B // 应该要被取走，不取走的话就没了
//    buffer.data(hit_victim)()
  }.otherwise{
    io.odata.foreach(value=>{
      value := 0.U
    })
  }

//  val operateIndex = Wire(UInt(config.victimDepthWidth.W))

  when(!is_hit_victim && io.op === true.B){
    for(bank <- 0 until config.bankNum){
      buffer.data(lruMem.io.waySel)(bank) := io.idata(bank)
    }
    buffer.addr(lruMem.io.waySel) := io.addr(32-config.offsetWidth,0)
    buffer.valid(lruMem.io.waySel) := true.B
  }.otherwise{
    //之前存过
    for(bank <- 0 until config.bankNum){
      buffer.data(hit_victim)(bank) := io.idata(bank)
    }
  }


  /**
   * AXI
   */
  io.axi.writeAddr.bits.id := 1.U
  io.axi.writeAddr.bits.size := 2.U
  io.axi.writeAddr.bits.len := (config.bankNum -1).U
  io.axi.writeAddr.bits.cache := 0.U
  io.axi.writeAddr.bits.lock := 0.U
  io.axi.writeAddr.bits.prot := 0.U
  io.axi.writeAddr.bits.burst := 2.U
  io.axi.writeAddr.valid := (!io.axi.writeAddr.ready && state === sWaitHandShake)

  io.axi.writeData.bits.wid := 1.U
  io.axi.writeData.bits.strb := "b1111".U
  io.axi.writeData.bits.last := false.B

  io.axi.writeResp.ready := state === sWriteBack && clock.asBool()

  val WtCounter = Counter(config.bankNum)

  switch(state) {
    is(sIDLE) {
      when(io.op === 1.U) {
        when(io.dirty) {
          state := sWaitHandShake
        }
      }
    }
    is(sWaitHandShake) {
      when(io.axi.writeAddr.ready){
        state := sWriteBack
        WtCounter.reset()
      }
    }
    is(sWriteBack){
      state := sWriteBack
      when(io.axi.writeData.fire()){
        io.axi.writeData.valid := false.B

      }.otherwise{
        io.axi.writeData.valid := true.B
        io.axi.writeData.bits.data := io.idata(WtCounter.value)
        when(WtCounter.value===(config.bankNum-1).U){
          io.axi.writeData.bits.last := true.B
        }
      }
      when(io.axi.writeResp.fire()){
        // 回复成功
        WtCounter.inc()
        when(WtCounter.value===(config.bankNum-1).U){
          //下一拍归0
          state := sIDLE
        }
      }
    }
  }

}



object Victim extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new Victim(new CacheConfig()))))
}


