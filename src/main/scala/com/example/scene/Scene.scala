package com.example.scene

import com.example.actor.Actor

import scala.collection.mutable

class Scene(sceneId: String) {
  private val actors: mutable.Map[String, Actor] = mutable.Map()

  def tick(tickIdx: Long): Unit = {
    actors.values.foreach(actor => actor.tick(tickIdx))
  }
}
