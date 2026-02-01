package com.example.thread

import com.example.tick.TickManager

object EventThread extends Runnable {
  val framePerSec = 10
  private val frameDelay = 1000 / framePerSec

  override def run(): Unit = {
    while (true) {
      val startTime = System.currentTimeMillis()

      TickManager.tick()

      val elapsedTime = System.currentTimeMillis() - startTime
      val sleepTime = frameDelay - elapsedTime
      if (sleepTime > 0) {
        Thread.sleep(sleepTime)
      }
    }
  }
}
