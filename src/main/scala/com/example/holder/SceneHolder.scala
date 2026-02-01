package com.example.holder

import com.example.actor.Actor
import com.example.config.SceneConfig
import com.example.reflect.Scanner
import com.example.scene.{Scene, SceneFacade}

import scala.collection.mutable

object SceneHolder extends Scanner[Int, SceneFacade] {
  private val scenes: mutable.Map[String, Scene] = mutable.Map()

  override val packageName: String = "com.example.scene"

  def tick(tickIdx: Long): Unit = {
    scenes.values.foreach(scene => scene.tick(tickIdx))
  }

  def addScene(scene: Scene): Unit = {
    scenes += (scene.id -> scene)
  }

  def getScene(sceneId: String): Scene = {
    scenes.getOrElse(sceneId, null)
  }

  private def getSceneFacade(defId: Int): SceneFacade = {
    val sceneDef = SceneConfig.getDef(defId)
    getSceneFacadeByType(sceneDef.typ)
  }

  private def getSceneFacadeByType(sceneType: Int): SceneFacade = {
    map.getOrElse(sceneType, throw new RuntimeException(s"Scene facade not found for type: $sceneType"))
  }

  def checkEnterScene(defId: Int, actor: Actor): Boolean = {
    val sceneFacade = getSceneFacade(defId)
    sceneFacade.checkEnterScene(actor)
  }

  def enterScene(defId: Int, actor: Actor): Unit = {
    val lastScene = scenes.getOrElse(actor.movement.lastSceneId, null)
    if (lastScene != null) {
      if (lastScene.players.contains(actor.id)) {
        lastScene.onExit(actor)
      }
    }
    val sceneFacade = getSceneFacade(defId)
    val scene = sceneFacade.apply(SceneConfig.getDef(defId))
    scene.onEnter(actor)
  }
}