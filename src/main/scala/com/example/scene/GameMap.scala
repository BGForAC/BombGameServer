package com.example.scene

import com.example.message.MessageBody
import scala.collection.mutable

/**
 * GameMap类表示游戏地图，负责管理地图节点和判断位置是否可通行
 * @param scene 场景对象，包含地图定义信息
 */
class GameMap(scene: Scene) {
  // 使用可变的ListBuffer存储地图节点
  private val grids: mutable.ListBuffer[MapNode] = mutable.ListBuffer()

  // 初始化地图
  init()
  /**
   * 初始化方法，从场景定义的 mapInfo（已解析的 JSON）中加载地图节点
   * mapInfo 由 SceneConfig.loadConfigs() 通过 initMap() 提前加载
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
            // z 用于高度判断，地面层设为 0
            val node = new MapNode(x, y, 0)
            node.nodeType(0) // 默认可通行；障碍物/墙体由场景预制体决定
            grids += node
          case _ =>
        }
        println(s"[GameMap] 从 mapInfo 加载了 ${grids.size} 个地图节点")
      case _ =>
        println("[GameMap] mapInfo 中缺少 mapData 字段")
    }
  }

  /**
   * 判断指定位置是否可通行
   * 服务端坐标单位为世界坐标×100，需转换为网格坐标（1-28）后比对
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
    // node.x = 网格列（地图 JSON x），node.y = 网格行（地图 JSON y），node.z = 0（高度层）
    grids.exists(node => node.x == gridX && node.y == gridY && node.isWalkable)
  }

  /**
   * 获取爆炸范围内的所有可破坏方块（obstacle）
   * @param centerX 爆炸中心X坐标
   * @param centerZ 爆炸中心Z坐标
   * @param radius 爆炸半径（单位：格数）
   * @return 范围内所有可破坏方块的列表
   */
  def getDestructibleNodesInRange(centerX: Int, centerZ: Int, radius: Float): List[MapNode] = {
    // 服务端坐标单位为世界坐标×100，网格每格100单位；需转换为网格坐标（1-28）
    val gridSize = 100
    val offsetDistance = 15  // 与客户端 MapInfo.GetVirtualCoord 对齐
    val centerGridX = Math.floor(centerX.toDouble / gridSize).toInt + offsetDistance
    val centerGridZ = Math.floor(centerZ.toDouble / gridSize).toInt + offsetDistance
    // radius 是服务端坐标距离（×100），转换为网格步数
    val radiusGrids = Math.ceil(radius / gridSize).toInt

    grids.filter { node =>
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
    grids.find(node => node.x == gridX && node.y == gridZ && node.isObstacle) match {
      case Some(node) =>
        node.isWalkable = true
        node.isObstacle = false
        // 返回世界坐标：x=3D X, y=0(地面高度), z=3D Z
        // 格子坐标 * 250 为世界坐标（客户端 /100 后使用）
        Some((gridX * 250, 0, gridZ * 250))
      case None => None
    }
  }

  /**
   * 检查指定格子坐标是否为墙（不可破坏的障碍物），用于爆炸方向扫描时停止传播
   * @param gridX 网格X坐标（对应 3D X）
   * @param gridZ 网格Y坐标（对应 3D Z，参数名 gridZ 为历史遗留）
   */
  def isWall(gridX: Int, gridZ: Int): Boolean = {
    grids.exists(node => node.x == gridX && node.y == gridZ && node.isWall)
  }

  /**
   * 检查指定格子坐标是否存在可破坏的障碍物
   * @param gridX 网格X坐标（对应 3D X）
   * @param gridZ 网格Y坐标（对应 3D Z，参数名 gridZ 为历史遗留）
   */
  def hasObstacleAt(gridX: Int, gridZ: Int): Boolean = {
    grids.exists(node => node.x == gridX && node.y == gridZ && node.isObstacle)
  }

  class MapNode {
    var x: Int = 0
    var y: Int = 0
    var z: Int = 0

    var isWalkable: Boolean = true
    var isObstacle: Boolean = false
    var isWall: Boolean = false

    def this(x: Int, y: Int, z: Int) = {
      this()
      this.x = x
      this.y = y
      this.z = z
    }

    def nodeType(nodeType: Int): Unit = {
      nodeType match {
        case 0 =>
        case 1 =>
          isWalkable = false
          isObstacle = true
        case 2 =>
          isWalkable = false
          isWall = true
        case _ =>
      }
    }
  }
}
