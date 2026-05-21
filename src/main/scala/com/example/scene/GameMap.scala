package com.example.scene

import com.example.message.MessageBody
import scala.collection.mutable

/**
 * GameMap类表示游戏地图，负责管理地图节点和判断位置是否可通行
 *
 * 核心语义（与客户端 MapInfo 对齐）：
 *   - JSON 中存在节点 → 该位置没有墙，可放置炸弹/行走
 *   - JSON 中不存在节点 → 该位置是墙（图构建时已排除）
 *   - 节点有 Destructible tag → 该位置有可破坏障碍物
 *   - 节点无 Destructible tag → 该位置可行走
 *
 * @param scene 场景对象，包含地图定义信息
 */
class GameMap(scene: Scene) {
  // 使用 Map 存储地图节点，key 为 (gridX, gridY)，O(1) 查找
  private val grids: mutable.Map[(Int, Int), MapNode] = mutable.Map()

  // 初始化地图
  init()

  /**
   * 初始化方法，从场景定义的 mapInfo（已解析的 JSON）中加载地图节点
   * mapInfo 由 SceneConfig.loadConfigs() 通过 initMap() 提前加载
   *
   * JSON 中只包含"没有墙"的节点，墙体位置不会出现在 mapData 中。
   * tags 字段记录该格子上当前存在的物体（Destructible 等）。
   */
  private def init(): Unit = {
    val mapInfo = scene._def.mapInfo
    if (mapInfo == null) {
      println("[GameMap] mapInfo 为空，地图无网格数据（SceneConfig 可能加载失败）")
      return
    }

    mapInfo.get("mapData") match {
      case Some(mapData: MessageBody) =>
        mapData.values.foreach {
          case nodeData: MessageBody =>
            val x = nodeData.getString("x").toInt
            val y = nodeData.getString("y").toInt
            val node = new MapNode(x, y)

            // 解析 tags 字段：判断该节点是否存在可破坏障碍物（Destructible）
            val tags = nodeData.get("tags")
            tags match {
              case Some(tagsBody: MessageBody) if tagsBody.nonEmpty =>
                val hasDestructible = tagsBody.values.exists {
                  case tagBody: MessageBody =>
                    tagBody.getString("type") == "Destructible"
                  case _ => false
                }
                if (hasDestructible) {
                  node.isObstacle = true
                }
              case _ => // 无 tags 或空 tags → 纯可行走节点
            }

            grids((x, y)) = node
          case _ =>
        }

        val obstacleCount = grids.values.count(_.isObstacle)
        val walkableCount = grids.size - obstacleCount
        println(s"[GameMap] 从 mapInfo 加载了 ${grids.size} 个地图节点（可行走=${walkableCount}, 可破坏障碍物=${obstacleCount}）")
        println(s"[GameMap] 语义说明: 不在 grids 中的位置均为墙壁（JSON未记录=墙）")
      case _ =>
        println("[GameMap] mapInfo 中缺少 mapData 字段")
    }
  }

  /**
   * 判断指定位置是否可通行
   * 服务端坐标单位为世界坐标×100，需转换为网格坐标（1-28）后比对
   *
   * 判定规则：节点存在于 grids 中 且 节点不是障碍物 → 可通行
   *
   * @param x 服务端X坐标（世界坐标×100）
   * @param y 服务端Y坐标（高度，walkable 不校验）
   * @param z 服务端Z坐标（世界坐标×100）
   * @return 如果位置可通行返回true，否则返回false
   */
  def walkable(x: Int, y: Int, z: Int): Boolean = {
    val gridSize = 100       // 1 世界单位 = 100 服务端单位
    val offsetDistance = 15  // 与客户端 MapInfo.GetVirtualCoord 的 offsetDistance 对齐
    val gridX = Math.floor(x.toDouble / gridSize).toInt + offsetDistance
    val gridY = Math.floor(z.toDouble / gridSize).toInt + offsetDistance
    grids.get((gridX, gridY)) match {
      case Some(node) => !node.isObstacle  // 节点存在且不是障碍物 → 可行走
      case None => false                    // 节点不存在 → 墙（不可通行）
    }
  }

  /**
   * 获取爆炸范围内的所有可破坏方块（obstacle）
   * @param centerX 爆炸中心X坐标
   * @param centerZ 爆炸中心Z坐标
   * @param radius 爆炸半径（单位：格数）
   * @return 范围内所有可破坏方块的列表
   */
  def getDestructibleNodesInRange(centerX: Int, centerZ: Int, radius: Float): List[MapNode] = {
    val gridSize = 100
    val offsetDistance = 15
    val centerGridX = Math.floor(centerX.toDouble / gridSize).toInt + offsetDistance
    val centerGridZ = Math.floor(centerZ.toDouble / gridSize).toInt + offsetDistance
    val radiusGrids = Math.ceil(radius / gridSize).toInt

    grids.values.filter { node =>
      node.isObstacle &&
        Math.abs(node.x - centerGridX) <= radiusGrids &&
        Math.abs(node.y - centerGridZ) <= radiusGrids
    }.toList
  }

  /**
   * 将指定位置的可破坏方块标记为已摧毁（变为可通行）
   * @param gridX 网格X坐标（对应 3D X）
   * @param gridZ 网格Y坐标（对应 3D Z，参数名 gridZ 为历史遗留）
   * @return 实际世界坐标 (x, y, z)，用于生成道具；如果未找到则返回None
   */
  def destroyObstacleAt(gridX: Int, gridZ: Int): Option[(Int, Int, Int)] = {
    grids.get((gridX, gridZ)) match {
      case Some(node) if node.isObstacle =>
        node.isObstacle = false
        // 返回世界坐标：格子坐标 * 250 为世界坐标（客户端 /100 后使用）
        Some((gridX * 250, 0, gridZ * 250))
      case _ => None
    }
  }

  /**
   * 检查指定格子坐标是否为墙（不可破坏的障碍物），用于爆炸方向扫描时停止传播
   *
   * 判定规则：网格坐标超出范围(1-28) 或 不存在于 grids 中 → 墙
   * JSON 构建图时已排除墙体节点，所以不在 grids 中的位置就是墙。
   *
   * @param gridX 网格X坐标（对应 3D X）
   * @param gridZ 网格Y坐标（对应 3D Z，参数名 gridZ 为历史遗留）
   */
  def isWall(gridX: Int, gridZ: Int): Boolean = {
    // 超出地图边界(1-28) → 墙
    if (gridX < 1 || gridX > 28 || gridZ < 1 || gridZ > 28) return true
    // 不在 grids 中 → 墙（JSON 构建图时已排除墙体）
    !grids.contains((gridX, gridZ))
  }

  /**
   * 检查指定格子坐标是否存在可破坏的障碍物
   * @param gridX 网格X坐标（对应 3D X）
   * @param gridZ 网格Y坐标（对应 3D Z，参数名 gridZ 为历史遗留）
   */
  def hasObstacleAt(gridX: Int, gridZ: Int): Boolean = {
    grids.get((gridX, gridZ)) match {
      case Some(node) => node.isObstacle
      case None => false
    }
  }

  /**
   * 地图节点类
   * 只记录"无墙"的格子，墙体由"grids中不存在"隐式表达
   */
  class MapNode {
    var x: Int = 0   // 网格列（地图 JSON x）
    var y: Int = 0   // 网格行（地图 JSON y）

    /** 该格子上是否存在可破坏的障碍物（Destructible） */
    var isObstacle: Boolean = false

    def this(x: Int, y: Int) = {
      this()
      this.x = x
      this.y = y
    }
  }
}
