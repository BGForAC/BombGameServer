package com.example.scene

import com.example.actor.{Actor, Player}
import com.example.commands.CmdType
import com.example.config.SceneDef
import com.example.message.{Message, MessageBody}
import com.example.serer.PlayerChannels

import scala.collection.mutable

class BaseGameScene(id: String, _def: SceneDef) extends Scene(id, _def) {
  private var spawnPointIdx: Int = 0
  private val player2SpawnPoint: mutable.Map[String, (Int, Int, Int, Int)] = mutable.Map.empty

  override def tick(tickIdx: Long): Unit = {
    super.tick(tickIdx)
  }

  override def onEnter(actor: Actor): Unit = {
    actor match {
      case player: Player =>
        player2SpawnPoint += player.id -> _def.spawnPoints(spawnPointIdx)
        super.onEnter(player)
        PlayerChannels.send(player.id, Message(CmdType.ENTER_BASE_GAME, MessageBody((Seq("index" -> spawnPointIdx) ++ player.baseInfo): _*)))
        spawnPointIdx = spawnPointIdx + 1
      case _ =>
    }
  }

  def getPlayerSpawnPoint(playerId: String): (Int, Int, Int, Int) = {
    player2SpawnPoint(playerId)
  }
}

object BaseGameSceneFacade extends SceneFacade {
  private var uniqueKey = 0

  override val keySet: Set[Int] = Set(SceneType.BASE_GAME)

  override def checkEnterScene(actor: Actor): Boolean = {
    true
  }

  override def apply(_def: SceneDef): Scene = {
    uniqueKey += 1
    val sceneId = s"${_def.id}_$uniqueKey"
    new BaseGameScene(sceneId, _def)
  }
}
