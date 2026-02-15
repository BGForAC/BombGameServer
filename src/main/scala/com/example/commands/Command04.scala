package com.example.commands

import com.example.holder.{BaseGameSceneHolder, PlayerHolder}
import com.example.message.Message

object Command04 extends IPlayerCommand {
  def handler01(playerId: String, message: Message): Unit = {
    val mapId: Int = message.getInt("mapId")
    val career: String = message.getString("career")
    val controlConfig = message.getInt("controlConfig")
    val player = PlayerHolder.getPlayer(playerId)
    if (player == null) {
      throw new RuntimeException("玩家未登录")
    }
    player.attr.initAttr(career)
    player.career = career
    player.controlConfig = controlConfig
    BaseGameSceneHolder.addToMatchQueue(playerId, mapId)
  }

  def handler02(playerId: String, message: Message): Unit = {
    BaseGameSceneHolder.removeFromMatchQueue(playerId)
  }
}
