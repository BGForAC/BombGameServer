package com.example.holder

import com.example.actor.Player

import scala.collection.mutable

/**
 * PlayerHolder 对象用于管理所有在线玩家的信息
 * 它提供了添加、移除、查询玩家以及检查玩家是否在线的功能
 * 使用了可变的Map来存储玩家信息，键为玩家ID，值为Player对象
 */
object PlayerHolder {
  // 使用可变Map来存储玩家信息，键为玩家ID(String类型)，值为Player对象
  private val players: mutable.Map[String, Player] = mutable.Map()

  /**
   * 添加玩家到PlayerHolder中
   * @param player 要添加的Player对象
   */
  def addPlayer(player: Player): Unit = {
    players += (player.id -> player)
  }

  /**
   * 根据玩家ID从PlayerHolder中移除玩家
   * @param playerId 要移除的玩家ID
   */
  def removePlayer(playerId: String): Unit = {
    players -= playerId
  }

  /**
   * 根据玩家ID获取Player对象
   * @param playerId 要查询的玩家ID
   * @return 返回对应的Player对象，如果不存在则返回null
   */
  def getPlayer(playerId: String): Player = {
    players.getOrElse(playerId, null)
  }

  /**
   * 检查指定ID的玩家是否在线
   * @param playerId 要检查的玩家ID
   * @return 如果玩家在线返回true，否则返回false
   */
  def isOnline(playerId: String): Boolean = {
    players.keySet.contains(playerId)
  }
}
