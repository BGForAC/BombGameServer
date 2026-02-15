package com.example.actor

import com.example.commands.CmdType
import com.example.exception.ThrowBusinessException
import com.example.holder.SceneHolder
import com.example.message.{Message, MessageBody}
import com.example.scene.Scene
import com.example.serer.PlayerChannels

class Movement(owner: Actor) {
  private var x: Int = 0
  private var y: Int = 0
  private var z: Int = 0
  private var angle: Int = 0

  private var lastX: Int = 0
  private var lastY: Int = 0
  private var lastZ: Int = 0

  var sceneId: String = _
  var lastSceneId: String = _
  private var lastTime: Long = _

  def setToScene(scene: Scene): Unit = {
    sceneId = scene.id
  }

  def setOutScene(scene: Scene): Unit = {
    if (sceneId == null) {
      println(s"玩家${owner.id}尝试退出一个不在的场景，当前场景：null，尝试退出的场景：${scene.id}")
      return
    }
    if (sceneId != scene.id) {
      println(s"玩家${owner.id}尝试退出一个不在的场景，当前场景：$sceneId，尝试退出的场景：${scene.id}")
      return
    }

    lastSceneId = scene.id
    sceneId = null
  }

  def tick(tickIndex: Long): Unit = {
  }

  def setPosition(pos: (Int, Int, Int, Int), checkMove: Boolean = true): Unit = {
    if (sceneId == null) {
      return
    }
    val curScene = SceneHolder.getScene(sceneId)
    if (curScene == null) {
      println(s"玩家${owner.id}尝试在不存在的场景移动，当前场景：$sceneId")
      return
    }

    lastX = x
    lastY = y
    lastZ = z
    x = pos._1
    y = pos._2
    z = pos._3
    angle = pos._4
    if (checkMove) {
      checkValidMove()
    }
    if (!curScene.walkable(x, y, z)) {
      println(s"玩家${owner.id}尝试在不可行走的场景位置移动，当前场景：$sceneId，位置：($x, $y, $z)")
      return
    }
    curScene.players.filter(_._1 != owner.id).values.foreach { player =>
      PlayerChannels.send(player.id, Message(CmdType.MOVE, MessageBody((Seq("id" -> owner.id) ++ info): _*)))
    }
  }

  private def checkValidMove(): Unit = {
//    val distance = Math.sqrt(Math.pow(x - lastX, 2) + Math.pow(y - lastY, 2) + Math.pow(z - lastZ, 2))
    val distance = Math.sqrt(Math.pow(x - lastX, 2) + Math.pow(z - lastZ, 2))
    // 暂时不考虑网络延迟等因素，直接按照20ms的tick来算，如果玩家的移动超过了这个速度，就认为是异常移动
    val passTime = System.currentTimeMillis() - lastTime + 20
    val speed = owner.attr.getSpeed
    val maxDistance = speed * (passTime.toDouble / 1000.0)
    lastTime = System.currentTimeMillis()
    if (distance > maxDistance) {
      ThrowBusinessException("移动速度异常")
    }
  }

  def info: Seq[(String, Any)] = {
    Seq("x" -> x, "y" -> y, "z" -> z, "angle" -> angle)
  }
}
