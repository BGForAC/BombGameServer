package com.example.commands

import com.example.holder.{PlayerHolder, SceneHolder}
import com.example.message.{Message, MessageBody}
import com.example.serer.PlayerChannels

/**
 * Command05 类，实现了 IPlayerCommand 接口，处理玩家放置炸弹的相关命令
 */
object Command05 extends IPlayerCommand {
  /**
   * handler01 方法处理玩家放置炸弹的逻辑
   * @param playerId 玩家的唯一标识符
   * @param message 包含命令信息的消息对象
   */
  def handler01(playerId: String, message: Message): Unit = {
    // 从 PlayerHolder 中获取玩家对象
    val player = PlayerHolder.getPlayer(playerId)
    // 调用玩家对象的 putBomb 方法放置炸弹
    player.putBomb()

    // 获取玩家所在的场景ID
    val sceneId = player.movement.sceneId
    // 从 SceneHolder 中获取场景对象
    val scene = SceneHolder.getScene(sceneId)
    // 向场景中所有玩家广播放置炸弹的消息
    scene.players.foreach { case (_, p) =>
      // 使用 PlayerChannels 发送消息，通知其他玩家有玩家放置了炸弹
      PlayerChannels.send(p.id, Message(CmdType.PUT_BOMB, player.baseInfo))
    }
  }
}
