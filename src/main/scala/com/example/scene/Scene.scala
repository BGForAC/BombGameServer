package com.example.scene

import com.example.actor.{Actor, Player}
import com.example.commands.CmdType
import com.example.config.SceneDef
import com.example.exception.ThrowBusinessException
import com.example.holder.SceneHolder
import com.example.message.{Message, MessageBody}
import com.example.reflect.ScanAble
import com.example.serer.PlayerChannels

import scala.collection.mutable

/**
 * Scene类是一个抽象类，代表游戏中的一个场景
 * @param sceneId 场景的唯一标识符
 * @param sceneDef 场景的定义配置
 */
abstract class Scene(sceneId: String, sceneDef: SceneDef) {
  //private val map = new GameMap(this)

  // 将当前场景添加到场景持有者中
  SceneHolder.addScene(this)

  // 存储场景中所有角色的可变映射表
  private val actors: mutable.Map[String, Actor] = mutable.Map()

  // 存储场景中所有玩家的可变映射表
  val players: mutable.Map[String, Player] = mutable.Map()

  // 获取场景ID
  def id: String = sceneId

  // 获取场景定义配置
  def _def: SceneDef = sceneDef

  /**
   * 场景的tick方法，用于处理场景内的逻辑更新
   * @param tickIdx 当前tick的索引值
   */
  def tick(tickIdx: Long): Unit = {
    // 遍历场景中的所有角色并调用其tick方法
    actors.values.foreach(actor => actor.tick(tickIdx))
  }

  /**
   * 处理角色进入场景的逻辑
   * @param actor 要进入场景的角色
   */
  def onEnter(actor: Actor): Unit = {
    // 将角色添加到场景中
    actors += (actor.id -> actor)
    // 设置角色的当前场景
    actor.setToScene(this)
    // 通知场景中的所有玩家有新角色进入
    players.values.foreach{ player =>
      PlayerChannels.send(player.id, Message(CmdType.ENTER_SCENE, MessageBody((Seq("pid" -> actor.id) ++ actor.movement.info): _*)))
    }
    // 如果进入的是玩家，则添加到玩家列表
    actor match {
      case player: Player =>
        players += (player.id -> player)
      case _ =>
    }
  }

  /**
   * 检查角色是否可以进入场景
   * @param actor 要进入场景的角色
   * @return 是否允许进入
   */
  def checkEnterScene(actor: Actor): Boolean = {
    actor match {
      case _: Player =>
        // 检查场景玩家数量是否已满
        if (players.size >= _def.maxPlayerCnt) {
          ThrowBusinessException("场景人数已满")
        } else {
          true
        }
      case _ =>
        true
    }
  }

  /**
   * 处理角色离开场景的逻辑
   * @param actor 要离开场景的角色
   */
  def onExit(actor: Actor): Unit = {
    // 从场景中移除角色
    actors -= actor.id
    // 通知场景中的所有玩家有角色离开
    players.values.foreach{ player =>
      PlayerChannels.send(player.id, new Message(CmdType.EXIT_SCENE, MessageBody("aid" -> actor.id)))
    }
    // 如果离开的是玩家，则从玩家列表中移除
    actor match {
      case player: Player =>
        players -= player.id
      case _ =>
    }
    // 设置角色的当前场景为空
    actor.setOutScene(this)
  }

  /**
   * 检查指定坐标点是否可通行
   * @param x X坐标
   * @param y Y坐标
   * @param z Z坐标
   * @return 是否可通行
   */
  def walkable(x: Int, y: Int, z: Int): Boolean = {
    true
    // 注释掉的代码可能是用于检查地图上某点是否可通行
    //map.walkable(x, y, z)
  }
}

/**
 * SceneFacade特质提供场景的接口和工具方法
 */
trait SceneFacade extends ScanAble[Int] {
  // 用于生成唯一标识的计数器
  private var uniqueKey = 0

  /**
   * 生成场景的唯一ID
   * @param _def 场景定义配置
   * @return 生成的场景ID
   */
  def genSceneId(_def: SceneDef): String = {
    uniqueKey += 1
    s"${_def.id}_$uniqueKey"
  }

  // 检查角色是否可以进入场景
  def checkEnterScene(actor: Actor): Boolean

  // 创建场景实例
  def apply(_def: SceneDef): Scene
}