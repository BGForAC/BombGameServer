package com.example.tick

trait ITick {
  def tick(tickIndex: Long)

  TickManager.addExecutor(this)
}
