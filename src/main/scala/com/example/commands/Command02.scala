package com.example.commands

import com.example.exception.ThrowBusinessException
import com.example.holder.{PlayerHolder, SceneHolder}
import com.example.message.{Message, MessageBody}
import com.example.serer.PlayerChannels

object Command02 extends IPlayerCommand {
  def handler01(playerId: String, message: Message): Unit = {
    val sceneId = message.getString("sceneId")
    val player = PlayerHolder.getPlayer(playerId)
    if (player == null) {
      ThrowBusinessException(s"玩家未登录")
    }
    if (!SceneHolder.checkEnterScene(sceneId, player)) {
      ThrowBusinessException(s"你不能进入该场景")
    }
    SceneHolder.enterScene(sceneId, player)
    PlayerChannels.send(playerId, message.response(MessageBody("sceneId" -> sceneId)))
  }
}
