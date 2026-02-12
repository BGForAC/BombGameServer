package com.example.scene

import com.example.actor.{Actor, Player}
import com.example.commands.CmdType
import com.example.config.SceneDef
import com.example.message.{Message, MessageBody}
import com.example.serer.PlayerChannels

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
        PlayerChannels.send(player.id, Message(CmdType.ENTER_BASE_GAME, MessageBody((Seq("index" -> idx) ++ player.baseInfo): _*)))
        player.movement.setPosition(getSpawnPoint(player.id))
        idx = idx + 1
      case _ =>
    }
  }

  private def getSpawnPoint(playerId: String): (Int, Int, Int, Int) = {
    _def.spawnPoints(player2Idx(playerId))
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
