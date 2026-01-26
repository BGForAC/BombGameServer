package com.example.tick

import com.example.holder.SceneHolder

object TickManager {
  private var currentTick: Long = 0

  def tick(): Unit = {
    currentTick += 1
    SceneHolder.tick(currentTick)
  }
}
