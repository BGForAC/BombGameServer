package com.example.service

import com.example.actor.Player
import com.example.exception.ThrowBusinessException

import scala.collection.mutable
import scala.util.Random

object PlayerService {
  private val playerMap: mutable.Map[String, Player] = mutable.Map.empty

  def getPlayer(username: String, password: String): Player = {
    val player = new Player(generatePlayerId())
    player.uname = username
//    if (playerMap.values.exists(p => p.uname == username)) {
//      ThrowBusinessException("用户名已存在")
//    }
    if (playerMap.keySet.contains(player.id)) {
      ThrowBusinessException("玩家ID已存在")
    }
    playerMap += (player.id -> player)
    player
  }

  private def generatePlayerId(): String = {
    (Random.nextInt(9000000) + 1000000).toString
  }
}
