package com.example.actor

import com.example.scene.Scene

abstract class Actor(aid: String) {
  val movement: Movement = new Movement(this)
  val attr: BaseAttr = new BaseAttr(this)

  def tick(tickIdx: Long): Unit = {
    movement.tick(tickIdx)
  }

  def id: String = aid

  def setToScene(scene: Scene): Unit = {
    movement.setToScene(scene)
  }

  def setOutScene(scene: Scene): Unit = {
    movement.setOutScene(scene)
  }
}
