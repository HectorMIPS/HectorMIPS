package com.github.hectormips.amba

/**
 * AXI4 接口
 * 参考：https://github.com/nhynes/chisel3-axi/blob/master/src/main/scala/Axi.scala
 */


import chisel3._
import chisel3.util._
import chisel3.util.experimental.{forceName}
import chisel3.experimental.{ChiselEnum, noPrefix}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

//import chisel3.internal.firrtl.Width

/**
 * burst type: b00  固定地址 FIFO用
 *             b01  递增内存
 *             b10  递增环形 Cache用
 *             b11  预留
 * burst length: 返回的数据是burst length+1
 * resp 不管
 */



// 读、写请求通道
class AXIAddr(val addrWidth:Int,val idWidth:Int) extends Bundle{
  val addr = UInt(addrWidth.W)
  val size = UInt(3.W)
  val len = UInt(8.W)
  val burst = UInt(2.W)
  val id = UInt(idWidth.W)
  val lock = Bool()
  val cache = UInt(4.W)
  val prot = UInt(3.W)
  override def clone = { new AXIAddr(addrWidth,idWidth).asInstanceOf[this.type] }

}

// 写数据通道
class AXIWriteData(dataWidth: Int) extends Bundle{
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth / 8).W)
  val last = Bool()
  override def clone = { new AXIWriteData(dataWidth).asInstanceOf[this.type] }

}

// 读数据通道
class AXIReadData(val dataWidth:Int,val idWidth:Int) extends Bundle{
  val data = UInt(dataWidth.W)
  val id =  UInt(idWidth.W)
  val last = Bool()
  val resp = UInt(2.W)
  override def clone = { new AXIReadData(dataWidth, idWidth).asInstanceOf[this.type] }
}

// 写响应通道
class AXIWriteResponse(val idWidth:Int) extends Bundle{
  val id = UInt(idWidth.W)
  val resp = UInt(2.W)
  override def clone = { new AXIWriteResponse(idWidth).asInstanceOf[this.type] }
}


// Master端口
class AXIMaster(val addrWidth:Int =32,
                val dataWidth:Int =32,
                val idWidth:Int   =4)
  extends Bundle{
//  val writeAddr = Decoupled(new AXIAddr(addrWidth,idWidth))
//  val writeData = Decoupled(new AXIWriteData(dataWidth))
  val readAddr  =  Decoupled(new AXIAddr(addrWidth,idWidth))
//  val writeResp = Flipped(Decoupled(new AXIWriteResponse(idWidth)))
  val readData  = Flipped(Decoupled(new AXIReadData(dataWidth,idWidth)))
  def initDefault(): Unit ={
    readAddr.bits.addr  := 0.U
    readAddr.bits.size  := 0.U
    readAddr.bits.len   := 0.U
    readAddr.bits.burst := 1.U
    readAddr.bits.id := 0.U
    readAddr.bits.lock  := 0.U
    readAddr.bits.cache := 0.U
    readAddr.bits.prot  := 0.U


//    writeAddr.bits.lock := 0.U
//    writeAddr.bits.cache := 0.U
//    writeAddr.bits.prot := 0.U
  }
  def clearRead():Unit={
    readAddr.valid := false.B
    readData.ready := false.B
  }
//  def clearWrite():Unit={
//    writeAddr.valid := false.B
//    writeAddr.ready := false.B
//  }
  def renameSignal():Unit={
  // read address channel
  forceName(readAddr.bits.addr,"M_AXI_ARADDR")
  forceName(readAddr.bits.prot,"M_AXI_ARPROT")
  forceName(readAddr.bits.size,"M_AXI_ARSIZE")
  forceName(readAddr.bits.len,"M_AXI_ARLEN")
  forceName(readAddr.bits.burst,"M_AXI_ARBURST")
  forceName(readAddr.bits.lock,"M_AXI_ARLOCK")
  forceName(readAddr.bits.cache,"M_AXI_ARCACHE")
  forceName(readAddr.bits.id,"M_AXI_ARID")
  forceName(readAddr.valid,"M_AXI_ARVALID")
  forceName(readAddr.ready,"M_AXI_ARREADY")
  // read data channel
  forceName(readData.bits.id,"M_AXI_RID")
  forceName(readData.bits.data,"M_AXI_RDATA")
  forceName(readData.bits.resp,"M_AXI_RRESP")
  forceName(readData.bits.last,"M_AXI_RLAST")
  forceName(readData.valid,"M_AXI_RVALID")
  forceName(readData.ready,"M_AXI_RREADY")
  }
  def computeSize(size:UInt):UInt={
    // 计算size的长度，*size = 1 << size
    MuxLookup(size, 1.U,
      Array(
        0.U -> 1.U,
        1.U -> 2.U,
        2.U -> 4.U,
        3.U -> 8.U,
        4.U -> 16.U,
        5.U -> 32.U,
        6.U -> 64.U,
        7.U -> 128.U
        )
    )
  }
}

class AXISlave( val addrWidth:Int =32,
                val dataWidth:Int =32,
                val idWidth:Int   =4)
  extends Bundle{
  val writeAddr = Flipped(Decoupled(new AXIAddr(addrWidth,idWidth)))
  val writeData = Flipped(Decoupled(new AXIWriteData(dataWidth)))
  val readAddr  = Flipped(Decoupled(new AXIAddr(addrWidth,idWidth)))
  val writeResp = Decoupled(new AXIWriteResponse(idWidth))
  val readData  = Decoupled(new AXIReadData(dataWidth,idWidth))
}

object AxiReadState extends ChiselEnum {
  val IDLE,READING,END = Value
}

class ICachePort extends Bundle{
  val rdata   = Output(UInt(32.W)) //返回数据
  val data_ok = Output(Bool()) //等到ok以后才能撤去数据
}

/**
 * SRAM-AXI转接桥
 * @param addrWidth
 * @param dataWidth
 * @param idWidth
 */
class ICache2AXI(
                    val portNum:Int = 2,
                   val addrWidth:Int =32,
                   val dataWidth:Int =32,
                   val idWidth:Int   =4)
  extends Module{
  val io = IO(new Bundle{
    val valid   = Input(Bool())
    val addr    = Input(UInt(32.W)) //等到ok以后才能撤去数据
    val addr_ok = Output(Bool()) //等到ok以后才能撤去数据
    val inst    = Vec(portNum,new ICachePort)
    val axi     = new AXIMaster()
  })
  def rename():Unit={

  }
  // 初始值
  def initIO():Unit={
    io.axi.initDefault()
    for(i<-0 until portNum){
      io.inst(i).data_ok := regOK(i)
      io.inst(i).rdata := regRdata(i)
    }
    io.addr_ok := false.B
    io.axi.readAddr.valid := false.B
    io.axi.readData.ready := false.B
    io.axi.readAddr.bits.burst := 1.U
  }



  // 第一个接口
  val rd_ps = RegInit(AxiReadState.IDLE)
  val rd_id = 0.U
  val counter = RegInit(0.U(portNum.W))
  val debug_timer = RegInit(0.U(10.W))
  val regRdata = RegInit(VecInit(Seq.fill(portNum)(0.U(32.W))))
  val regOK    = RegInit(VecInit(Seq.fill(portNum)(false.B)))
  debug_timer := debug_timer + 1.U
  initIO()
//  valid := io.valid
  printf("[%d]rd1_ps=%d,last=%d,data=%x,ready=%d,valid=%d\n",
    debug_timer,rd_ps.asUInt(),io.axi.readData.bits.last.asUInt(),io.axi.readData.bits.data.asUInt(),
    io.axi.readData.ready,io.valid.asUInt())


  switch(rd_ps) {
    is(AxiReadState.IDLE) {
      when(io.valid === true.B) {
        io.addr_ok := true.B
        io.axi.readAddr.valid := true.B
        io.axi.readAddr.bits.addr := io.addr
//        io.axi.readAddr.bits.size := io.axi.computeSize(io.size)
        io.axi.readAddr.bits.id := rd_id
        io.axi.readAddr.bits.len := (portNum-1).U // 突发长度
        io.axi.readAddr.bits.burst := 1.U //固定为01
        rd_ps := AxiReadState.READING
        counter := 0.U
      }.otherwise {
        io.addr_ok := false.B
        io.axi.readData.ready := false.B
        io.axi.clearRead()
      }
    }

    is(AxiReadState.READING) {
      io.addr_ok := false.B
      when(io.axi.readData.valid && io.axi.readData.bits.id === rd_id) {
        counter := counter + 1.U
        io.axi.readData.ready := true.B
        //          rd_data := io.axi.readData.bits.data
        regRdata(counter)        := io.axi.readData.bits.data
        regOK(counter) := true.B
        when(io.axi.readData.bits.last) {
          rd_ps := AxiReadState.END
        }
      }
    }

    is(AxiReadState.END) {
        io.axi.readData.ready := false.B
        when(io.valid === false.B) {
          rd_ps := AxiReadState.IDLE
          for(i <- 0 until portNum){
            regOK(i) := false.B
          }
        }
    }
  }
}

///**
// * cache使用的AXI接口
// * @param addrWidth
// * @param dataWidth
// * @param idWidth
// */
//class AXIBurstMaster(val addrWidth:Int =32,
//                    val dataWidth:Int =32,
//                    val idWidth:Int   =4)
//  extends Module{
//  val io = new Bundle{
//
//  }
//
//
//}


object ICache2AXI extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new ICache2AXI)))
}
