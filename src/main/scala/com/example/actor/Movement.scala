package com.example.actor

import com.example.commands.CmdType
import com.example.exception.ThrowBusinessException
import com.example.holder.SceneHolder
import com.example.message.{Message, MessageBody}
import com.example.scene.Scene
import com.example.serer.PlayerChannels

/**
 * Movement类用于管理角色的移动相关功能
 * @param owner 拥有此Movement对象的Actor实例
 */
class Movement(owner: Actor) {
  // 当前坐标
  private var x: Int = 0          // 当前X坐标
  private var y: Int = 0          // 当前Y坐标
  private var z: Int = 0          // 当前Z坐标
  private var angle: Float = 0    // 当前角色朝向角度

  // 上一次的坐标
  private var lastX: Int = 0      // 上一次X坐标
  private var lastY: Int = 0      // 上一次Y坐标
  private var lastZ: Int = 0      // 上一次Z坐标

  // 当前场景ID和上一个场景ID
  var sceneId: String = _         // 当前所在场景ID
  var lastSceneId: String = _    // 上一个场景ID
  // 上一次移动的时间戳
  private var lastTime: Long = _  // 上一次移动的时间戳

  /**
   * 检查另一个Movement对象是否在指定范围内
   * @param movement 另一个Movement对象
   * @param range 范围阈值
   * @return 如果在范围内返回true，否则返回false
   */
  def inRange(movement: Movement, range: Float): Boolean = {
    // 计算两个角色之间的欧几里得距离
    val distance = Math.sqrt(Math.pow(x - movement.x, 2) + Math.pow(y - movement.y, 2) + Math.pow(z - movement.z, 2))
    // 判断距离是否在指定范围内
    distance <= range
  }

  /**
   * 将角色设置到指定场景中
   * @param scene 要进入的场景对象
   */
  def setToScene(scene: Scene): Unit = {
    // 设置当前场景ID
    sceneId = scene.id
  }

  /**
   * 将角色从指定场景中移除
   * @param scene 要离开的场景对象
   */
  def setOutScene(scene: Scene): Unit = {
    // 检查角色是否在要离开的场景中
    if (sceneId == null) {
      println(s"玩家${owner.id}尝试退出一个不在的场景，当前场景：null，尝试退出的场景：${scene.id}")
      return
    }
    // 检查场景ID是否匹配
    if (sceneId != scene.id) {
      println(s"玩家${owner.id}尝试退出一个不在的场景，当前场景：$sceneId，尝试退出的场景：${scene.id}")
      return
    }

    // 更新场景ID
    lastSceneId = scene.id
    sceneId = null
  }

  /**
   * 定时更新方法
   * @param tickIndex 当前tick索引
   */
  def tick(tickIndex: Long): Unit = {
    // 当前方法为空实现，可能用于后续扩展
  }

  /**
   * 设置角色位置
   * @param pos 包含x,y,z坐标和角度的元组
   * @param checkMove 是否检查移动的合法性
   */
  def setPosition(pos: (Int, Int, Int, Float), checkMove: Boolean = true): Unit = {
    // 检查角色是否在场景中
    if (sceneId == null) {
      return
    }
    // 获取当前场景
    val curScene = SceneHolder.getScene(sceneId)
    if (curScene == null) {
      println(s"玩家${owner.id}尝试在不存在的场景移动，当前场景：$sceneId")
      return
    }

    // 保存上一次的位置
    lastX = x
    lastY = y
    lastZ = z
    // 更新当前位置
    x = pos._1
    y = pos._2
    z = pos._3
    angle = pos._4
    // 检查移动是否合法
    if (checkMove) {
      checkValidMove()
    }
    // 检查目标位置是否可行走
    if (!curScene.walkable(x, y, z)) {
      println(s"玩家${owner.id}尝试在不可行走的场景位置移动，当前场景：$sceneId，位置：($x, $y, $z)")
      return
    }
    // 向场景中其他玩家广播移动消息
    curScene.players.filter(_._1 != owner.id).values.foreach { player =>
      PlayerChannels.send(player.id, Message(CmdType.MOVE, MessageBody((Seq("id" -> owner.id) ++ info): _*)))
    }
  }

  /**
   * 检查移动是否合法
   */
  private def checkValidMove(): Unit = {
//    val distance = Math.sqrt(Math.pow(x - lastX, 2) + Math.pow(y - lastY, 2) + Math.pow(z - lastZ, 2))
    val distance = Math.sqrt(Math.pow(x - lastX, 2) + Math.pow(z - lastZ, 2))  // 计算移动距离（XZ平面）
    // 暂时不考虑网络延迟等因素，直接按照20ms的tick来算，如果玩家的移动超过了这个速度，就认为是异常移动
    val passTime = System.currentTimeMillis() - lastTime + 20               // 计算经过的时间
    val speed = owner.attr.Speed                                             // 获取角色移动速度
    val maxDistance = speed * (passTime.toDouble / 1000.0)                  // 计算最大允许移动距离
    lastTime = System.currentTimeMillis()                                    // 更新最后移动时间
    if (distance > maxDistance) {
      ThrowBusinessException("移动速度异常")  // 如果移动距离超过最大允许距离，抛出异常
    }
  }

  /**
   * 获取角色位置信息
   * @return 包含角色位置信息的序列
   */
  def info: Seq[(String, Any)] = {
    if (sceneId == null) return Seq.empty
    Seq("x" -> x, "y" -> y, "z" -> z, "angle" -> angle)
  }
}
