package com.example.commands

import com.example.holder.BaseGameSceneHolder
import com.example.message.Message

object Command04 extends IPlayerCommand {
  def handler01(playerId: String, message: Message): Unit = {
    val mapId: Int = message.getInt("mapId")
    BaseGameSceneHolder.addToMatchQueue(playerId, mapId)
  }

  def handler02(playerId: String, message: Message): Unit = {
    val mapTyp: String = message.getString("mapType")
    BaseGameSceneHolder.removeFromMatchQueue(playerId)
  }
}
