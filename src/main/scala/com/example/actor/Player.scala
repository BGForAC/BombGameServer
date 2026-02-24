package com.example.actor

import com.example.exception.ThrowBusinessException
import com.example.holder.{PlayerHolder, SceneHolder}
import com.example.scene.Scene
import com.example.serer.PlayerChannels

class Player(pid: String) extends Actor(pid) {
  var uname: String = _

  var career: String = _

  var controlConfig: Int = _

  private var offLine: Boolean = false

  private var bombNum: Int = 0

  private var lastPutBombTime: Long = 0

  private var lastRecoverBombTime: Long = 0

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

  override def tick(tickIdx: Long): Unit = {
    super.tick(tickIdx)
    if (offLine) {
      return
    }
    if (bombNum > 0 && System.currentTimeMillis() - lastRecoverBombTime >= attr.BombRecoveryTime) {
      bombNum = bombNum - 1
      lastRecoverBombTime = System.currentTimeMillis()
    }
  }

  def baseInfo: Seq[(String, Any)] = {
//    movement.info ++ attr.info
    movement.info ++ Seq(("id", id), ("uname", uname), ("career", career), ("controlConfig", controlConfig))
  }

  def baseInfoStr(extraInfo: Seq[(String, Any)] = Seq.empty): String = {
    (extraInfo ++ baseInfo).map{case (k, v) => s"$k:$v"}.mkString(",")
  }

  override def setOutScene(scene: Scene): Unit = {
    super.setOutScene(scene)
    career = null
    controlConfig = 0
  }

  def putBomb(): Unit = {
    if (bombNum >= attr.MaxBombCount) ThrowBusinessException(s"你放的炸弹太多了，等炸弹爆炸了再放吧")
//    println(s"玩家[$id]放了一个炸弹 当前炸弹数量[$bombNum] 总共炸弹数量[${attr.bombFuseTime}] 当前时间[${System.currentTimeMillis()}] 上次放炸弹时间[$lastPutBombTime] 炸弹冷却时间[${attr.Cooldown}]")
    if (System.currentTimeMillis() - lastPutBombTime < attr.Cooldown) ThrowBusinessException(s"你放炸弹太快了，等会再放吧")

    SceneHolder.enterScene(movement.sceneId, Bomb(this))

    bombNum = bombNum + 1
    lastPutBombTime = System.currentTimeMillis()
    if (bombNum == 1) {
      // 只有当炸弹从满到不满的时候才开始计算恢复炸弹的时间
      lastRecoverBombTime = System.currentTimeMillis()
    }
  }
}