package com.example.actor

import com.example.commands.CmdType
import com.example.holder.SceneHolder
import com.example.message.{Message, MessageBody}
import com.example.scene.Scene
import com.example.serer.PlayerChannels

/**
 * Actor抽象类，代表游戏中的基本行为实体
 * @param aid Actor的唯一标识符
 */
abstract class Actor(aid: String) {
  // 创建移动组件，引用当前Actor实例
  val movement: Movement = new Movement(this)
  // 创建基础属性组件，引用当前Actor实例
  val attr: BaseAttr = new BaseAttr(this)

  /**
   * 游戏循环中的tick方法，用于更新Actor状态
   * @param tickIdx 当前游戏循环的索引值
   */
  def tick(tickIdx: Long): Unit = {
    movement.tick(tickIdx)
  }

  /**
   * 获取Actor的ID
   * @return Actor的唯一标识符
   */
  def id: String = aid

  /**
   * 将Actor设置到指定场景中
   * @param scene 要设置的目标场景
   */
  def setToScene(scene: Scene): Unit = {
    movement.setToScene(scene)
  }

  /**
   * 将Actor从当前场景中移除
   * @param scene 要移除的场景
   */
  def setOutScene(scene: Scene): Unit = {
    movement.setOutScene(scene)
  }

  /**
   * 处理Actor的生命值变化
   * @param source 造成伤害的来源Actor
   * @param damage 伤害值
   */
  def hpChange(source: Actor, damage: Int): Unit = {
    attr.hp = attr.hp - damage
    // 获取Actor当前所在的场景
    val scene = SceneHolder.getScene(movement.sceneId)
    // 如果场景不存在，抛出异常
    if (scene == null) throw new IllegalStateException(s"玩家[$id]所在的场景[${movement.sceneId}]不存在")
    // 向场景中的所有玩家广播生命值变化消息
    scene.players.foreach { case (_, p) =>
      PlayerChannels.send(p.id, Message(CmdType.HP_CHANGE, MessageBody(("id", id), ("hp", attr.hp))))
    }
  }
}
