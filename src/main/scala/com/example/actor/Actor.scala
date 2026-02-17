package com.example.actor

import com.example.commands.CmdType
import com.example.holder.SceneHolder
import com.example.message.{Message, MessageBody}
import com.example.scene.Scene
import com.example.serer.PlayerChannels

abstract class Actor(aid: String) {
  val movement: Movement = new Movement(this)
  val attr: BaseAttr = new BaseAttr(this)

  def tick(tickIdx: Long): Unit = {
    movement.tick(tickIdx)
  }

  def id: String = aid

  def setToScene(scene: Scene): Unit = {
    movement.setToScene(scene)
  }

  def setOutScene(scene: Scene): Unit = {
    movement.setOutScene(scene)
  }

  def hpChange(source: Actor, damage: Int): Unit = {
    attr.hp = attr.hp - damage
    val scene = SceneHolder.getScene(movement.sceneId)
    if (scene == null) throw new IllegalStateException(s"玩家[$id]所在的场景[${movement.sceneId}]不存在")
    scene.players.foreach { case (_, p) =>
      PlayerChannels.send(p.id, Message(CmdType.HP_CHANGE, MessageBody(("id", id), ("hp", attr.hp))))
    }
  }
}
