package com.github.hectormips.predict

import chisel3._
import scala.util.Random
import chiseltest._
import org.scalatest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.VerilatorBackendAnnotation

class BTBTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "BTB"

  class BTB_sim(size: Int, BHT_size: Int) {
    var location_table: Array[Int] = new Array[Int](size)
    var target_table: Array[Int] = new Array[Int](size)
    var pattern_table: Array[Int] = new Array[Int](size)
    var valid_table: Array[Boolean] = new Array[Boolean](size)
    var time_table: Array[Int] = new Array[Int](size)
    var BHT: Array[Array[Int]] = Array.ofDim[Int](size, BHT_size)

    private def find(pc: Int): Option[Int] = {
      for (i <- 0 until size) {
        if (location_table(i) == pc & valid_table(i)) {
          return Some(i)
        }
      }
      None
    }

    def step(is_success: Boolean, pc: Int, target: Int) {
      for (i <- 0 until size) {
        time_table(i) = time_table(i) + 1
      }
      val index = find(pc)
      if (index.isEmpty) {
        val next = find_max_time()
        time_table(next) = 0
        location_table(next) = pc
        target_table(next) = target
        pattern_table(next) = if (is_success) 1 else 0
        for (j <- 0 until BHT_size) {
          if (is_success & j == 1) {
            BHT(next)(j) = 2
          } else {
            BHT(next)(j) = 1
          }
        }
        valid_table(next) = true
      } else {
        val next = index.get
        time_table(next) = 0
        target_table(next) = target
        val next_pattern = (pattern_table(next) * 2 + (if (is_success) 1 else 0)) % BHT_size
        pattern_table(next) = next_pattern
        if (is_success) {
          if (BHT(next)(next_pattern) != 3) {
            BHT(next)(next_pattern) += 1
          }
        } else {
          if (BHT(next)(next_pattern) != 0) {
            BHT(next)(next_pattern) -= 1
          }
        }
        valid_table(next) = true
      }
    }

    def predict(pc: Int): Option[Int] = {
      find(pc).map(i => {
        val pattern = pattern_table(i)
        val predict = BHT(i)(pattern)
        if (predict >= 2) {
          return Some(target_table(i))
        } else {
          return None
        }
      })
    }

    private def find_max_time(): Int = {
      var max = -1
      var index = -1
      for (i <- 0 until size) {
        if (time_table(i) > max) {
          max = time_table(i)
          index = i
        }
      }
      index
    }
  }

  val jump_data = Seq((true, 4, 5), (false, 4, 8), (true, 8, 1), (false, 3, 2), (true, 4, 5), (false, 4, 9))

  it should "basic" in {
    test(new BTB(4, 16)) { c =>
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)

      c.io.pc.poke(0.U)
      c.io.en_ex.poke(1.B)

      c.io.ex_success.poke(1.B)
      c.io.ex_pc.poke(4.U)
      c.io.ex_target.poke(5.U)
      c.clock.step(1)

      c.io.ex_success.poke(0.B)
      c.io.ex_pc.poke(4.U)
      c.io.ex_target.poke(5.U)
      c.clock.step(1)

      c.io.ex_success.poke(1.B)
      c.io.ex_pc.poke(4.U)
      c.io.ex_target.poke(5.U)
      c.clock.step(1)

      c.io.pc.poke(4.U)
      c.io.target.expect(5.U)
      c.io.predict.expect(1.B)
    }
  }

  it should "jump" in {
    test(new BTB(4, 16)) { c => {
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)

      c.io.en_ex.poke(1.B)

      for ((success, pc, target) <- jump_data) {
        c.io.ex_success.poke(success.B)
        c.io.ex_pc.poke(pc.U)
        c.io.ex_target.poke(target.U)
        c.clock.step(1)
      }
      c.io.en_ex.poke(0.B)
      c.clock.step(10)

      c.io.pc.poke(4.U)
      c.io.predict.expect(false.B)
      c.io.target.expect(9.U)

      c.clock.step(1)

      c.io.pc.poke(8.U)
      c.io.predict.expect(true.B)
      c.io.target.expect(1.U)

      c.clock.step(1)

      c.io.pc.poke(3.U)
      c.io.predict.expect(false.B)
      c.io.target.expect(2.U)
    }
    }
  }

  it should "auto" in {
    test(new BTB(4, 16)) { c => {
      c.reset.poke(1.B)
      c.clock.step(5)
      c.reset.poke(0.B)

      val sim = new BTB_sim(4, 16)
      val random = new Random

      for (i <- 0 until 100) {
        val is_success = random.nextBoolean()
        val pc = random.nextInt(5)
        val target = random.nextInt(100)
        //        println((is_success, pc, target))
        c.io.en_ex.poke(1.B)
        c.io.ex_pc.poke(pc.U)
        c.io.ex_target.poke(target.U)
        c.io.ex_success.poke(is_success.B)
        sim.step(is_success, pc, target)
        c.clock.step(1)
        c.io.en_ex.poke(0.B)
        for (j <- 0 to 4) {
          val y_true = sim.predict(j)
          //          println(y_true)
          c.io.pc.poke(j.U)
          if (y_true.isDefined) {
            c.io.predict.expect(1.B)
            c.io.target.expect(y_true.get.U)
          } else {
            c.io.predict.expect(0.B)
          }
          c.clock.step(1)
        }
        //        println(sim.location_table.mkString(" "))
        //        println()
      }
    }
    }
  }
}
