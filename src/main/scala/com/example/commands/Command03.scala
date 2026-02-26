package com.example.commands

import com.example.holder.PlayerHolder

/**
 * Command03 类实现了 IPlayerCommand 接口，用于处理玩家位置更新的命令
 * 该命令从消息中解析玩家的坐标和角度信息，并更新到玩家的位置信息中
 */
object Command03 extends IPlayerCommand {
  /**
   * 处理玩家位置更新的方法
   * @param playerId 玩家ID，用于标识需要更新位置的玩家
   * @param message 包含位置信息的消息对象，其中包含x、y、z坐标和角度信息
   */
  def handler01(playerId: String, message: com.example.message.Message): Unit = {
    // 从消息中获取玩家的x坐标
    val x = message.getInt("x")
    // 从消息中获取玩家的y坐标
    val y = message.getInt("y")
    // 从消息中获取玩家的z坐标
    val z = message.getInt("z")
    // 从消息中获取玩家的朝向角度
    val angle = message.getFloat("angle")
    // 根据玩家ID获取玩家对象
    val player = PlayerHolder.getPlayer(playerId)
    // 更新玩家的位置信息，包括坐标和朝向角度
    player.movement.setPosition((x, y, z, angle))
  }
}
