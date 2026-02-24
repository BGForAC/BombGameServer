package com.example.thread

import com.example.exception.BusinessException
import com.example.tick.TickManager

object EventThread extends Runnable {
  private val framePerSec = 10
  private val frameDelay = 1000 / framePerSec

  override def run(): Unit = {
    while (true) {
      val startTime = System.currentTimeMillis()

      try {
        TickManager.tick()
      } catch {
        case t: Throwable =>
          t.getCause match {
            case e: BusinessException =>
              println(s"business exception, ignore (${e.getMessage})")
            case e: Exception =>
              println(s"Tick error: ${e.getMessage}")
            case _ =>
              println(s"Unexpected error: ${t.getMessage}")
          }
        case e: Exception =>
          println(s"Tick error: ${e.getMessage}")
      }

      val elapsedTime = System.currentTimeMillis() - startTime
      val sleepTime = frameDelay - elapsedTime
      if (sleepTime > 0) {
        Thread.sleep(sleepTime)
      }
    }
  }
}
