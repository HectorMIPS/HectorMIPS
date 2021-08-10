package com.github.hectormips.tlb

import chisel3._
import org.scalatest._
import chiseltest._

import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation
import com.github.hectormips.cache.setting.CacheConfig
class TLBTest  extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "TLBTest"
  it should "basic_test" in {
    test(new tlb(16)).withAnnotations(Seq(WriteVcdAnnotation)) { tlb =>
      myinit(tlb)

      //s0:{'vpn2': '000', 'odd_page': '0', 'asid': '0', 'found': '1', 'index': '0', 'pfn': '111'}
      //s1:{'vpn2': '000', 'odd_page': '1', 'asid': '0', 'found': '1', 'index': '0', 'pfn': '022'}
      tlb.io.s0.vpn2.poke("h000".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("h0".U)
      tlb.io.s1.vpn2.poke("h000".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("h0".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(0.U)
      tlb.io.s0.pfn.expect("h111".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(0.U)
      tlb.io.s1.pfn.expect("h022".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '000', 'odd_page': '1', 'asid': '0', 'found': '1', 'index': '0', 'pfn': '022'}
      //s1:{'vpn2': '000', 'odd_page': '1', 'asid': '1', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      tlb.io.s0.vpn2.poke("h000".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("h0".U)
      tlb.io.s1.vpn2.poke("h000".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("h1".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(0.U)
      tlb.io.s0.pfn.expect("h022".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(false.B)


      //s0:{'vpn2': '000', 'odd_page': '1', 'asid': '1', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      //s1:{'vpn2': '111', 'odd_page': '1', 'asid': '0', 'found': '1', 'index': '1', 'pfn': '033'}
      tlb.io.s0.vpn2.poke("h000".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("h1".U)
      tlb.io.s1.vpn2.poke("h111".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("h0".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(false.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(true.B)
      tlb.io.s1.pfn.expect("h033".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '111', 'odd_page': '1', 'asid': '0', 'found': '1', 'index': '1', 'pfn': '033'}
      //s1:{'vpn2': '111', 'odd_page': '0', 'asid': '1', 'found': '1', 'index': '1', 'pfn': '222'}
      tlb.io.s0.vpn2.poke("h111".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("h0".U)
      tlb.io.s1.vpn2.poke("h111".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("h1".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(true.B)
      tlb.io.s0.pfn.expect("h033".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(true.B)
      tlb.io.s1.pfn.expect("h222".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '111', 'odd_page': '0', 'asid': '1', 'found': '1', 'index': '1', 'pfn': '222'}
      //s1:{'vpn2': '222', 'odd_page': '0', 'asid': '0', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      tlb.io.s0.vpn2.poke("h111".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("h1".U)
      tlb.io.s1.vpn2.poke("h222".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("h0".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(true.B)
      tlb.io.s0.pfn.expect("h222".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(false.B)


      //s0:{'vpn2': '222', 'odd_page': '0', 'asid': '0', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      //s1:{'vpn2': '222', 'odd_page': '0', 'asid': '2', 'found': '1', 'index': '2', 'pfn': '333'}
      tlb.io.s0.vpn2.poke("h222".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("h0".U)
      tlb.io.s1.vpn2.poke("h222".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("h2".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(false.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(2.U)
      tlb.io.s1.pfn.expect("h333".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '222', 'odd_page': '0', 'asid': '2', 'found': '1', 'index': '2', 'pfn': '333'}
      //s1:{'vpn2': '333', 'odd_page': '0', 'asid': '3', 'found': '1', 'index': '3', 'pfn': '444'}
      tlb.io.s0.vpn2.poke("h222".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("h2".U)
      tlb.io.s1.vpn2.poke("h333".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("h3".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(2.U)
      tlb.io.s0.pfn.expect("h333".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(3.U)
      tlb.io.s1.pfn.expect("h444".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '333', 'odd_page': '0', 'asid': '3', 'found': '1', 'index': '3', 'pfn': '444'}
      //s1:{'vpn2': '333', 'odd_page': '1', 'asid': '4', 'found': '1', 'index': '3', 'pfn': '055'}
      tlb.io.s0.vpn2.poke("h333".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("h3".U)
      tlb.io.s1.vpn2.poke("h333".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("h4".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(3.U)
      tlb.io.s0.pfn.expect("h444".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(3.U)
      tlb.io.s1.pfn.expect("h055".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '333', 'odd_page': '1', 'asid': '4', 'found': '1', 'index': '3', 'pfn': '055'}
      //s1:{'vpn2': '444', 'odd_page': '0', 'asid': '4', 'found': '1', 'index': '4', 'pfn': '555'}
      tlb.io.s0.vpn2.poke("h333".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("h4".U)
      tlb.io.s1.vpn2.poke("h444".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("h4".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(3.U)
      tlb.io.s0.pfn.expect("h055".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(4.U)
      tlb.io.s1.pfn.expect("h555".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '444', 'odd_page': '0', 'asid': '4', 'found': '1', 'index': '4', 'pfn': '555'}
      //s1:{'vpn2': '444', 'odd_page': '0', 'asid': '5', 'found': '1', 'index': '5', 'pfn': '666'}
      tlb.io.s0.vpn2.poke("h444".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("h4".U)
      tlb.io.s1.vpn2.poke("h444".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("h5".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(4.U)
      tlb.io.s0.pfn.expect("h555".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(5.U)
      tlb.io.s1.pfn.expect("h666".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '444', 'odd_page': '0', 'asid': '5', 'found': '1', 'index': '5', 'pfn': '666'}
      //s1:{'vpn2': '555', 'odd_page': '1', 'asid': '5', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      tlb.io.s0.vpn2.poke("h444".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("h5".U)
      tlb.io.s1.vpn2.poke("h555".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("h5".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(5.U)
      tlb.io.s0.pfn.expect("h666".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(false.B)


      //s0:{'vpn2': '555', 'odd_page': '1', 'asid': '5', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      //s1:{'vpn2': '666', 'odd_page': '0', 'asid': '6', 'found': '1', 'index': '6', 'pfn': '777'}
      tlb.io.s0.vpn2.poke("h555".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("h5".U)
      tlb.io.s1.vpn2.poke("h666".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("h6".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(false.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(6.U)
      tlb.io.s1.pfn.expect("h777".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '666', 'odd_page': '0', 'asid': '6', 'found': '1', 'index': '6', 'pfn': '777'}
      //s1:{'vpn2': '666', 'odd_page': '1', 'asid': '7', 'found': '1', 'index': '7', 'pfn': '099'}
      tlb.io.s0.vpn2.poke("h666".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("h6".U)
      tlb.io.s1.vpn2.poke("h666".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("h7".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(6.U)
      tlb.io.s0.pfn.expect("h777".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(7.U)
      tlb.io.s1.pfn.expect("h099".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '666', 'odd_page': '1', 'asid': '7', 'found': '1', 'index': '7', 'pfn': '099'}
      //s1:{'vpn2': '666', 'odd_page': '1', 'asid': '8', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      tlb.io.s0.vpn2.poke("h666".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("h7".U)
      tlb.io.s1.vpn2.poke("h666".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("h8".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(7.U)
      tlb.io.s0.pfn.expect("h099".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(false.B)


      //s0:{'vpn2': '666', 'odd_page': '1', 'asid': '8', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      //s1:{'vpn2': '777', 'odd_page': '0', 'asid': '7', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      tlb.io.s0.vpn2.poke("h666".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("h8".U)
      tlb.io.s1.vpn2.poke("h777".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("h7".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(false.B)
      tlb.io.s1.found.expect(false.B)


      //s0:{'vpn2': '777', 'odd_page': '0', 'asid': '7', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      //s1:{'vpn2': '888', 'odd_page': '0', 'asid': '8', 'found': '1', 'index': '8', 'pfn': '999'}
      tlb.io.s0.vpn2.poke("h777".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("h7".U)
      tlb.io.s1.vpn2.poke("h888".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("h8".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(false.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(8.U)
      tlb.io.s1.pfn.expect("h999".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '888', 'odd_page': '0', 'asid': '8', 'found': '1', 'index': '8', 'pfn': '999'}
      //s1:{'vpn2': '999', 'odd_page': '1', 'asid': '9', 'found': '1', 'index': '9', 'pfn': '0bb'}
      tlb.io.s0.vpn2.poke("h888".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("h8".U)
      tlb.io.s1.vpn2.poke("h999".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("h9".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(8.U)
      tlb.io.s0.pfn.expect("h999".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(9.U)
      tlb.io.s1.pfn.expect("h0bb".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': '999', 'odd_page': '1', 'asid': '9', 'found': '1', 'index': '9', 'pfn': '0bb'}
      //s1:{'vpn2': 'aaa', 'odd_page': '0', 'asid': 'a', 'found': '1', 'index': '10', 'pfn': 'bbb'}
      tlb.io.s0.vpn2.poke("h999".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("h9".U)
      tlb.io.s1.vpn2.poke("haaa".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("ha".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(9.U)
      tlb.io.s0.pfn.expect("h0bb".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(10.U)
      tlb.io.s1.pfn.expect("hbbb".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': 'aaa', 'odd_page': '0', 'asid': 'a', 'found': '1', 'index': '10', 'pfn': 'bbb'}
      //s1:{'vpn2': 'bbb', 'odd_page': '1', 'asid': 'b', 'found': '1', 'index': '11', 'pfn': '0dd'}
      tlb.io.s0.vpn2.poke("haaa".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("ha".U)
      tlb.io.s1.vpn2.poke("hbbb".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("hb".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(10.U)
      tlb.io.s0.pfn.expect("hbbb".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(11.U)
      tlb.io.s1.pfn.expect("h0dd".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': 'bbb', 'odd_page': '1', 'asid': 'b', 'found': '1', 'index': '11', 'pfn': '0dd'}
      //s1:{'vpn2': 'ccc', 'odd_page': '0', 'asid': 'c', 'found': '1', 'index': '12', 'pfn': 'ddd'}
      tlb.io.s0.vpn2.poke("hbbb".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("hb".U)
      tlb.io.s1.vpn2.poke("hccc".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("hc".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(11.U)
      tlb.io.s0.pfn.expect("h0dd".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(12.U)
      tlb.io.s1.pfn.expect("hddd".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': 'ccc', 'odd_page': '0', 'asid': 'c', 'found': '1', 'index': '12', 'pfn': 'ddd'}
      //s1:{'vpn2': 'ddd', 'odd_page': '1', 'asid': 'd', 'found': '1', 'index': '13', 'pfn': '0ff'}
      tlb.io.s0.vpn2.poke("hccc".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("hc".U)
      tlb.io.s1.vpn2.poke("hddd".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("hd".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(12.U)
      tlb.io.s0.pfn.expect("hddd".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(13.U)
      tlb.io.s1.pfn.expect("h0ff".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': 'ddd', 'odd_page': '1', 'asid': 'd', 'found': '1', 'index': '13', 'pfn': '0ff'}
      //s1:{'vpn2': 'eee', 'odd_page': '0', 'asid': 'e', 'found': '1', 'index': '14', 'pfn': 'fff'}
      tlb.io.s0.vpn2.poke("hddd".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("hd".U)
      tlb.io.s1.vpn2.poke("heee".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("he".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(13.U)
      tlb.io.s0.pfn.expect("h0ff".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(14.U)
      tlb.io.s1.pfn.expect("hfff".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': 'eee', 'odd_page': '0', 'asid': 'e', 'found': '1', 'index': '14', 'pfn': 'fff'}
      //s1:{'vpn2': 'fff', 'odd_page': '1', 'asid': 'f', 'found': '1', 'index': '15', 'pfn': '011'}
      tlb.io.s0.vpn2.poke("heee".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("he".U)
      tlb.io.s1.vpn2.poke("hfff".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("hf".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(14.U)
      tlb.io.s0.pfn.expect("hfff".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(15.U)
      tlb.io.s1.pfn.expect("h011".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      //s0:{'vpn2': 'fff', 'odd_page': '1', 'asid': 'f', 'found': '1', 'index': '15', 'pfn': '011'}
      //s1:{'vpn2': 'abc', 'odd_page': '0', 'asid': 'f', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      tlb.io.s0.vpn2.poke("hfff".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("hf".U)
      tlb.io.s1.vpn2.poke("habc".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("hf".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(true.B)
      tlb.io.s0.index.expect(15.U)
      tlb.io.s0.pfn.expect("h011".U)
      tlb.io.s0.c.expect(3.U)
      tlb.io.s0.d.expect(true.B)
      tlb.io.s0.v.expect(true.B)
      tlb.io.s1.found.expect(false.B)


      //s0:{'vpn2': 'abc', 'odd_page': '0', 'asid': 'f', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      //s1:{'vpn2': '123', 'odd_page': '1', 'asid': '3', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      tlb.io.s0.vpn2.poke("habc".U)
      tlb.io.s0.odd_page.poke(false.B)
      tlb.io.s0.asid.poke("hf".U)
      tlb.io.s1.vpn2.poke("h123".U)
      tlb.io.s1.odd_page.poke(true.B)
      tlb.io.s1.asid.poke("h3".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(false.B)
      tlb.io.s1.found.expect(false.B)


      //s0:{'vpn2': '123', 'odd_page': '1', 'asid': '3', 'found': '0', 'index': 'x', 'pfn': 'xxx'}
      //s1:{'vpn2': '000', 'odd_page': '0', 'asid': '0', 'found': '1', 'index': '0', 'pfn': '111'}
      tlb.io.s0.vpn2.poke("h123".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("h3".U)
      tlb.io.s1.vpn2.poke("h000".U)
      tlb.io.s1.odd_page.poke(false.B)
      tlb.io.s1.asid.poke("h0".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(false.B)
      tlb.io.s1.found.expect(true.B)
      tlb.io.s1.index.expect(0.U)
      tlb.io.s1.pfn.expect("h111".U)
      tlb.io.s1.c.expect(3.U)
      tlb.io.s1.d.expect(true.B)
      tlb.io.s1.v.expect(true.B)


      // 测试valid
      //s0:{'vpn2': 'fff', 'odd_page': '1', 'asid': 'f', 'found': '1', 'index': '15', 'pfn': '011'}
      tlb.io.we.poke(true.B)
      tlb.io.w_index.poke(15.U)
      tlb.io.w_vpn2.poke("hfff".U(19.W))
      tlb.io.w_asid.poke("hf".U(19.W))
      tlb.io.w_g.poke(false.B)
      tlb.io.w_pfn0.poke("h000".U(19.W))
      tlb.io.w_pfn1.poke("h011".U(19.W))
      tlb.io.w_c0.poke(3.U(3.W))
      tlb.io.w_d0.poke(true.B)
      tlb.io.w_v0.poke(true.B)
      tlb.io.w_c1.poke(3.U(3.W))
      tlb.io.w_d1.poke(true.B)
      tlb.io.w_v1.poke(false.B) // 改成false
      tlb.io.r_index.poke(15.U)
      tlb.clock.step(1)
      tlb.io.s0.vpn2.poke("hfff".U)
      tlb.io.s0.odd_page.poke(true.B)
      tlb.io.s0.asid.poke("hf".U)
      tlb.clock.step(1)
      tlb.io.s0.found.expect(false.B)


      def myinit(dut: tlb): Unit = {

        // 写第0项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(0.U)
        tlb.io.w_vpn2.poke("h000".U(19.W))
        tlb.io.w_asid.poke("h0".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("h111".U(19.W))
        tlb.io.w_pfn1.poke("h022".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(0.U)
        tlb.clock.step(1)
        //验证第0项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("h000".U(19.W))
        tlb.io.r_asid.expect("h0".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("h111".U(19.W))
        tlb.io.r_pfn1.expect("h022".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第1项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(1.U)
        tlb.io.w_vpn2.poke("h111".U(19.W))
        tlb.io.w_asid.poke("h1".U(19.W))
        tlb.io.w_g.poke(true.B)
        tlb.io.w_pfn0.poke("h222".U(19.W))
        tlb.io.w_pfn1.poke("h033".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(1.U)
        tlb.clock.step(1)
        //验证第1项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("h111".U(19.W))
        tlb.io.r_asid.expect("h1".U(19.W))
        tlb.io.r_g.expect(true.B)
        tlb.io.r_pfn0.expect("h222".U(19.W))
        tlb.io.r_pfn1.expect("h033".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第2项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(2.U)
        tlb.io.w_vpn2.poke("h222".U(19.W))
        tlb.io.w_asid.poke("h2".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("h333".U(19.W))
        tlb.io.w_pfn1.poke("h044".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(2.U)
        tlb.clock.step(1)
        //验证第2项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("h222".U(19.W))
        tlb.io.r_asid.expect("h2".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("h333".U(19.W))
        tlb.io.r_pfn1.expect("h044".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第3项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(3.U)
        tlb.io.w_vpn2.poke("h333".U(19.W))
        tlb.io.w_asid.poke("h3".U(19.W))
        tlb.io.w_g.poke(true.B)
        tlb.io.w_pfn0.poke("h444".U(19.W))
        tlb.io.w_pfn1.poke("h055".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(3.U)
        tlb.clock.step(1)
        //验证第3项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("h333".U(19.W))
        tlb.io.r_asid.expect("h3".U(19.W))
        tlb.io.r_g.expect(true.B)
        tlb.io.r_pfn0.expect("h444".U(19.W))
        tlb.io.r_pfn1.expect("h055".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第4项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(4.U)
        tlb.io.w_vpn2.poke("h444".U(19.W))
        tlb.io.w_asid.poke("h4".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("h555".U(19.W))
        tlb.io.w_pfn1.poke("h066".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(4.U)
        tlb.clock.step(1)
        //验证第4项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("h444".U(19.W))
        tlb.io.r_asid.expect("h4".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("h555".U(19.W))
        tlb.io.r_pfn1.expect("h066".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第5项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(5.U)
        tlb.io.w_vpn2.poke("h444".U(19.W))
        tlb.io.w_asid.poke("h5".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("h666".U(19.W))
        tlb.io.w_pfn1.poke("h077".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(5.U)
        tlb.clock.step(1)
        //验证第5项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("h444".U(19.W))
        tlb.io.r_asid.expect("h5".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("h666".U(19.W))
        tlb.io.r_pfn1.expect("h077".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第6项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(6.U)
        tlb.io.w_vpn2.poke("h666".U(19.W))
        tlb.io.w_asid.poke("h6".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("h777".U(19.W))
        tlb.io.w_pfn1.poke("h088".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(6.U)
        tlb.clock.step(1)
        //验证第6项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("h666".U(19.W))
        tlb.io.r_asid.expect("h6".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("h777".U(19.W))
        tlb.io.r_pfn1.expect("h088".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第7项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(7.U)
        tlb.io.w_vpn2.poke("h666".U(19.W))
        tlb.io.w_asid.poke("h7".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("h888".U(19.W))
        tlb.io.w_pfn1.poke("h099".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(7.U)
        tlb.clock.step(1)
        //验证第7项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("h666".U(19.W))
        tlb.io.r_asid.expect("h7".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("h888".U(19.W))
        tlb.io.r_pfn1.expect("h099".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第8项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(8.U)
        tlb.io.w_vpn2.poke("h888".U(19.W))
        tlb.io.w_asid.poke("h8".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("h999".U(19.W))
        tlb.io.w_pfn1.poke("h0aa".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(8.U)
        tlb.clock.step(1)
        //验证第8项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("h888".U(19.W))
        tlb.io.r_asid.expect("h8".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("h999".U(19.W))
        tlb.io.r_pfn1.expect("h0aa".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第9项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(9.U)
        tlb.io.w_vpn2.poke("h999".U(19.W))
        tlb.io.w_asid.poke("h9".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("haaa".U(19.W))
        tlb.io.w_pfn1.poke("h0bb".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(9.U)
        tlb.clock.step(1)
        //验证第9项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("h999".U(19.W))
        tlb.io.r_asid.expect("h9".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("haaa".U(19.W))
        tlb.io.r_pfn1.expect("h0bb".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第10项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(10.U)
        tlb.io.w_vpn2.poke("haaa".U(19.W))
        tlb.io.w_asid.poke("ha".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("hbbb".U(19.W))
        tlb.io.w_pfn1.poke("h0cc".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(10.U)
        tlb.clock.step(1)
        //验证第10项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("haaa".U(19.W))
        tlb.io.r_asid.expect("ha".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("hbbb".U(19.W))
        tlb.io.r_pfn1.expect("h0cc".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第11项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(11.U)
        tlb.io.w_vpn2.poke("hbbb".U(19.W))
        tlb.io.w_asid.poke("hb".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("hccc".U(19.W))
        tlb.io.w_pfn1.poke("h0dd".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(11.U)
        tlb.clock.step(1)
        //验证第11项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("hbbb".U(19.W))
        tlb.io.r_asid.expect("hb".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("hccc".U(19.W))
        tlb.io.r_pfn1.expect("h0dd".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第12项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(12.U)
        tlb.io.w_vpn2.poke("hccc".U(19.W))
        tlb.io.w_asid.poke("hc".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("hddd".U(19.W))
        tlb.io.w_pfn1.poke("h0ee".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(12.U)
        tlb.clock.step(1)
        //验证第12项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("hccc".U(19.W))
        tlb.io.r_asid.expect("hc".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("hddd".U(19.W))
        tlb.io.r_pfn1.expect("h0ee".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第13项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(13.U)
        tlb.io.w_vpn2.poke("hddd".U(19.W))
        tlb.io.w_asid.poke("hd".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("heee".U(19.W))
        tlb.io.w_pfn1.poke("h0ff".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(13.U)
        tlb.clock.step(1)
        //验证第13项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("hddd".U(19.W))
        tlb.io.r_asid.expect("hd".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("heee".U(19.W))
        tlb.io.r_pfn1.expect("h0ff".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第14项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(14.U)
        tlb.io.w_vpn2.poke("heee".U(19.W))
        tlb.io.w_asid.poke("he".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("hfff".U(19.W))
        tlb.io.w_pfn1.poke("h000".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(14.U)
        tlb.clock.step(1)
        //验证第14项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("heee".U(19.W))
        tlb.io.r_asid.expect("he".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("hfff".U(19.W))
        tlb.io.r_pfn1.expect("h000".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)


        // 写第15项
        tlb.io.we.poke(true.B)
        tlb.io.w_index.poke(15.U)
        tlb.io.w_vpn2.poke("hfff".U(19.W))
        tlb.io.w_asid.poke("hf".U(19.W))
        tlb.io.w_g.poke(false.B)
        tlb.io.w_pfn0.poke("h000".U(19.W))
        tlb.io.w_pfn1.poke("h011".U(19.W))
        tlb.io.w_c0.poke(3.U(3.W))
        tlb.io.w_d0.poke(true.B)
        tlb.io.w_v0.poke(true.B)
        tlb.io.w_c1.poke(3.U(3.W))
        tlb.io.w_d1.poke(true.B)
        tlb.io.w_v1.poke(true.B)
        tlb.io.r_index.poke(15.U)
        tlb.clock.step(1)
        //验证第15项
        tlb.io.we.poke(false.B)
        tlb.io.r_vpn2.expect("hfff".U(19.W))
        tlb.io.r_asid.expect("hf".U(19.W))
        tlb.io.r_g.expect(false.B)
        tlb.io.r_pfn0.expect("h000".U(19.W))
        tlb.io.r_pfn1.expect("h011".U(19.W))
        tlb.io.r_c0.expect(3.U(3.W))
        tlb.io.r_d0.expect(true.B)
        tlb.io.r_v0.expect(true.B)
        tlb.io.r_c1.expect(3.U(3.W))
        tlb.io.r_d1.expect(true.B)
        tlb.io.r_v1.expect(true.B)
      }
    }
  }
  it should "command_test" in {
    /**
     *  三种指令和例外测试
     */
    test(new tlb(16)).withAnnotations(Seq(WriteVcdAnnotation)) { tlb =>
      tlb.io.we.poke(true.B)
      tlb.io.w_index.poke(0.U)
      tlb.io.w_vpn2.poke("hfff".U(19.W))
      tlb.io.w_asid.poke("hf".U(19.W))
      tlb.io.w_g.poke(false.B)
      tlb.io.w_pfn0.poke("h000".U(19.W))
      tlb.io.w_pfn1.poke("h011".U(19.W))
      tlb.io.w_c0.poke(3.U(3.W))
      tlb.io.w_d0.poke(true.B)
      tlb.io.w_v0.poke(true.B)
      tlb.io.w_c1.poke(3.U(3.W))
      tlb.io.w_d1.poke(true.B)
      tlb.io.w_v1.poke(true.B)
      tlb.io.r_index.poke(15.U)
      tlb.clock.step(1)

    }
  }
}


