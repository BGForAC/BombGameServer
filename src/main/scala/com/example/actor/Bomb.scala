package com.example.actor

import com.example.holder.SceneHolder
import com.example.message.MessageBody

/**
 * Bomb类表示游戏中的一个炸弹对象，继承自Actor类
 * @param owner 炸弹的所有者Actor
 * @param id 炸弹的唯一标识符
 */
class Bomb(owner: Actor, id: String) extends Actor(id) {
  // 炸弹爆炸的时间点，为当前时间加上所有者设置的引信时间
  private val explodeTime: Long = System.currentTimeMillis() + owner.attr.FuseTime

  /**
   * 每个游戏tick调用的方法，用于更新炸弹状态
   * @param tickIdx 当前tick的索引
   */
  override def tick(tickIdx: Long): Unit = {
    // 检查是否到达爆炸时间
    if (System.currentTimeMillis() >= explodeTime) {
      explode() // 执行爆炸
      // 从场景中移除炸弹
      SceneHolder.exitScene(this.movement.sceneId, this)
    }
  }

  /**
   * 炸弹爆炸的方法
   * 计算爆炸范围内的所有玩家并造成伤害
   */
  def explode(): Unit = {
    // 获取炸弹所在的场景
    val scene = SceneHolder.getScene(movement.sceneId)
    // 检查场景是否存在，不存在则抛出异常
    if (scene == null) throw new IllegalStateException(s"炸弹[$id]所在的场景[${movement.sceneId}]不存在")
    // 获取场景中所有玩家
    val players = scene.players.values
    // 遍历每个玩家，检查是否在爆炸范围内
    players.foreach { player =>
      if (player.movement.inRange(this.movement, owner.attr.BombRadius)) {
        // 在范围内则对玩家造成伤害
        player.hpChange(this, owner.attr.BombDamage)
      }
    }
  }

  /**
   * 炸弹基本信息
   *
   */
  def BombInfo(extraInfo: MessageBody = MessageBody()) : MessageBody ={
    extraInfo += "BombId" -> id
    extraInfo += "explodeTime" -> explodeTime
    extraInfo += "createTime" -> (explodeTime - owner.attr.FuseTime)
    extraInfo
  }
}

/**
 * Bomb的伴生对象，提供炸弹的工厂方法和计数器功能
 */
object Bomb {
  // 按所有者ID存储的炸弹计数器，用于生成唯一ID
  private var bombIdCounter: Map[String, Int] = Map.empty.withDefaultValue(0)
  // 全局炸弹计数器，用于无所有者的炸弹
  private var globalBombIdCounter: Int = 0

  /**
   * 创建炸弹的工厂方法
   * @param owner 炸弹的所有者，可以为null表示无所有者的炸弹
   * @return 创建的Bomb实例
   */
  def apply(owner: Actor): Bomb = {
    // 如果没有所有者，创建一个全局ID的炸弹
    if (owner == null) {
      val bomb = new Bomb(null, s"global-${globalBombIdCounter}")
      globalBombIdCounter += 1
      return bomb
    }
    // 有所有者时，使用所有者ID和计数器创建炸弹ID
    val ownerId = owner.id
    val bombId = s"$ownerId-${bombIdCounter(ownerId)}"
    // 更新计数器
    bombIdCounter += (ownerId -> (bombIdCounter(ownerId) + 1))
    // 创建并返回炸弹实例
    new Bomb(owner, bombId)
  }
}
