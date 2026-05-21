package com.example.commands

import com.example.holder.{PlayerHolder, SceneHolder}
import com.example.message.Message
import com.example.props.PropsItem

/**
 * Command08 道具系统命令处理器
 * 实现 IPlayerCommand，处理玩家发送的道具相关命令
 */
object Command08 extends IPlayerCommand {

  /**
   * 处理道具拾取命令 (PROP_PICKED_UP = 0x0802)
   * 客户端检测到玩家触碰道具后发送
   *
   * @param playerId 玩家ID
   * @param message 包含 itemId（道具实例ID）的消息
   */
  def handler02(playerId: String, message: Message): Unit = {
    val itemId = message.getString("itemId")

    if (itemId.isEmpty) {
      println(s"[Command08] 玩家[$playerId]发送的道具拾取消息缺少 itemId")
      return
    }

    val player = PlayerHolder.getPlayer(playerId)
    if (player == null) {
      println(s"[Command08] 玩家[$playerId]不存在，道具拾取失败")
      return
    }

    // 获取玩家当前所在场景
    val sceneId = player.movement.sceneId
    if (sceneId == null) {
      println(s"[Command08] 玩家[$playerId]不在任何场景中")
      return
    }

    val scene = SceneHolder.getScene(sceneId)
    if (scene == null) {
      println(s"[Command08] 场景[$sceneId]不存在")
      return
    }

    // 在场景中查找道具Actor
    val actor = scene.getActor(itemId)
    actor match {
      case propsItem: PropsItem =>
        // 验证拾取距离（简化：坐标乘以100对齐客户端坐标）
        val itemPos = propsItem.movement.info
        val px = itemPos.getInt("x")
        val py = itemPos.getInt("y")
        val pz = itemPos.getInt("z")
        val playerPos = player.movement.info
        val plx = playerPos.getInt("x")
        val ply = playerPos.getInt("y")
        val plz = playerPos.getInt("z")
        val dist = Math.sqrt(Math.pow(px - plx, 2) + Math.pow(py - ply, 2) + Math.pow(pz - plz, 2))
        // 拾取范围：200单位（对应Unity中2个单位距离）
        if (dist <= 200) {
          propsItem.onPickUp(playerId)
        } else {
          println(s"[Command08] 玩家[$playerId]距离道具[$itemId]太远 ($dist)，拒绝拾取")
        }
      case _ =>
        println(s"[Command08] 道具[$itemId]不存在或已被拾取")
    }
  }
}
