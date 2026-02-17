package com.example.actor

import com.example.holder.SceneHolder

class Bomb(owner: Actor, id: String) extends Actor(id) {
  private val explodeTime: Long = System.currentTimeMillis() + owner.attr.FuseTime

  override def tick(tickIdx: Long): Unit = {
    if (System.currentTimeMillis() >= explodeTime) {
      explode()
      SceneHolder.exitScene(this.movement.sceneId, this)
    }
  }

  def explode(): Unit = {
    val scene = SceneHolder.getScene(movement.sceneId)
    if (scene == null) throw new IllegalStateException(s"炸弹[$id]所在的场景[${movement.sceneId}]不存在")
    val players = scene.players.values
    players.foreach { player =>
      if (player.movement.inRange(this.movement, owner.attr.BombRadius)) {
        player.hpChange(this, owner.attr.BombDamage)
      }
    }
  }
}

object Bomb {
  private var bombIdCounter: Map[String, Int] = Map.empty.withDefaultValue(0)
  private var globalBombIdCounter: Int = 0

  def apply(owner: Actor): Bomb = {
    if (owner == null) {
      val bomb = new Bomb(null, s"global-${globalBombIdCounter}")
      globalBombIdCounter += 1
      return bomb
    }
    val ownerId = owner.id
    val bombId = s"$ownerId-${bombIdCounter(ownerId)}"
    bombIdCounter += (ownerId -> (bombIdCounter(ownerId) + 1))
    new Bomb(owner, bombId)
  }
}
