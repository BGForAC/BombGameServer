package com.example.actor

import com.example.holder.{PlayerHolder, SceneHolder}
import com.example.serer.PlayerChannels

class Player(pid: String) extends Actor(pid) {
  var uname: String = _

  private var offLine: Boolean = false

  PlayerHolder.addPlayer(this)
  def onDisConnect(): Unit = {
    offLine = true
    // 现在的设计是玩家断线了直接删了，后续可以改成离线状态，等玩家重连了再把数据加载回来, 倘若玩家长时间不重连了，才把数据删了
    val curScene = SceneHolder.getScene(movement.sceneId)
    if (curScene != null) {
      curScene.onExit(this)
    }
    PlayerHolder.removePlayer(pid)
    PlayerChannels.removeChannel(pid)
  }

  def baseInfo: Seq[(String, Any)] = {
    movement.info ++ attr.info
  }
}