package com.example.commands

import com.example.holder.{PlayerHolder, SceneHolder}
import com.example.message.{Message, MessageBody}
import com.example.serer.PlayerChannels

object Command05 extends IPlayerCommand {
  def handler01(playerId: String, message: Message): Unit = {
    val player = PlayerHolder.getPlayer(playerId)
    player.putBomb()

    val sceneId = player.movement.sceneId
    val scene = SceneHolder.getScene(sceneId)
    scene.players.foreach { case (_, p) =>
      PlayerChannels.send(p.id, Message(CmdType.PUT_BOMB, MessageBody(player.baseInfo: _*)))
    }

  }
}
