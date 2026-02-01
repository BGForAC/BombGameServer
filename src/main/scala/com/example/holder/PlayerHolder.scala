package com.example.holder

import com.example.actor.Player

import scala.collection.mutable

object PlayerHolder {
  private val players: mutable.Map[String, Player] = mutable.Map()

  def addPlayer(player: Player): Unit = {
    players += (player.id -> player)
  }

  def getPlayer(playerId: String): Player = {
    players.getOrElse(playerId, null)
  }
}
