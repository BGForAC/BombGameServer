package com.example.config

import com.example.scene.SceneType

object SceneConfig extends IConfig {
  private val sceneDefs: collection.mutable.Map[Int, SceneDef] = collection.mutable.Map()

  override def loadConfigs(): Unit = {
    val sceneDef = new SceneDef
    sceneDef.id = 1
    sceneDef.typ = SceneType.BASE_GAME
    sceneDef.name = s"基础游戏场景"
    sceneDef.maxPlayerCnt = 4
    sceneDef.spawnPoints = Array(
      (-1250, 40, -1250, 45),
      (1250, 40, -1250, 135),
      (1250, 40, 1250, 225),
      (-1250, 40, 1250, 315)
    )
    sceneDefs += (1 -> sceneDef)
  }

  def getDef(sceneId: String): SceneDef = {
    getDef(sceneId.toInt)
  }

  def getDef(sceneId: Int): SceneDef = {
    sceneDefs.getOrElse(sceneId, null)
  }
}

class SceneDef {
  var id: Int = _
  var typ: Int = _
  var name: String = _
  var spawnPoints: Array[(Int, Int, Int, Int)] = _
  var maxPlayerCnt: Int = _
  var mapId: String = _
}