package com.example.holder

import com.example.commands.CmdType
import com.example.exception.ThrowBusinessException
import com.example.message.{Message, MessageBody}
import com.example.scene.BaseGameScene
import com.example.serer.PlayerChannels
import com.example.service.PlayerService

import scala.collection.mutable
import scala.util.Random

/**
 * 游戏房间持有者类，负责管理所有游戏房间的创建、销毁和状态更新
 * 提供房间的增删改查、成员管理、状态同步等功能
 */
object BaseGameRoomHolder {
  // 玩家ID到房间的映射
  private val playerId2Room: mutable.Map[String, Room] = mutable.Map.empty
  // 房间ID到房间的映射
  private val rooms: mutable.Map[Int, Room] = mutable.Map.empty
  // 下次更新时间
  private var nextUpdateTime = 0L

  /**
   * 定时更新房间状态
   * @param tickIdx 当前tick索引
   */
  def tick(tickIdx: Long): Unit = {
    // 打印tick日志，方便排查定时任务卡顿或频率问题
    // println(s"[Tick] 当前Tick索引: $tickIdx, 当前房间数量: ${rooms.size}")

    rooms.values.foreach(_.tick(tickIdx)) // 遍历所有房间，执行各自的tick方法
    // 定期向所有玩家发送房间信息
    if (nextUpdateTime < System.currentTimeMillis()) {
      nextUpdateTime = System.currentTimeMillis() + 5000 // 设置下次更新时间为当前时间+5秒
      println(s"[Tick] 广播房间信息给所有玩家，当前房间总数: ${rooms.size}")
      PlayerChannels.sendToAll(roomMessage) // 向所有玩家广播房间信息
    }
  }

  /**
   * 创建新房间
   * @param playerId 创建者ID
   * @param roomName 房间名称
   */
  def createRoom(playerId: String, roomName: String): Unit = {
    println(s"[CreateRoom] 玩家 $playerId 尝试创建房间: $roomName")
    // 检查房间名称是否合法
    if (Seq('-', '=', ':', '%', ';', '|').exists(c => roomName.contains(c))) {
      println(s"[CreateRoom] 失败: 房间名包含非法字符")
      ThrowBusinessException("房间名不合法，创建失败")
    }
    val room = Room(playerId, roomName) // 创建新房间
    rooms += room.id -> room // 将房间添加到房间映射中
    joinRoom(playerId, room.id) // 创建者自动加入房间
    println(s"[CreateRoom] 成功: 房间ID ${room.id} 已创建")
  }

  /**
   * 请求移除房间
   * @param playerId 请求者ID
   */
  def reqRemoveRoom(playerId: String): Unit = {
    println(s"[RemoveRoom] 玩家 $playerId 请求移除房间")
    playerId2Room.get(playerId) match {
      case Some(room) => {
        // 只有房主才能移除房间
        if (room.leaderId != playerId) {
          println(s"[RemoveRoom] 失败: 玩家 $playerId 不是房主 (当前房主: ${room.leaderId})")
          ThrowBusinessException("只有房主才能移除房间")
        }
        removeRoom(room.id) // 执行移除房间操作
      }
      case None => {
        println(s"[RemoveRoom] 失败: 玩家 $playerId 不在任何房间中")
        ThrowBusinessException("玩家不在任何房间中")
      }
    }
  }

  /**
   * 移除房间
   * @param roomId 要移除的房间ID
   */
  private def removeRoom(roomId: Int): Unit = {
    println(s"[RemoveRoom] 正在移除房间 ID: $roomId")
    rooms.get(roomId) match {
      case Some(room) => room.beforeRemove() // 移除前处理
      case None => println(s"[RemoveRoom] 警告: 尝试移除不存在的房间 ID: $roomId")
    }
    rooms -= roomId // 从房间映射中移除
    println(s"[RemoveRoom] 房间 ID: $roomId 已从管理列表中移除")
  }

  def startGame(playerId: String) : Unit = {
    playerId2Room.get(playerId) match {
      case Some(room) =>{
        if (room.leaderId != playerId) {
          println(s"[StartGame] 失败: 操作者 $playerId 不是房主 (当前房主: ${room.leaderId})")
          ThrowBusinessException("只有房主才能开始游戏")
        }
        room.startGame()
        println(s"[StartGame] 成功: 房间 ${room.id} 开始游戏")
      }
      case None => {
        println(s"[StartGame] 失败: 目标玩家 $playerId 不在房间中")
        ThrowBusinessException("目标不在任何房间中")
      }
    }
  }

  def changeMap(playerId: String, mapIndex: Int): Unit = {
    playerId2Room.get(playerId) match {
      case Some(room) => {
        // 只有房主才能更换房主
        if (playerId != room.leaderId) {
          println(s"[ChangeMap] 失败: 操作者 $playerId 不是房主 (当前房主: ${room.leaderId})")
          ThrowBusinessException("只有房主才能更换房主")
        }
        room.changeMap(mapIndex) // 更换地图
        println(s"[ChangeMap] 地图更换成功！")
      }
      case None => {
        println(s"[ChangeLeader] 失败: 目标玩家 $playerId 不在房间中")
        ThrowBusinessException("目标不在任何房间中")
      }
    }
  }

  def sendRoomMessage(playerId: String, message: Message): Unit = {
    println(s"[SendRoomMessage] 玩家 $playerId 发送消息到房间")
    playerId2Room.get(playerId) match {
      case Some(room) => {
        val msg = message.getString("message")
        room.sendRoomMessage(playerId, new Message(CmdType.BASE_GAME_PLAYER_SEND_MESSAGE
          , MessageBody({
            "message" -> s"${PlayerService.getPlayerName(playerId)}说：$msg"
          })))
      }
      case None => ThrowBusinessException("玩家不在任何房间中")
    }
  }

  /**
   * 加入房间
   * @param playerId 玩家ID
   * @param roomId 房间ID
   */
  def joinRoom(playerId: String, roomId: Int): Unit = {
    println(s"[JoinRoom] 玩家 $playerId 尝试加入房间 ID: $roomId")
    rooms.get(roomId) match {
      case Some(room) => {
        room.addMember(playerId) // 添加成员到房间
        println(s"[JoinRoom] 成功: 玩家 $playerId 已加入房间 $roomId")
      }
      case None => {
        println(s"[JoinRoom] 失败: 房间 ID: $roomId 不存在")
        ThrowBusinessException("房间不存在")
      }
    }
  }

  /**
   * 离开房间
   * @param playerId 玩家ID
   */
  def leaveRoom(playerId: String): Unit = {
    println(s"[LeaveRoom] 玩家 $playerId 请求离开房间")
    playerId2Room.get(playerId) match {
      case Some(room) => room.removeMember(playerId) // 从房间中移除成员
      case None => {
        println(s"[LeaveRoom] 失败: 玩家 $playerId 不在房间中")
        ThrowBusinessException("你不在房间中")
      }
    }
  }

  /**
   * 踢出玩家
   * @param targetId 被踢玩家ID
   * @param sourceId 操作者ID
   */
  def kickPlayer(targetId: String, sourceId: String) = {
    println(s"[KickPlayer] 玩家 $sourceId 尝试踢出玩家 $targetId")
    playerId2Room.get(targetId) match {
      case Some(room) => {
        // 只有房主才能踢人
        if (room.leaderId != sourceId) {
          println(s"[KickPlayer] 失败: 操作者 $sourceId 不是房主 (当前房主: ${room.leaderId})")
          ThrowBusinessException("只有房主才能踢人")
        }
        room.removeMember(targetId, ExitTypeEnum.KICK) // 以踢出方式移除成员
        println(s"[KickPlayer] 成功: 玩家 $targetId 已被踢出")
      }
      case None => {
        println(s"[KickPlayer] 失败: 目标玩家 $targetId 不在房间中")
        ThrowBusinessException("房间不存在")
      }
    }
  }

  /**
   * 玩家准备状态切换
   * @param playerId 玩家ID
   */
  def ready(playerId: String): Unit = {
    println(s"[Ready] 玩家 $playerId 切换准备状态") // 可选：如果状态切换非常频繁，建议注释掉
    playerId2Room.get(playerId) match {
      case Some(room) => room.ready(playerId) // 切换玩家准备状态
      case None => ThrowBusinessException("玩家不在任何房间中")
    }
  }

  /**
   * 更改玩家职业
   * @param playerId 玩家ID
   * @param career 职业名称
   */
  def changeCareer(playerId: String, career: String): Unit = {
    println(s"[ChangeCareer] 玩家 $playerId 切换职业为 $career") // 可选
    playerId2Room.get(playerId) match {
      case Some(room) => room.changeCareer(playerId, career) // 更改玩家职业
      case None => ThrowBusinessException("玩家不在任何房间中")
    }
  }

  /**
   * 更换房主
   * @param sourceId 操作者ID
   * @param targetId 新房主ID
   */
  def changeLeader(sourceId: String, targetId: String): Unit = {
    println(s"[ChangeLeader] 玩家 $sourceId 尝试将房主移交给 $targetId")
    playerId2Room.get(targetId) match {
      case Some(room) => {
        // 只有房主才能更换房主
        if (sourceId != room.leaderId) {
          println(s"[ChangeLeader] 失败: 操作者 $sourceId 不是房主 (当前房主: ${room.leaderId})")
          ThrowBusinessException("只有房主才能更换房主")
        }
        room.changeLeader(targetId) // 更换房主
        println(s"[ChangeLeader] 成功: 房主已变更为 $targetId")
      }
      case None => {
        println(s"[ChangeLeader] 失败: 目标玩家 $targetId 不在房间中")
        ThrowBusinessException("目标不在任何房间中")
      }
    }
  }

  /**
   * 刷新房间信息
   * @param playerIds 要刷新信息的玩家ID列表
   */
  def refreshRoomInfo(playerIds: String*): Unit = {
    val message = roomMessage // 获取房间消息
    playerIds.foreach(pid => PlayerChannels.send(pid, message)) // 向指定玩家发送房间信息
    println(s"[Refresh] 向玩家 ${playerIds.mkString(",")} 刷新了房间信息") // 可选
  }

  /**
   * 获取房间消息
   * @return 房间信息消息
   */
  private def roomMessage: Message = {
    Message(CmdType.BASE_GAME_REQ_ROOM_INFO, info) // 创建房间信息消息
  }

  /**
   * 获取房间信息
   * @return 房间信息消息体
   */
  def info: MessageBody = {
    var x = MessageBody()
    var i = 0
    rooms.values.foreach(room => {
      x = MessageBody.addMessageBody(x , MessageBody(s"room${i}" -> room.infoStr))
      i = i + 1
    })
    if(x == MessageBody()){
      MessageBody("rooms" -> "")
    }else {
      MessageBody("rooms" -> x) // 组装所有房间的信息
    }
  }


  /**
   * 房间成员类
   * @param id 成员ID
   * @param room 所属房间
   */
  private class RoomMember(val id: String, room: Room) {
    var isReady = false // 准备状态
    val player = PlayerHolder.getPlayer(id) // 获取玩家对象
    player.career = "Balance" // 默认职业

    /**
     * 获取成员信息
     * @return 成员信息字符串
     */
    def info: MessageBody = {
      player.baseInfoStr(MessageBody("isReady" -> isReady, "id" -> id)) // 生成成员信息字符串
    }

    /**
     * 更改职业
     * @param career 新职业
     */
    def changeCareer(career: String): Unit = {
      player.career = career // 设置新职业
    }

    /**
     * 更改准备状态
     */
    def changeStatus(): Unit = {
      isReady = !isReady // 切换准备状态
    }
  }

  /**
   * RoomMember的工厂对象
   */
  private object RoomMember {
    def apply(playerId: String, room: Room) = {
      new RoomMember(playerId, room) // 创建房间成员实例
    }
  }



  /**
   * 房间类
   * @param id 房间ID
   * @param roomName 房间名称
   */
  private class Room(val id: Int, val roomName: String) {
    // 房间成员映射
    private val roomMember = mutable.Map.empty[String, RoomMember]

    // 房主ID
    var leaderId: String = _
    // 房主名称
    private var leaderName: String = _

    // 房间最大容量
    private val roomSize: Int = 4

    // 地图ID
    private var mapIndex = 0

    private var isStartGame = false


    /**
     * 设置房主
     * @param leaderId 新房主ID
     */
    private def setLeader(leaderId: String) = {
      this.leaderId = leaderId
      leaderName = PlayerHolder.getPlayer(leaderId).uname // 获取房主名称
    }

    /**
     * 定时更新房间状态
     * @param tickIdx 当前tick索引
     */
    def tick(tickIdx: Long): Unit = {
      // 检查并移除离线玩家
      roomMember.keys.foreach(playerId => {
        if (!PlayerHolder.isOnline(playerId)) {
          println(s"[RoomTick] 房间 $id: 检测到玩家 $playerId 离线，执行移除")
          removeMember(playerId) // 移除离线玩家
        }
      })
      // 如果房间为空则移除房间
      if (roomMember.size == 0) {
        // 注意：这里会递归调用 BaseGameRoomHolder.removeRoom，那里已经有日志了
        BaseGameRoomHolder.removeRoom(id) // 移除空房间
      }
    }

    /**
     * 移除房间前的处理
     */
    def beforeRemove(): Unit = {
      println(s"[RoomBeforeRemove] 房间 $id 即将解散，开始移除所有成员")
      val ids = mutable.ListBuffer.empty[String]
      roomMember.keys.foreach({ id =>
        ids += id
        removeMember(id, ExitTypeEnum.DISBAND) // 以解散方式移除所有成员
      })
      refreshRoomInfo(ids.toSeq: _*) // 刷新所有成员的房间信息
    }

    /**
     * 添加成员
     * @param playerId 玩家ID
     */
    def addMember(playerId: String): Unit = {
      // 检查房间是否已满
      if (roomMember.size >= roomSize) {
        println(s"[AddMember] 失败: 房间 $id 人数已满 (${roomMember.size}/$roomSize)")
        ThrowBusinessException("房间人数已满")
      }
      roomMember += (playerId -> RoomMember(playerId, this)) // 添加新成员
      playerId2Room += playerId -> this // 更新玩家到房间的映射
      // 通知新成员加入成功
      PlayerChannels.send(playerId, Message(CmdType.BASE_GAME_JOIN_ROOM, MessageBody("result" -> "success", "info" -> infoStr)))
      notifyRoomChange() // 通知房间成员变化
    }

    /**
     * 移除成员
     * @param playerId 玩家ID
     * @param exitType 退出类型
     */
    def removeMember(playerId: String, exitType: Int = ExitTypeEnum.LEAVE): Unit = {
      println(s"[RemoveMember] 房间 $id: 移除成员 $playerId, 原因: ${ExitTypeEnum.getExitTypeStr(exitType)}")
      roomMember -= playerId // 从房间中移除成员
      playerId2Room -= playerId // 从玩家到房间的映射中移除
      // 如果移除的是房主，需要选择新房主
      if (playerId == leaderId) {
        if(roomMember.size > 0){
          val nextLeader = roomMember.keys.toArray.apply(Random.nextInt(roomMember.size)) // 随机选择新房主
          println(s"[RemoveMember] 房主 $playerId 离开，随机指定新房主: $nextLeader")
          changeLeader(nextLeader) // 更换房主
        }else {
          // 房间为空，将在 tick 或这里触发 removeRoom，避免重复日志，这里不打印
          BaseGameRoomHolder.removeRoom(id) // 移除空房间
        }
      }
      // 为了性能不会在解散队伍移除成员的时候通知其他人队伍成员变化
      // 当不为解散队伍时
      if (!Seq(ExitTypeEnum.DISBAND).contains(exitType)) {
        notifyRoomChange() // 通知房间成员变化
        // 通知被踢出的成员新的房间信息，因为每次调用都会重新获取服务器上的所有房间的新信息，
        // 比较耗性能，所以解散时要用同一个数据进行刷新,避免重复获取
        BaseGameRoomHolder.refreshRoomInfo(playerId) // 刷新被移除成员的房间信息
      }
      PlayerChannels.send(playerId, Message(CmdType.BASE_GAME_LEAVE_ROOM, MessageBody("result" -> "success", "info" -> infoStr)))
    } // 通知成员离开

    def ready(playerId: String): Unit = {
      roomMember.get(playerId) match {
        case Some(member) => {
          member.changeStatus()
          notifyRoomChange() // 切换准备状态
        } // 通知房间状态变化
        case None => ThrowBusinessException("玩家不在队伍中")
      }
    }

    def changeCareer(playerId: String, career: String): Unit = {
      roomMember.get(playerId) match {
        case Some(member) => {
          member.changeCareer(career)
          notifyRoomChange() // 更改职业
        } // 通知房间状态变化
        case None => ThrowBusinessException("玩家不在队伍中")
      }
    }

    private def notifyRoomChange(): Unit = {
      //println(s"[NotifyChange] 房间 $id 状态变更，通知成员 info = ${info.toJsonString}") // 可选，日志量可能较大
      roomMember.keys.foreach(playerId => {
        PlayerChannels.send(playerId, Message(CmdType.BASE_GAME_CURRENT_ROOM_CHANGE,
          info))
      }) // 通知所有成员房间状态变化
    }

    def sendRoomMessage(playerId: String, message: Message): Unit = {
      roomMember.keys.foreach(playerId =>
        PlayerChannels.send(playerId, message))
    }



    private def info: MessageBody = {
      var memberInfos = MessageBody()
      var i = 0;
      roomMember.values.foreach( roomMember =>{
        memberInfos = MessageBody.addMessageBody(memberInfos ,MessageBody(s"member${i}" -> roomMember.info))
        i = i + 1
      })
      MessageBody("roomId" -> id, "roomName" -> roomName, "members" -> memberInfos, "leaderId" -> leaderId, // 组装所有成员信息
        "leaderName" -> leaderName, "memberCnt" -> roomMember.size, "mapIndex" -> mapIndex)
    } // 组装房间信息

    def infoStr: MessageBody = {
      info // 将房间信息转换为字符串格式
    }

    def changeLeader(targetId: String): Unit = {
      if (!roomMember.contains(targetId)) ThrowBusinessException("目标玩家不存在")
      setLeader(targetId)
      notifyRoomChange() // 设置新房主
      PlayerChannels.alert(targetId, s"你成为了队伍 ${roomName} 的队长") // 通知房间状态变化
    } // 通知新房主

    def changeMap(mapIndex: Int): Unit = {
      this.mapIndex = mapIndex
      notifyRoomChange() // 通知房间状态变化
    }

    def startGame(): Unit = {
      //验证玩家是否都准备
      roomMember.values.foreach( roomMember =>{
        if(!roomMember.isReady){
          PlayerChannels.alert(leaderId, "存在玩家未准备")
          ThrowBusinessException("玩家未准备")
        }
      })
      isStartGame = true

      // 获取房间中的所有玩家
      val playerIds = roomMember.keys.toArray
      val players = playerIds.map(id =>{
        val player = PlayerHolder.getPlayer(id)
        player.attr.initAttr(player.career)
        (id, PlayerHolder.getPlayer(id))
      })

      players.foreach( player => {

      })


      // 检查是否有离线玩家
      val offLinePlayers = players.filter(_._2 == null)
      if (offLinePlayers.nonEmpty) {
        // 移除离线玩家
        offLinePlayers.foreach { case (playerId, _) =>
          println(s"玩家[$playerId]已离线，移出房间")
          removeMember(playerId)
        }
        PlayerChannels.alert(leaderId, "存在离线玩家，无法开始游戏")
        ThrowBusinessException("存在离线玩家，无法开始游戏")
      }

      // 验证地图ID是否有效
      if (mapIndex < 0) {
        PlayerChannels.alert(leaderId, s"地图ID[$mapIndex]无效，无法开始游戏")
        ThrowBusinessException(s"地图ID[$mapIndex]无效，无法开始游戏")
      }

      // 确定实际使用的地图ID
      val actualMapId = if (mapIndex == 0) {
        // 如果mapIndex为0，则随机选择一个地图
        // 这里假设地图ID从1开始，最大值为mapCnt
        val mapCnt = 1 // 地图数量，表示可用的地图总数
        val baseSceneId = 1 // 基础场景ID，作为随机地图的起始ID
        Random.nextInt(mapCnt) + baseSceneId
      } else {
        mapIndex
      }

      // 创建游戏场景
      val scene = SceneHolder.createScene(actualMapId)

      // 将玩家加入场景
      players.foreach { case (_, player) =>
        SceneHolder.enterScene(scene.id, player)
      }

      // 获取场景中的玩家索引信息
      val playerIdxInfo = scene.asInstanceOf[BaseGameScene].playerIdxInfo

      // 验证玩家索引信息是否正确
      if (playerIdxInfo.size != players.length) {
        println(s"玩家索引信息数量[${playerIdxInfo.size}]与玩家数量[${players.length}]不匹配")
        // 发送错误消息给所有玩家
        players.foreach { case (playerId, _) =>
          PlayerChannels.sendError(playerId, "游戏启动失败，有玩家没有成功进入场景")
        }
        ThrowBusinessException("游戏启动失败，有玩家没有成功进入场景")
      }

      // 构建玩家信息字符串
      var playersInfo = MessageBody()
      playerIdxInfo.keys.foreach( playerId => {
        val player = PlayerHolder.getPlayer(playerId)
        playersInfo = MessageBody.addMessageBody(playersInfo, MessageBody(playerId -> player.baseInfoStr()))
      })

      // 发送进入游戏场景的消息给所有玩家
      players.foreach { case (_, player) =>
        PlayerChannels.send(player.id, Message(CmdType.ENTER_BASE_GAME, MessageBody(
          "mapId" -> actualMapId, "playersInfo" -> playersInfo)))
      }

      // 打印游戏启动成功信息
      println(s"游戏启动成功，房间[$id]中的玩家[${players.mkString(",")}]进入场景")
    }


  }

  private object Room extends AutoGrow {
    def apply(leaderId: String, roomName: String): Room = {
      val room = new Room(generateId(), roomName)
      room.setLeader(leaderId) // 创建新房间实例
      room // 设置房主
    }
  }
}
