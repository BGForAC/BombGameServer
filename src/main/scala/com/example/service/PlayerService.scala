package com.example.service

import com.example.actor.Player
import com.example.exception.ThrowBusinessException

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
   * 获取玩家方法
   * @param username 用户名
   * @param password 密码
   * @return Player 玩家对象
   */
  def getPlayer(username: String, password: String): Player = {
    // 创建新玩家并生成唯一ID
    val player = new Player(generatePlayerId())
    // 设置玩家用户名
    player.uname = username
    // 以下是检查用户名是否存在的代码（已被注释）
//    if (playerMap.values.exists(p => p.uname == username)) {
//      ThrowBusinessException("用户名已存在")
//    }
    // 检查玩家ID是否已存在
    if (playerMap.keySet.contains(player.id)) {
      ThrowBusinessException("玩家ID已存在")
    }
    // 将新玩家添加到玩家Map中
    playerMap += (player.id -> player)
    // 返回创建的玩家对象
    player
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
