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

abstract class Scene(sceneId: String, sceneDef: SceneDef) {
//  private val map = new GameMap(this)

  SceneHolder.addScene(this)

  private val actors: mutable.Map[String, Actor] = mutable.Map()

  val players: mutable.Map[String, Player] = mutable.Map()

  def id: String = sceneId

  def _def: SceneDef = sceneDef

  def tick(tickIdx: Long): Unit = {
    actors.values.foreach(actor => actor.tick(tickIdx))
  }

  def onEnter(actor: Actor): Unit = {
    actors += (actor.id -> actor)
    actor.setToScene(this)
    players.values.foreach{ player =>
      PlayerChannels.send(player.id, Message(CmdType.ENTER_SCENE, MessageBody((Seq("pid" -> actor.id) ++ actor.movement.info): _*)))
    }
    actor match {
      case player: Player =>
        players += (player.id -> player)
      case _ =>
    }
  }

  def checkEnterScene(actor: Actor): Boolean = {
    actor match {
      case _: Player =>
        if (players.size >= _def.maxPlayerCnt) {
          ThrowBusinessException("场景人数已满")
        } else {
          true
        }
      case _ =>
        true
    }
  }

  def onExit(actor: Actor): Unit = {
    actors -= actor.id
    players.values.foreach{ player =>
      PlayerChannels.send(player.id, new Message(CmdType.EXIT_SCENE, MessageBody("aid" -> actor.id)))
    }
    actor match {
      case player: Player =>
        players -= player.id
      case _ =>
    }
    actor.setOutScene(this)
  }

  def walkable(x: Int, y: Int, z: Int): Boolean = {
    true
//    map.walkable(x, y, z)
  }
}

trait SceneFacade extends ScanAble[Int] {
  private var uniqueKey = 0

  def genSceneId(_def: SceneDef): String = {
    uniqueKey += 1
    s"${_def.id}_$uniqueKey"
  }

  def checkEnterScene(actor: Actor): Boolean

  def apply(_def: SceneDef): Scene
}