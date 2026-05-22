package com.example.service

import com.example.actor.Player
import com.example.exception.ThrowBusinessException
import com.example.serer.PlayerChannels

import scala.collection.mutable
import scala.util.Random

/**
 * PlayerService 玩家服务类
 * 提供玩家相关的业务功能，如创建玩家、获取玩家等
 */
object PlayerService {
  // 使用可变Map存储玩家信息，key为玩家ID，value为Player对象
  private val playerMap: mutable.Map[String, Player] = mutable.Map.empty

  /**
   * 获取或验证玩家
   * 先检查用户名是否存在：存在则验证密码，不存在则创建新玩家
   * @param username 用户名
   * @param password 密码
   * @return Player 玩家对象
   */
  def getPlayer(username: String, password: String): Player = {
    // 检查用户名是否已存在
    playerMap.values.find(p => p.uname == username) match {
      case Some(existingPlayer) =>
        // 用户名存在，验证密码
        if (existingPlayer.password != password) {
          ThrowBusinessException("密码错误")
        }
        // 玩家重新登录：关闭原通道，注销旧连接
        PlayerChannels.closeAndRemoveChannel(existingPlayer.id)
        existingPlayer
      case None =>
        // 用户名不存在，创建新玩家
        var pid = generatePlayerId()
        while (playerMap.keySet.contains(pid)) {
          pid = generatePlayerId()
        }
        val player = new Player(pid)
        player.uname = username
        player.password = password
        playerMap += (player.id -> player)
        player
    }
  }

  def getPlayerName(pid : String): String = {
    playerMap.get(pid) match {
      case Some(player) => player.uname
      case _ => "未知玩家"
    }
  }




  /**
   * 生成玩家ID的私有方法
   * @return String 生成的7位数字ID字符串
   */
  private def generatePlayerId(): String = {
    // 生成1000000到9999999之间的随机数作为玩家ID
    (Random.nextInt(9000000) + 1000000).toString
  }
}
