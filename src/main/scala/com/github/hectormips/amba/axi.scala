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
class AXIWriteData(dataWidth: Int,val idWidth:Int) extends Bundle{
  val wid = UInt(idWidth.W)
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth / 8).W)
  val last = Bool()
  override def clone = { new AXIWriteData(dataWidth,idWidth).asInstanceOf[this.type] }

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
  val writeAddr = Decoupled(new AXIAddr(addrWidth,idWidth))
  val writeData = Decoupled(new AXIWriteData(dataWidth,idWidth))
  val readAddr  =  Decoupled(new AXIAddr(addrWidth,idWidth))
  val writeResp = Flipped(Decoupled(new AXIWriteResponse(idWidth)))
  val readData  = Flipped(Decoupled(new AXIReadData(dataWidth,idWidth)))
  def initDefault(): Unit ={
    readAddr.bits.size  := 2.U
    readAddr.bits.burst := 1.U
    readAddr.bits.lock  := 0.U
    readAddr.bits.cache := 0.U
    readAddr.bits.prot  := 0.U
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
  val writeData = Flipped(Decoupled(new AXIWriteData(dataWidth,idWidth)))
  val readAddr  = Flipped(Decoupled(new AXIAddr(addrWidth,idWidth)))
  val writeResp = Decoupled(new AXIWriteResponse(idWidth))
  val readData  = Decoupled(new AXIReadData(dataWidth,idWidth))
}

object AxiReadState extends ChiselEnum {
  val IDLE,READING,END = Value
}
object AxiWriteState extends ChiselEnum {
  val IDLE,WRITING,CHECK = Value
}
class ICachePort extends Bundle{
  val rdata   = Output(UInt(32.W)) //返回数据
  val data_ok = Output(Bool()) //等到ok以后才能撤去数据
}

class DCache2AXI extends Module{
  val io =IO(new Bundle{
    val mode   = Input(Bool())
    val rvalid = Input(Bool())
    val raddr    = Input(UInt(32.W)) //等到ok以后才能撤去数据
    val raddr_ok = Output(Bool()) //等到ok以后才能撤去数据
    val rdata   = Output(UInt(32.W)) //读数据
    val rdata_ok = Output(Bool()) // 返回数据(写成功；读返回数据)

    val wvalid = Input(Bool())
    val wdata   = Input(UInt(32.W)) //写数据
    val wstrb   = Input(UInt(4.W)) //写字节使能
    val waddr   = Input(UInt(32.W))
    val waddr_ok = Output(Bool())
    val wdata_ok = Output(Bool()) // 返回数据(写成功；读返回数据)

    val axi = new AXIMaster()

  })
//  regDataOK
  val regRdata = RegInit(0.U)
  io.rdata := regRdata
  val regROK   = RegInit(false.B)
  io.rdata_ok := regROK
  val readAddrValidReg = RegInit(false.B)
  val readDataReadyReg = RegInit(false.B)
  val rd_ps = RegInit(AxiReadState.IDLE)
  val rd_id = 1.U
  io.raddr_ok := false.B
  io.axi.readAddr.valid := readAddrValidReg
  io.axi.readData.ready := readDataReadyReg
  io.axi.readAddr.bits.burst := 1.U
  io.axi.readAddr.bits.addr := io.raddr
  io.axi.readAddr.bits.id := rd_id
  io.axi.readAddr.bits.len := 0.U // 突发长度
  io.axi.readAddr.bits.burst := 1.U //固定为01
  when(io.axi.readAddr.fire()){//握手成功自动降下
    readAddrValidReg := false.B
  }
  readDataReadyReg := false.B

  // 读-状态机
  switch(rd_ps) {
    is(AxiReadState.IDLE) {
      when(io.rvalid === true.B) {
        //一拍取了地址就走了
        readAddrValidReg := true.B
        io.raddr_ok := true.B
        rd_ps := AxiReadState.READING
      }.otherwise {
        io.raddr_ok := false.B
      }
    }

    is(AxiReadState.READING) {
      when(io.axi.readData.valid && io.axi.readData.bits.id === rd_id) {
        readDataReadyReg:= true.B
        regRdata := io.axi.readData.bits.data
        regROK := true.B
        when(io.axi.readData.bits.last) {
          rd_ps := AxiReadState.END
        }
      }
    }

    is(AxiReadState.END) {
      rd_ps := AxiReadState.IDLE
      //可以多续一会，这里不续了，下一拍进入IDLE
      regROK := false.B
    }
  }


  // 写-状态机
  val writeAddrValidReg = RegInit(false.B)
  val writeDataValidReg = RegInit(false.B)
  val writeRespReadyReg = RegInit(false.B)
  val writeCounter        = RegInit(0.U(8.W))
  io.axi.writeAddr.valid := writeAddrValidReg
  io.axi.writeData.valid := writeDataValidReg
  val regWOK   = RegInit(false.B)
  io.wdata_ok := regWOK
  val wt_ps = RegInit(AxiWriteState.IDLE)
  val wt_id = 2.U
  io.waddr_ok := false.B
  io.axi.writeAddr.bits.burst := 1.U
  io.axi.writeAddr.bits.len := 0.U
  io.axi.writeAddr.bits.addr := io.waddr
  io.axi.writeAddr.bits.id := wt_id
  io.axi.writeData.bits.data := io.wdata
  io.axi.writeData.bits.strb := "b1111".U(4.W)
  when(io.axi.writeAddr.fire()){//握手成功自动降下
    writeAddrValidReg := false.B
  }
  when(io.axi.writeData.fire()){//握手成功自动降下
    writeDataValidReg := false.B
  }
  when(io.axi.writeResp.valid){
    writeRespReadyReg := true.B
  }

  // 可以支持burst
  switch(wt_ps){
    is(AxiWriteState.IDLE){
      when(io.wvalid){
        readAddrValidReg := true.B
        readDataReadyReg := true.B
        wt_ps := AxiWriteState.WRITING
        writeCounter := 1.U
      }.otherwise{
        writeRespReadyReg := false.B
        readAddrValidReg  := false.B
        writeRespReadyReg := false.B
      }
    }
    is(AxiWriteState.WRITING){
      when(io.axi.writeResp.valid && io.axi.writeResp.bits.id===wt_id){
        wt_ps := AxiWriteState.CHECK
        writeCounter := writeCounter - 1.U
      }
    }
    is(AxiWriteState.CHECK){
      when(writeCounter===0.U){
        wt_ps := AxiWriteState.IDLE
      }
    }
  }






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
    val axi     = new Bundle{
      val readAddr  =  Decoupled(new AXIAddr(32,4))
      val readData  = Flipped(Decoupled(new AXIReadData(32,4)))
    }
    val debug_timer = Output(UInt(10.W))
  })
  // 初始值

  def initIO():Unit={
    io.axi.readAddr.bits.size  := 2.U
    io.axi.readAddr.bits.burst := 1.U
    io.axi.readAddr.bits.lock  := 0.U
    io.axi.readAddr.bits.cache := 0.U
    io.axi.readAddr.bits.prot  := 0.U
    for(i<-0 until portNum){
      io.inst(i).data_ok := regOK(i)
      io.inst(i).rdata := regRdata(i)
    }
    io.addr_ok := false.B
    io.axi.readAddr.valid := readAddrValidReg
    io.axi.readData.ready := readDataReadyReg
    io.axi.readAddr.bits.burst := 1.U
  }



  // 第一个接口
//  val stay_for_one_cycle =RegInit(false.B)
  val readAddrValidReg = RegInit(false.B)
  val readDataReadyReg = RegInit(false.B)
  val rd_ps = RegInit(AxiReadState.IDLE)
  val rd_id = 0.U
  val counter = RegInit(0.U(portNum.W))
  val debug_timer = RegInit(0.U(10.W))
  val regRdata = RegInit(VecInit(Seq.fill(portNum)(0.U(32.W))))
  val regOK    = RegInit(VecInit(Seq.fill(portNum)(false.B)))
  io.axi.readAddr.bits.addr := io.addr
  io.axi.readAddr.bits.id := rd_id
  io.axi.readAddr.bits.len := (portNum - 1).U // 突发长度
  io.axi.readAddr.bits.burst := 1.U //固定为01

  debug_timer := debug_timer + 1.U
  initIO()
//  valid := io.valid
  printf("[%d]rd1_ps=%d,last=%d,data=%x,ready=%d,valid=%d\n",
    debug_timer,rd_ps.asUInt(),io.axi.readData.bits.last.asUInt(),io.axi.readData.bits.data.asUInt(),
    io.axi.readData.ready,io.valid.asUInt())
  io.debug_timer := debug_timer
  when(io.axi.readAddr.fire()){//握手成功自动降下
    readAddrValidReg := false.B
  }
  readDataReadyReg := false.B

  switch(rd_ps) {
    is(AxiReadState.IDLE) {
      when(io.valid === true.B) {
        //一拍取了地址就走了
        readAddrValidReg := true.B
        io.addr_ok := true.B
        rd_ps := AxiReadState.READING
        counter := 0.U
      }.otherwise {
        io.addr_ok := false.B
      }
    }

    is(AxiReadState.READING) {
      //      io.addr_ok := false.B

      when(io.axi.readData.valid && io.axi.readData.bits.id === rd_id) {
        counter := counter + 1.U
        readDataReadyReg:= true.B
        //          rd_data := io.axi.readData.bits.data
        regRdata(counter) := io.axi.readData.bits.data
        regOK(counter) := true.B
        when(io.axi.readData.bits.last) {
          rd_ps := AxiReadState.END
        }
      }
    }

    is(AxiReadState.END) {
      //        io.axi.readData.ready := false.B
      rd_ps := AxiReadState.IDLE
      for (i <- 0 until portNum) {
        regOK(i) := false.B
      }
    }
  }
}



object ICache2AXI extends App {
  new ChiselStage execute(args, Seq(ChiselGeneratorAnnotation(
    () =>
      new ICache2AXI)))
}
