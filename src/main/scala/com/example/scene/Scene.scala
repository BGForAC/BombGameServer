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

  // 游戏是否已结束（防止重复触发）
  var isGameOver: Boolean = false
  // 是否为随机匹配模式（非房间创建）
  var isRandomMatch: Boolean = false

  // 上次同步的状态缓存（playerId → 上次发送的全量 MessageBody），用于增量同步
  // 仅发送与上次不一致的字段，减少网络传输量
  private val lastSyncState: mutable.Map[String, MessageBody] = mutable.Map()

  // 可复用的 MessageBody，避免每 tick 重复分配（clear + repopulate）
  private val reusableAllPlayersBody = new MessageBody()
  // 每玩家复用的 currentState 构建器（clear + 填充 → 仅在存储时快照克隆）
  private val reusableCurrentState = new MessageBody()
  // 每玩家复用的 delta 构建器
  private val reusableDelta = new MessageBody()

  // 获取场景ID
  def id: String = sceneId

  // 获取场景定义配置
  def _def: SceneDef = sceneDef

  /**
   * 场景的tick方法，用于处理场景内的逻辑更新
   * @param tickIdx 当前tick的索引值
   */
  def tick(tickIdx: Long): Unit = {
    // 游戏已结束：跳过所有 tick 逻辑，避免死场景空转
    if (isGameOver) return

    // 遍历场景中的所有角色并调用其tick方法
    actors.values.foreach(actor => actor.tick(tickIdx))

    // 每帧广播玩家状态增量同步：仅发送与上次缓存相比发生变化的状态字段
    // 首次同步发送全量，后续仅发送变化的字段（动态长度），大幅减少网络传输量
    if (players.nonEmpty) {
      reusableAllPlayersBody.clear()
      players.values.foreach { p =>
        val info = p.movement.info
        if (!info.isEmpty) {
          // 复用构建器：clear + 填充（避免每玩家每 tick 分配 MessageBody）
          reusableCurrentState.clear()
          reusableCurrentState ++= Seq(
            "id" -> p.id, "hp" -> p.attr.hp, "maxHp" -> p.attr.maxHp,
            "level" -> p.attr.level, "exp" -> p.attr.exp,
            "maxExpToLevelUp" -> p.attr.maxExpToLevelUp,
            "maxStamina" -> p.attr.maxStamina,
            // 体力与速度（服务端权威）
            "stamina" -> p.stamina,
            "currentSpeed" -> p.currentSpeed,
            "isStaminaEmpty" -> (if (p.isStaminaEmpty) 1 else 0),
            // 炸弹状态（服务端权威）
            "bombCount" -> (p.attr.MaxBombCount - p.bombNum),
            "bombCooldown" -> (p.bombCooldownRemaining / 1000f),
            "bombRecoveryTime" -> (p.bombRecoveryRemaining / 1000f),
            "maxBombCount" -> p.attr.MaxBombCount
          )
          reusableCurrentState ++= info

          // 获取上次同步的缓存状态
          val prevState = lastSyncState.get(p.id)

          // 构建增量消息体：仅包含变化的字段（首次同步则全量）
          val (deltaBody, hasChanges) = if (prevState.isEmpty) {
            (reusableCurrentState, true)  // 首次同步：发送全量
          } else {
            reusableDelta.clear()
            val prev = prevState.get
            reusableCurrentState.foreach { case (k, v) =>
              // 仅当 key 不存在于缓存，或值发生变化时才加入增量消息
              if (!prev.contains(k) || prev(k).toString != v.toString) {
                reusableDelta.put(k, v)
              }
            }
            (reusableDelta, reusableDelta.nonEmpty)
          }

          // 有变化才加入广播，并更新缓存（快照克隆：复用构建器在下一循环会被 clear）
          if (hasChanges) {
            reusableAllPlayersBody.put(p.id, MessageBody.addMessageBody(new MessageBody(), deltaBody))
            lastSyncState(p.id) = MessageBody.addMessageBody(new MessageBody(), reusableCurrentState)
          }
        }
      }
      if (!reusableAllPlayersBody.isEmpty) {
        PlayerChannels.sendToAll(Message(CmdType.PLAYER_SYNC, MessageBody("players" -> reusableAllPlayersBody)))
      }
    }

    // 每60 tick输出一次场景摘要（约1秒，便于排查）
//    if (tickIdx % 60 == 0) {
//      val bombCount = actors.values.count(_.isInstanceOf[Bomb])
//      if (bombCount > 0 || players.nonEmpty) {
//        val hpInfo = players.values.map(p =>
//          s"${p.id}:HP=${p.attr.hp}/${p.attr.maxHp} Lv.${p.attr.level} Exp=${p.attr.exp}"
//        ).mkString(", ")
//        println(s"[Scene.tick] tick#$tickIdx 场景[$sceneId]: 总actor=${actors.size}, 炸弹=$bombCount, 玩家=${players.size} [$hpInfo]")
//      }
//    }

    // 游戏结束检测：存活玩家 ≤ 1 时判定游戏结束（需要至少2名玩家才开始检测）
    if (!isGameOver && players.size > 1) {
      val aliveCount = players.values.count(_.attr.hp > 0)
      if (aliveCount <= 1) {
        isGameOver = true
        val winnerId = if (aliveCount == 1) players.values.find(_.attr.hp > 0).map(_.id).orNull else null
        //println(s"[Scene.GameOver] 场景[$sceneId] 游戏结束, 存活玩家=$aliveCount, 胜者=$winnerId, 随机匹配=$isRandomMatch")
        // 向所有玩家广播游戏结束消息
        players.values.foreach { p =>
          PlayerChannels.send(p.id, Message(CmdType.GAME_OVER, MessageBody(
            "winnerId" -> (if (winnerId != null) winnerId else ""),
            "isRandomMatch" -> (if (isRandomMatch) 1 else 0)
          )))
        }
        // 通知房间处理器游戏结束（房间模式下自动返回房间）
        com.example.holder.BaseGameRoomHolder.onGameOver(sceneId, isRandomMatch)

        // 清退所有玩家与残留 Actor：清理 actors Map 并解除场景引用
        // 注意：不能只删 players，Bomb 等非玩家 Actor 也可能残留在 actors 中
        actors.values.foreach { actor =>
          actor.setOutScene(this)
        }
        actors.clear()
        players.clear()
        lastSyncState.clear()  // 清理增量同步缓存，避免 Scene GC 前的短暂内存占用
        //println(s"[Scene.GameOver] 场景[$sceneId] 已清退所有玩家和 Actor，剩余actor=0")
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
    // 如果进入的是玩家，则添加到玩家列表
    actor match {
      case player: Player =>
        players += (player.id -> player)
//        println(s"[Scene.onEnter] 玩家[${player.id}]进入场景[$sceneId], 当前玩家=${players.size}")
      case bomb: Bomb =>
//        println(s"[Scene.onEnter] 炸弹[${bomb.id}]进入场景[$sceneId], 当前actor总数=${actors.size}")
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
    // 如果离开的是玩家，则从玩家列表中移除
    actor match {
      case player: Player =>
        players -= player.id
        lastSyncState -= player.id  // 清理增量同步缓存
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
  // 用于生成唯一标识的计数器（AtomicInteger 保证多房间并发创建场景时线程安全）
  private val uniqueKey = new java.util.concurrent.atomic.AtomicInteger(0)

  /**
   * 生成场景的唯一ID
   * @param _def 场景定义配置
   * @return 生成的场景ID
   */
  def genSceneId(_def: SceneDef): String = {
    s"${_def.id}_${uniqueKey.incrementAndGet()}"
  }

  // 检查角色是否可以进入场景
  def checkEnterScene(actor: Actor): Boolean

  // 创建场景实例
  def apply(_def: SceneDef): Scene
}