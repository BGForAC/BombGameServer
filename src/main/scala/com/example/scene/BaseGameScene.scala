package com.example.scene

import com.example.actor.{Actor, Player}
import com.example.config.SceneDef

import scala.collection.mutable

class BaseGameScene(id: String, _def: SceneDef) extends Scene(id, _def) {
  private var idx: Int = 0
  private val player2Idx: mutable.Map[String, Int] = mutable.Map.empty

  override def tick(tickIdx: Long): Unit = {
    super.tick(tickIdx)
  }

  override def onEnter(actor: Actor): Unit = {
    actor match {
      case player: Player =>
        player2Idx += player.id -> idx
        super.onEnter(player)
        player.movement.setPosition(getSpawnPoint(player.id), checkMove = false)
        idx = idx + 1
      case _ =>
    }
  }

  private def getSpawnPoint(playerId: String): (Int, Int, Int, Float) = {
    _def.spawnPoints(player2Idx(playerId))
  }

  def playerIdxInfo: mutable.Map[String, Int] = {
    player2Idx
  }
}

object BaseGameSceneFacade extends SceneFacade {
  override val keySet: Set[Int] = Set(SceneType.BASE_GAME)

  override def checkEnterScene(actor: Actor): Boolean = {
    true
  }

  override def apply(_def: SceneDef): Scene = {
    new BaseGameScene(genSceneId(_def), _def)
  }
}
