package com.example.actor

import com.example.commands.CmdType
import com.example.exception.ThrowBusinessException
import com.example.holder.SceneHolder
import com.example.message.{Message, MessageBody}
import com.example.scene.{BaseGameScene, Scene}
import com.example.serer.PlayerChannels
import com.example.thread.EventThread

class Movement(owner: Actor) {
  private val SCALE: Int = 100
  private var x: Int = 0
  private var y: Int = 0
  private var z: Int = 0
  private var angle: Int = 0
  var sceneId: String = _
  var lastSceneId: String = _
  private var lastTime: Long = _

  def setToScene(scene: Scene): Unit = {
    sceneId = scene.id
    scene match {
      case bgs: BaseGameScene =>
        setPosition(bgs.getPlayerSpawnPoint(owner.id))
      case _ =>
        setPosition(scene._def.spawnPoints(0))
    }
  }

  def setOutScene(scene: Scene): Unit = {
    lastSceneId = scene.id
    sceneId = null
  }

  def tick(tickIndex: Long): Unit = {
  }

  def setPosition(pos: (Int, Int, Int, Int)): Unit = {
    if (sceneId == null) {
      return
    }
    checkValidMove(pos)
    x = pos._1
    y = pos._2
    z = pos._3
    angle = pos._4
    SceneHolder.getScene(sceneId).players.filter(_._1 != owner.id).values.foreach { player =>
      PlayerChannels.send(player.id, Message(CmdType.MOVE, MessageBody((Seq("id" -> owner.id) ++ info): _*)))
    }
    lastTime = System.currentTimeMillis()
  }

  private def checkValidMove(pos: (Int, Int, Int, Int)): Unit = {
    val (newX, newY, newZ, _) = pos
    val distance = Math.sqrt(Math.pow(newX - x, 2) + Math.pow(newY - y, 2) + Math.pow(newZ - z, 2)) / Math.pow(SCALE, 2)
    val perSecondTick = EventThread.framePerSec
    // 暂时不考虑网络延迟等因素
    val passTime = System.currentTimeMillis() - lastTime
    val speed = owner.attr.currentSpeed / SCALE
    val maxDistance = speed * (passTime.toDouble / 1000.0)
    if (distance > maxDistance) {
      ThrowBusinessException("移动速度异常")
    }
  }

  def info: Seq[(String, Any)] = {
    Seq("x" -> x, "y" -> y, "z" -> z, "angle" -> angle)
  }
}
