package com.example.scene

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
   * 初始化方法，从文件中读取地图数据并创建节点
   */
  private def init(): Unit = {
    // 从场景定义中获取地图ID
    val mapId = scene._def.mapId
    // 构建地图文件路径
    val fileName = s"maps/$mapId.txt"
    // 创建文件源
    val source = scala.io.Source.fromFile(fileName)
    // 逐行读取文件内容
    for (line <- source.getLines()) {
      // 分割每行的数据
      val parts = line.split(",")
      // 确保数据完整
      if (parts.length >= 5) {
        // 解析节点坐标
        val x = parts(0).toInt
        val y = parts(1).toInt
        val z = parts(2).toInt
        // 解析节点类型
        val nodeType = parts(3).toInt
        // 创建地图节点
        val node = new MapNode(x, y, z)
        // 设置节点类型
        node.nodeType(nodeType)
        // 将节点添加到地图中
        grids += node
      }
    }
  }

  /**
   * 判断指定位置是否可通行
   * @param x X坐标
   * @param y Y坐标
   * @param z Z坐标
   * @return 如果位置可通行返回true，否则返回false
   */
  def walkable(x: Int, y: Int, z: Int): Boolean = {
//    grids.exists(node => node.x == x && node.y == y && node.z == z && node.isWalkable)
    grids.exists(node => node.x == x && node.z == z && node.isWalkable)
  }

  private class MapNode {
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
