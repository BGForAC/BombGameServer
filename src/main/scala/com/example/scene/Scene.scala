package com.example.scene

import com.example.actor.{Actor, Bomb, Player}
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
  val map: GameMap = new GameMap(this)

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

    // 每帧广播玩家状态同步（HP + 属性 + 位置），确保客户端及时反映服务端权威状态
    if (players.nonEmpty) {
      players.values.foreach { p =>
        val info = p.movement.info
        if (!info.isEmpty) {
          PlayerChannels.send(p.id, Message(CmdType.PLAYER_SYNC, MessageBody(
            Seq(
              "id" -> p.id, "hp" -> p.attr.hp, "maxHp" -> p.attr.maxHp,
              "level" -> p.attr.level, "exp" -> p.attr.exp,
              "maxStamina" -> p.attr.maxStamina
            ) ++ info: _*
          )))
        }
      }
    }

    // 每60 tick输出一次场景摘要（约1秒，便于排查）
    if (tickIdx % 60 == 0) {
      val bombCount = actors.values.count(_.isInstanceOf[Bomb])
      if (bombCount > 0 || players.nonEmpty) {
        val hpInfo = players.values.map(p =>
          s"${p.id}:HP=${p.attr.hp}/${p.attr.maxHp} Lv.${p.attr.level} Exp=${p.attr.exp}"
        ).mkString(", ")
        println(s"[Scene.tick] tick#$tickIdx 场景[$sceneId]: 总actor=${actors.size}, 炸弹=$bombCount, 玩家=${players.size} [$hpInfo]")
      }
    }
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
      PlayerChannels.send(player.id, Message(CmdType.ENTER_SCENE, MessageBody(Seq("pid" -> actor.id) ++ actor.movement.info: _*)))
    }
    // 如果进入的是玩家，则添加到玩家列表
    actor match {
      case player: Player =>
        players += (player.id -> player)
        println(s"[Scene.onEnter] 玩家[${player.id}]进入场景[$sceneId], 当前玩家=${players.size}")
      case bomb: Bomb =>
        println(s"[Scene.onEnter] 炸弹[${bomb.id}]进入场景[$sceneId], 当前actor总数=${actors.size}")
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
   * 根据ID查找场景中的Actor
   * @param actorId Actor的唯一标识符
   * @return 找到的Actor，如果不存在则返回null
   */
  def getActor(actorId: String): Actor = {
    actors.getOrElse(actorId, null)
  }

  /**
   * 获取场景中所有Actor（用于炸弹爆炸时查找范围内的其他炸弹）
   */
  def getAllActors: Iterable[Actor] = actors.values

  /**
   * 查找场景中指定格子坐标上的所有炸弹（用于连锁爆炸）
   * @param gridX 网格X坐标（对应 3D X）
   * @param gridZ 网格Y坐标（对应 3D Z，参数名 gridZ 为历史遗留）
   */
  def getBombsAtGrid(gridX: Int, gridZ: Int, gridSize: Int = 100, offsetDistance: Int = 15): List[Bomb] = {
    actors.values.collect {
      case bomb: Bomb if Math.floor(bomb.movement.info.getInt("x").toDouble / gridSize).toInt + offsetDistance == gridX &&
                        Math.floor(bomb.movement.info.getInt("z").toDouble / gridSize).toInt + offsetDistance == gridZ =>
        bomb
    }.toList
  }

  /**
   * 检查指定坐标点是否可通行
   * 服务端坐标单位为世界坐标×100；映射关系：server(x) = 3D X, server(z) = 3D Z, server(y) = 高度
   * @param x 服务端X坐标（世界坐标×100，对应 3D X）
   * @param y 服务端Y坐标（高度）
   * @param z 服务端Z坐标（世界坐标×100，对应 3D Z）
   * @return 是否可通行
   */
  def walkable(x: Int, y: Int, z: Int): Boolean = {
    map.walkable(x, y, z)
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