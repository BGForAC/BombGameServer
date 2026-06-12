package com.example.holder

import com.example.commands.CmdType
import com.example.exception.ThrowBusinessException
import com.example.message.{Message, MessageBody}
import com.example.scene.BaseGameScene
import com.example.serer.PlayerChannels
import com.example.tick.ITick

import scala.collection.mutable
import scala.util.Random

/**
 * 游戏场景持有者类，用于管理和匹配玩家到不同的游戏场景
 * 该对象单例模式，负责管理所有游戏场景的匹配逻辑
 */
object BaseGameSceneHolder extends ITick {
  // key: mapId 0-随机 其他-固定地图
  // value: MatchQueue

  private val matchQueues: mutable.Map[Int, MatchQueue] = mutable.Map.empty // 存储地图ID与对应匹配队列的映射关系

  private val minRoomSize = 2 // 最小房间人数，即开始游戏的最低人数要求
  private val maxRoomSize = 4 // 最大房间人数，即每个房间的最大容量
  private val tickInterval = 50 // 定时器间隔，用于定期检查匹配情况
  private val mapCnt = 1 // 地图数量，表示可用的地图总数
  private val baseSceneId = 1 // 基础场景ID，作为随机地图的起始ID
  // 暂时不考虑玩家加入多个地图匹配队列的情况
  /**
   * 将玩家加入匹配队列
   * @param playerId 玩家ID
   * @param mapId 地图ID，0表示随机地图
   */
  def addToMatchQueue(playerId: String, mapId: Int): Unit = {
    if (mapId < 0 || mapId > mapCnt) {
      ThrowBusinessException(s"地图ID[$mapId]无效，加入匹配队列失败")
      return
    }
    val queue = matchQueues.getOrElseUpdate(mapId, new MatchQueue(mapId))
    queue.addToMatchQueue(playerId)
  }

  /**
   * 从匹配队列中移除玩家
   * @param playerId 玩家ID
   */
  def removeFromMatchQueue(playerId: String): Unit = {
    matchQueues.values.foreach(_.removeFromMatchQueue(playerId))
  }

  /**
   * 定时处理匹配逻辑
   * @param tickIdx 当前定时器索引
   */
  def tick(tickIdx: Long): Unit = {
    matchQueues.values.foreach(_.tick(tickIdx))
  }

  // 在 BaseGameSceneHolder 对象中添加
  private[holder] def clearMatchQueues(): Unit = {
    matchQueues.clear()
  }


  /**
   * 匹配队列内部类，处理特定地图的玩家匹配逻辑
   * @param mapId 地图ID
   */
  private class MatchQueue(mapId: Int) {
    private val matchQueue: mutable.ListBuffer[String] = mutable.ListBuffer.empty // 存储等待匹配的玩家ID列表
    private val playerSet: mutable.HashSet[String] = mutable.HashSet.empty // 存储等待匹配的玩家ID集合，用于快速查找

    // 添加同步锁对象
    private val lock = new Object()

    /**
     * 将玩家加入匹配队列
     * @param playerId 玩家ID
     */
    def addToMatchQueue(playerId: String): Unit = {
      lock.synchronized {
        if (!playerSet.contains(playerId)) {
          matchQueue += playerId
          playerSet.add(playerId)
          //println(s"玩家[$playerId]加入匹配队列")
        }
      }
    }

    /**
     * 从匹配队列中移除玩家
     * @param playerId 玩家ID
     */
    def removeFromMatchQueue(playerId: String): Unit = {
      lock.synchronized {
        if (playerSet.contains(playerId)) {
          matchQueue -= playerId
          playerSet.remove(playerId)
          //println(s"玩家[$playerId]离开匹配队列")
        }
      }
    }

    /**
     * 定时处理匹配逻辑
     * @param tickIdx 当前定时器索引
     */
    def tick(tickIdx: Long): Unit = {
      lock.synchronized {
        if (tickIdx % tickInterval == 0) {
          // 当队列中的玩家数量达到最小房间人数时，开始创建房间
          while (matchQueue.lengthCompare(minRoomSize) >= 0) {
          // 计算当前房间大小，取最小房间人数和剩余玩家数中的较小值
          val roomSize = Math.min(maxRoomSize, matchQueue.length)
          // 获取当前房间的玩家ID列表
          val playerIds = matchQueue.take(roomSize)
          // 获取玩家对象
          val players = playerIds.map(id => (id, PlayerHolder.getPlayer(id)))
          // 检查是否有离线玩家
          val offLinePlayers = players.filter(_._2 == null)
          if (offLinePlayers.nonEmpty) {
            // 移除离线玩家
            offLinePlayers.foreach { case (playerId, _) =>
              //println(s"玩家[$playerId]已离线，移出匹配队列")
              removeFromMatchQueue(playerId)
            }
          } else {
            // 从匹配队列中移除已匹配的玩家
            matchQueue.remove(0, roomSize)
            // 从playerSet中移除已匹配的玩家
            playerIds.foreach(playerSet.remove)
            // 确定实际使用的地图ID
            val actualMapId = if (mapId == 0) {
              // 如果mapId为0，则随机选择一个地图
              Random.nextInt(mapCnt) + baseSceneId
            } else mapId // 否则使用指定的地图ID
            // 创建游戏场景
            val scene = SceneHolder.createScene(actualMapId)
            // 标记为随机匹配模式
            scene.isRandomMatch = true
            // 将玩家加入场景
            players.foreach { case (_, player) =>
              SceneHolder.enterScene(scene.id, player)
            }

            // 获取场景中的玩家索引信息
            val playerIdxInfo = scene.asInstanceOf[BaseGameScene].playerIdxInfo

            // 验证玩家索引信息是否正确
            if (playerIdxInfo.size != players.size) {
              //println(s"玩家索引信息数量[${playerIdxInfo.size}]与玩家数量[${players.size}]不匹配")
              // 发送错误消息给所有玩家
              players.foreach { case (playerId, _) =>
                PlayerChannels.sendError(playerId, "匹配失败，有玩家没有成功进入场景")
              }
              return
            }

            // 构建玩家信息字符串
            val playersInfo = new MessageBody()
            playerIdxInfo.keys.foreach( playerId => {
              val player = PlayerHolder.getPlayer(playerId)
              playersInfo.put(playerId, player.baseInfoStr())
            })
            // 发送进入游戏场景的消息给所有玩家
            players.foreach { case (_, player) =>
              PlayerChannels.send(player.id, Message(CmdType.ENTER_BASE_GAME, MessageBody(
                "mapId" -> actualMapId, "playersInfo" -> playersInfo, "isRandomMatch" -> 1)))
            }

            // 打印匹配成功信息
            //println(s"匹配成功，玩家[${players.mkString(",")}]进入场景")
          }
        }
      }
      }
    }

  }
}
