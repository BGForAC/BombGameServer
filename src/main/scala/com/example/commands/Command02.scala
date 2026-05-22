package com.example.commands

import com.example.exception.ThrowBusinessException
import com.example.holder.{PlayerHolder, SceneHolder}
import com.example.message.Message

/**
 * Command02 类实现了 IPlayerCommand 接口，用于处理玩家进入场景的相关命令
 * 该类提供了 handler01 方法来处理玩家进入场景的业务逻辑
 */
object Command02 extends IPlayerCommand {
  /**
   * ENTER_SCENE = 0x0201  进入场景命令
   * 处理玩家进入场景的方法
   * @param playerId 玩家的唯一标识符
   * @param message 包含场景ID等信息的消息对象
   * @throws ThrowBusinessException 当玩家未登录或不能进入场景时抛出业务异常
   */
  def handler01(playerId: String, message: Message): Unit = {
    // 从消息中获取场景ID
    val sceneId = message.getString("sceneId")
    // 根据玩家ID获取玩家对象
    val player = PlayerHolder.getPlayer(playerId)
    // 检查玩家是否已登录，若未登录则抛出业务异常
    if (player == null) {
      ThrowBusinessException(s"玩家未登录")
    }
    // 检查玩家是否有权限进入指定场景，若无权限则抛出业务异常
    if (!SceneHolder.checkEnterScene(sceneId, player)) {
      ThrowBusinessException(s"你不能进入该场景")
    }
    SceneHolder.enterScene(sceneId, player)
  }
}
