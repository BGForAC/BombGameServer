package com.example.tick

import com.example.holder.{BaseGameRoomHolder, BaseGameSceneHolder, SceneHolder}

/**
 * TickManager 是一个单例对象，负责管理游戏中的"tick"计数器
 * 它会在每个游戏循环中增加tick计数，并通知相关的游戏组件处理tick事件
 */
object TickManager {
  // 当前游戏tick计数器，从0开始，每tick一次增加1
  private var currentTick: Long = 0

  /**
   * tick方法会在每个游戏循环中被调用
   * 它会增加当前tick计数，并通知所有游戏场景和房间处理新的tick
   */
  def tick(): Unit = {
    // 增加当前tick计数
    currentTick += 1
    // 通知场景持有者处理当前tick
    SceneHolder.tick(currentTick)
    // 通知基础游戏场景持有者处理当前tick
    BaseGameSceneHolder.tick(currentTick)
    // 通知基础游戏房间持有者处理当前tick
    BaseGameRoomHolder.tick(currentTick)
  }
}
