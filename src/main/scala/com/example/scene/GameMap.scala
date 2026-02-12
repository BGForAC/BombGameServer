package com.example.scene

import scala.collection.mutable

class GameMap(scene: Scene) {
  private val grids: mutable.ListBuffer[MapNode] = mutable.ListBuffer()

  init()
  private def init(): Unit = {
    val mapId = scene._def.mapId
    val fileName = s"maps/map_$mapId.txt"
    val source = scala.io.Source.fromFile(fileName)
    for (line <- source.getLines()) {
      val parts = line.split(",")
      if (parts.length >= 5) {
        val x = parts(0).toInt
        val y = parts(1).toInt
        val z = parts(2).toInt
        val nodeType = parts(3).toInt
        val node = new MapNode(x, y, z)
        node.nodeType(nodeType)
        grids += node
      }
    }
  }

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
