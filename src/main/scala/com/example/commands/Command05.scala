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
   * @param message 包含命令信息的消息对象（客户端可附带位置坐标 x, y, z）
   */
  def handler01(playerId: String, message: Message): Unit = {
    // 从 PlayerHolder 中获取玩家对象
    val player = PlayerHolder.getPlayer(playerId)
    if (player == null) {
      println(s"[PutBomb] 玩家[$playerId]不存在，忽略放置请求")
      return
    }

    val hasPos = message.contains("x")
    println(s"[PutBomb] 收到玩家[$playerId]放置炸弹请求, 携带位置=${hasPos}")

    // 调用玩家对象的 putBomb 方法放置炸弹（只调用一次，修复双重放置 Bug）
    val bomb = player.putBomb()
    println(s"[PutBomb] 玩家[$playerId]炸弹创建成功: bombId=[${bomb.id}], owner=[${player.id}]")

    // 客户端可能上报了炸弹位置（在线模式下坐标×100）
    val bombX = if (hasPos) message.getInt("x") else player.movement.info.getInt("x")
    val bombY = if (message.contains("y")) message.getInt("y") else 0
    val bombZ = if (message.contains("z")) message.getInt("z") else player.movement.info.getInt("z")

    println(s"[PutBomb] 炸弹位置: ($bombX, $bombY, $bombZ), 玩家位置: (${player.movement.info.getInt("x")}, ${player.movement.info.getInt("z")})")

    // 设置炸弹位置（与客户端 Mathf.Ceil(x) - 0.5f 对齐：gridCell * 250 = worldPos）
    bomb.movement.setPosition((bombX, bombY, bombZ, 0f), checkMove = false)
    // 防止炸弹位置被 map.walkable 拒绝（炸弹可以放在任何可行走位置）

    // 将炸弹位置信息同步到所有玩家（包含炸弹 ID 和爆炸时间，客户端据此独立计时）
    val sceneId = player.movement.sceneId
    val scene = SceneHolder.getScene(sceneId)
    if (scene != null) {
      val playerCount = scene.players.size
      println(s"[PutBomb] 向场景[$sceneId]内${playerCount}名玩家广播PUT_BOMB: bombId=[${bomb.id}]")
      scene.players.foreach { case (_, p) =>
        PlayerChannels.send(p.id, Message(CmdType.PUT_BOMB, MessageBody(
          "id" -> player.id,
          "bombId" -> bomb.id,
          "x" -> bombX,
          "y" -> bombY,
          "z" -> bombZ
        )))
      }
    } else {
      println(s"[PutBomb] 警告: 场景[$sceneId]不存在，无法广播PUT_BOMB")
    }
  }
}
