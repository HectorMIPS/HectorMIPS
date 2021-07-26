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
  val valid = RegInit(VecInit.tabulate(depth) { _ => false.B })
}

class VictimQueueItem(val bankNum:Int) extends Bundle{
  val addr  = UInt(32.W)
  val data  = Vec(bankNum, UInt(32.W))
  val dirty = Bool()
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
//  val debug_counter = RegInit(0.U(11.W))
//  debug_counter := debug_counter + 1.U
  val sIDLE::sWaitHandShake::sWriteBack::sWaitResp::Nil =Enum(4)
  val buffer = new VictimBuffer(config,config.victimDepth)
  val state =  Reg(UInt(2.W))
  val idata_r = RegInit(VecInit(Seq.fill(config.bankNum)(0.U(32.W))))
  val addr_r  = RegInit(0.U(32.W))
  val hit_victim_onehot = Wire(Vec(config.victimDepth,Bool()))
  val hit_victim = Wire(UInt(log2Ceil(config.victimDepth).W))
  val is_hit_victim = Wire(Bool())
  io.full := state =/= sIDLE // 队列已满
  /**
   * LRU 配置
   */
//  val lruMem = Module(new LruMem(config.victimDepth,0))
//  lruMem.io.setAddr := config.getVictimIndex(io.addr)
//  lruMem.io.visit := hit_victim
//  lruMem.io.visitValid := is_hit_victim
  val randomCounter = Counter(config.victimDepth)
  randomCounter.inc()

  val waySel = Reg(UInt(config.victimDepth.W))
  when(state === sIDLE){
    waySel := randomCounter.value
  }

  is_hit_victim := hit_victim_onehot.asUInt().orR() =/= 0.U

  hit_victim := OHToUInt(hit_victim_onehot)
  for(item <- 0 until config.victimDepth){
    hit_victim_onehot(item) := config.getVictimTag(buffer.addr(item)) === config.getTag(addr_r)&&
      config.getVictimIndex(buffer.addr(item)) === config.getIndex(addr_r) &&
      buffer.valid(item)
//    printf("[%d]id=[%d] %x %x   | %x %x  | %d result=%d\n",debug_counter,item.U,config.getVictimTag(buffer.addr(item)),config.getTag(io.addr),
//      config.getVictimIndex(buffer.addr(item)),config.getIndex(io.addr), buffer.valid(item),
//      config.getVictimTag(buffer.addr(item)) === config.getTag(io.addr)&&
//        config.getVictimIndex(buffer.addr(item)) === config.getIndex(io.addr) &&
//        buffer.valid(item))
  }

  io.find := is_hit_victim && state === sIDLE
  when(io.find){
    for(bank <- 0 until config.bankNum){
      io.odata(bank) := buffer.data(hit_victim)(bank)
    }
    buffer.valid(hit_victim) := false.B //取走了，下一拍置false
  }.otherwise{
    io.odata.foreach(value=>{
      value := 0.U
    })
  }


//  val operateIndex = Wire(UInt(config.victimDepthWidth.W))
  when(io.op === true.B){
    when(!is_hit_victim) {
        for (bank <- 0 until config.bankNum) {
          buffer.data(waySel)(bank) := io.idata(bank)
        }
        buffer.addr(waySel) := io.addr(31, config.offsetWidth)
        buffer.valid(waySel) := true.B
    }.otherwise{
      //之前存过
      for(bank <- 0 until config.bankNum){
        buffer.data(hit_victim)(bank) := io.idata(bank)
      }
    }
  }

  /**
   * 初始化
   */

  state := sIDLE


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
  io.axi.writeAddr.bits.addr := addr_r
  io.axi.writeAddr.valid := false.B

  io.axi.writeData.bits.wid := 1.U
  io.axi.writeData.bits.strb := "b1111".U
  io.axi.writeData.bits.last := false.B
  io.axi.writeData.bits.data := 0.U
  io.axi.writeData.valid := state === sWriteBack

  io.axi.writeResp.ready := state === sWaitResp


  val WtCounter = RegInit(0.U(config.bankNumWidth.W))


  switch(state) {
    is(sIDLE) {
      for(bank <- 0 until config.bankNum){
        idata_r(bank) := io.idata(bank)
      }
      addr_r := io.addr

      when(io.op === 1.U) {
        when(io.dirty) {
          state := sWaitHandShake
          io.axi.writeAddr.valid := true.B
        }
      }
    }
    is(sWaitHandShake) {
      state := sWaitHandShake
      when(io.axi.writeAddr.ready){
        io.axi.writeAddr.valid := false.B
        state := sWriteBack
        WtCounter := 0.U
      }.otherwise{
        io.axi.writeAddr.valid := true.B
      }
    }
    is(sWriteBack){
      state := sWriteBack
      io.axi.writeData.bits.data := idata_r(WtCounter)
      when(io.axi.writeData.fire()){
        WtCounter := WtCounter + 1.U
        when(WtCounter===(config.bankNum-1).U){
          io.axi.writeData.bits.last := true.B
          state := sWaitResp
        }
      }

    }
    is(sWaitResp){
      when(io.axi.writeResp.fire() && io.axi.writeResp.bits.id ===io.axi.writeAddr.bits.id){
        // 回复
        state := sIDLE
      }.otherwise{
        state := sWaitResp
      }
    }
  }


}



object Victim extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new Victim(new CacheConfig()))))
}


