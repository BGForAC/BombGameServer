package com.example.actor

import com.example.commands.CmdType
import com.example.holder.SceneHolder
import com.example.message.{Message, MessageBody}
import com.example.props.{PropsItem, PropsManager}
import com.example.serer.PlayerChannels

import scala.collection.mutable

/**
 * Bomb类表示游戏中的一个炸弹对象，继承自Actor类
 * @param owner 炸弹的所有者Actor
 * @param id 炸弹的唯一标识符
 */
class Bomb(owner: Actor, id: String) extends Actor(id) {
  // 炸弹爆炸的时间点，为当前时间加上所有者设置的引信时间
  private val explodeTime: Long = System.currentTimeMillis() + owner.attr.FuseTime

  // 防止重复爆炸（连锁引爆竞态条件）
  private var isExploded: Boolean = false

  /**
   * 每个游戏tick调用的方法，用于更新炸弹状态
   * @param tickIdx 当前tick的索引
   */
  override def tick(tickIdx: Long): Unit = {
    if (isExploded) return  // 已爆炸，跳过

    val now = System.currentTimeMillis()
    val remaining = explodeTime - now

    // 每60 tick输出一次调试信息，确认炸弹tick被正常调用
    if (tickIdx % 60 == 0 || remaining <= 0) {
      println(s"[Bomb.tick] tick#$tickIdx 炸弹[$id] sceneId=[${movement.sceneId}] owner=[${owner.id}] remaining=${remaining}ms explodeTime=$explodeTime now=$now")
    }

    // 检查是否到达爆炸时间
    if (now >= explodeTime) {
      println(s"[Bomb.tick] tick#$tickIdx 炸弹[$id]到达爆炸时间 (延迟${-remaining}ms), 执行explode()")
      explode() // 执行爆炸（explode 内部会调用 exitScene 移除自身）
    }
  }

  /**
   * 炸弹爆炸的方法（与客户端 Bomb.Explode 对称）
   * 1. 四方向扫描（遇到墙停止），收集玩家、炸弹、可破坏方块
   * 2. 对范围内的玩家造成伤害
   * 3. 摧毁可破坏方块并生成道具，更新服务端地图
   * 4. 触发其他炸弹连锁爆炸
   * 5. 广播 BOMB_EXPLODE 通知客户端播放视觉效果
   * 6. 从场景中移除此炸弹（无论通过 tick 还是连锁引爆触发）
   */
  def explode(): Unit = {
    if (isExploded) return
    isExploded = true

    println(s"[Bomb.explode] ===== 炸弹[$id]开始爆炸, owner=[${owner.id}] =====")

    // 获取炸弹所在的场景
    val scene = SceneHolder.getScene(movement.sceneId)
    if (scene == null) throw new IllegalStateException(s"炸弹[$id]所在的场景[${movement.sceneId}]不存在")

    // 获取炸弹位置信息
    val moveInfo = this.movement.info
    val bombX = moveInfo.getInt("x")
    val bombZ = moveInfo.getInt("z")

    val gridSize = 100       // 1 世界单位 = 100 服务端单位
    val offsetDistance = 15  // 与客户端 MapInfo.GetVirtualCoord 对齐
    val centerGridX = Math.floor(bombX.toDouble / gridSize).toInt + offsetDistance
    val centerGridZ = Math.floor(bombZ.toDouble / gridSize).toInt + offsetDistance
    val radius = owner.attr.BombRadius.toInt

    // 收集所有受影响的实体（使用 Set 去重）
    val affectedPlayers = mutable.LinkedHashSet[Player]()
    val affectedBombs = mutable.LinkedHashSet[Bomb]()
    val affectedObstacles = mutable.LinkedHashSet[(Int, Int)]()  // (gridX, gridZ)

    // 扫描中心点
    scanCell(scene, centerGridX, centerGridZ, gridSize, offsetDistance, affectedPlayers, affectedBombs, affectedObstacles)

    // 四个方向扫描
    val directions = List((0, 1), (0, -1), (1, 0), (-1, 0))  // 前、后、右、左
    for ((dx, dz) <- directions) {
      scanDirection(scene, centerGridX, centerGridZ, dx, dz, radius, gridSize, offsetDistance,
        affectedPlayers, affectedBombs, affectedObstacles)
    }

    // ===== 1. 对范围内的玩家造成伤害 =====
    affectedPlayers.foreach { player =>
      player.hpChange(this, owner.attr.BombDamage)
    }

    // ===== 2. 摧毁可破坏方块并生成道具 =====
    affectedObstacles.foreach { case (gx, gz) =>
      scene.map.destroyObstacleAt(gx, gz) match {
        case Some((worldX, worldY, worldZ)) =>
          PropsManager.randomPick() match {
            case Some(propsConfig) =>
              val propsItem = PropsItem(propsConfig, movement.sceneId)
              propsItem.spawnAt(worldX, worldY, worldZ)
              SceneHolder.enterScene(movement.sceneId, propsItem)

              scene.players.values.foreach { p =>
                PlayerChannels.send(p.id, Message(CmdType.PROP_SPAWN, propsItem.toSpawnMessage))
              }
              println(s"[Bomb] 在($worldX, $worldY, $worldZ)生成了道具[${propsConfig.id}]")
            case None => // 不生成道具
          }
        case None =>
      }
    }

    // ===== 3. 触发其他炸弹连锁爆炸 =====
    affectedBombs.foreach { bomb =>
      if (bomb.id != this.id && !bomb.isExploded) {
        println(s"[Bomb] 炸弹[$id]触发炸弹[${bomb.id}]连锁爆炸")
        bomb.explode()  // 递归连锁引爆
      }
    }

    // ===== 4. 广播炸弹爆炸事件 =====
    val playerCount = scene.players.size
    println(s"[Bomb.explode] 向场景内${playerCount}名玩家广播BOMB_EXPLODE: bombId=[$id], pos=($bombX,$bombZ), grid=($centerGridX,$centerGridZ), radius=$radius, 摧毁障碍物=${affectedObstacles.size}个")

    // 序列化障碍物网格列表（嵌套 JSON: {"0":{"x":18,"y":18},"1":{"x":19,"y":19}}）
    // 使用嵌套 MessageBody 避免字符串内分隔符与 JSON 解析器冲突
    val obstaclesBody = new MessageBody()
    var obsIdx = 0
    affectedObstacles.foreach { case (gx, gz) =>
      obstaclesBody.put(obsIdx.toString, MessageBody("x" -> gx.toString, "y" -> gz.toString))
      obsIdx += 1
    }

    scene.players.values.foreach { p =>
      PlayerChannels.send(p.id, Message(CmdType.BOMB_EXPLODE, MessageBody(
        "bombId" -> id,
        "ownerId" -> owner.id,
        "x" -> bombX,
        "y" -> moveInfo.getInt("y"),
        "z" -> bombZ,
        "gridX" -> centerGridX,
        "gridZ" -> centerGridZ,
        "radius" -> owner.attr.BombRadius,
        "obstacles" -> obstaclesBody
      )))
    }

    val chainCount = affectedBombs.size - 1
    println(s"[Bomb.explode] ===== 炸弹[$id]爆炸完成: 伤害${affectedPlayers.size}名玩家, 摧毁${affectedObstacles.size}个方块, 连锁引爆${chainCount}个炸弹 =====")

    // ===== 5. 从场景中移除此炸弹（无论通过 tick 还是连锁引爆触发，都必须移除） =====
    // 修复：原来 exitScene 只在 tick() 中调用，连锁引爆的炸弹永远不会从 actors 中移除
    val sceneId = this.movement.sceneId
    if (sceneId != null) {
      println(s"[Bomb.explode] 炸弹[$id]开始从场景[$sceneId]移除")
      SceneHolder.exitScene(sceneId, this)
      println(s"[Bomb.explode] 炸弹[$id]已从场景移除")
    } else {
      println(s"[Bomb.explode] 警告: 炸弹[$id] sceneId 为 null，无法从场景移除")
    }
  }

  /**
   * 沿单个方向扫描，直到 radius 步或遇到墙
   */
  private def scanDirection(
    scene: com.example.scene.Scene,
    startGX: Int, startGZ: Int,
    dx: Int, dz: Int,
    radius: Int, gridSize: Int, offsetDistance: Int,
    players: mutable.LinkedHashSet[Player],
    bombs: mutable.LinkedHashSet[Bomb],
    obstacles: mutable.LinkedHashSet[(Int, Int)]
  ): Unit = {
    var gx = startGX
    var gz = startGZ
    for (_ <- 1 to radius) {
      gx += dx
      gz += dz

      // 遇到墙则停止该方向传播
      if (scene.map.isWall(gx, gz)) return

      scanCell(scene, gx, gz, gridSize, offsetDistance, players, bombs, obstacles)
    }
  }

  /**
   * 扫描单个格子：收集该格的玩家、炸弹、可破坏方块
   */
  private def scanCell(
    scene: com.example.scene.Scene,
    gridX: Int, gridZ: Int, gridSize: Int, offsetDistance: Int,
    players: mutable.LinkedHashSet[Player],
    bombs: mutable.LinkedHashSet[Bomb],
    obstacles: mutable.LinkedHashSet[(Int, Int)]
  ): Unit = {
    // 收集该格子上的玩家
    scene.players.values.foreach { player =>
      val pX = Math.floor(player.movement.info.getInt("x").toDouble / gridSize).toInt + offsetDistance
      val pZ = Math.floor(player.movement.info.getInt("z").toDouble / gridSize).toInt + offsetDistance
      if (pX == gridX && pZ == gridZ) {
        players += player
      }
    }

    // 收集该格子上的炸弹
    scene.getBombsAtGrid(gridX, gridZ, gridSize, offsetDistance).foreach { bomb =>
      bombs += bomb
    }

    // 收集该格子上的可破坏方块
    if (scene.map.hasObstacleAt(gridX, gridZ)) {
      obstacles += ((gridX, gridZ))
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
