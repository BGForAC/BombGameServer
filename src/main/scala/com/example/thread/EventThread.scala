package com.example.thread

import com.example.exception.BusinessException
import com.example.tick.TickManager

/**
 * EventThread 对象，实现了 Runnable 接口，用于创建一个事件处理线程
 * 该线程以固定的帧率执行 TickManager.tick() 方法
 */
object EventThread extends Runnable {
  // 定义每秒帧率
  private val framePerSec = 10
  // 计算每帧的延迟时间（毫秒）
  private val frameDelay = 1000 / framePerSec

  /**
   * 重写 run 方法，这是线程执行的入口点
   * 该方法会以固定的帧率循环执行 TickManager.tick()
   */
  override def run(): Unit = {
    while (true) {
      // 记录当前时间，用于计算帧执行时间
      val startTime = System.currentTimeMillis()

      try {
        // 执行 TickManager 的 tick 方法
        TickManager.tick()
      } catch {
        // 捕获所有 Throwable 类型的异常
        case t: Throwable =>
          // 根据异常类型进行不同处理
          t.getCause match {
            // 业务异常，打印信息但不中断线程
            case e: BusinessException =>
              println(s"business exception, ignore (${e.getMessage})")
            // 其他异常，打印错误信息
            case e: Exception =>
              println(s"Tick error: ${e.getMessage}")
            // 匹配错误，打印堆栈信息
            case e: MatchError =>
              e.printStackTrace()
          }
        // 捕获其他异常（虽然上面的 case 已经覆盖了所有情况）
        case e: Exception =>
          println(s"Tick error: ${e.getMessage}")
      }

      // 计算当前帧执行所花费的时间
      val elapsedTime = System.currentTimeMillis() - startTime
      // 计算需要睡眠的时间，以维持固定的帧率
      val sleepTime = frameDelay - elapsedTime
      // 如果睡眠时间大于0，则让线程休眠相应时间
      if (sleepTime > 0) {
        Thread.sleep(sleepTime)
      }
    }
  }
}
