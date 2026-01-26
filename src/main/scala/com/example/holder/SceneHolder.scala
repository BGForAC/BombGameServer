package com.example.holder

import com.example.scene.Scene

import scala.collection.mutable

object SceneHolder {
  private val scenes: mutable.Map[String, Scene] = mutable.Map()

  def tick(tickIdx: Long): Unit = {
    scenes.values.foreach(scene => scene.tick(tickIdx))
  }

  def addScene(sceneId: String, scene: Scene): Unit = {
    scenes += (sceneId -> scene)
  }


}
