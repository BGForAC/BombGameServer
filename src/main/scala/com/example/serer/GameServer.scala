package com.example.serer

import com.example.config.IConfig
import com.example.holder.SceneHolder
import com.example.utils.ClassUtil
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelHandler, ChannelInitializer}

/**
 * 游戏服务器主对象
 * 负责初始化配置、场景和启动网络服务
 */
object GameServer {
  /**
   * 启动游戏服务器的主方法
   * 依次执行加载配置、初始化场景和启动客户端连接服务
   */
  def run(): Unit = {
    loadConfigs()  // 加载所有配置
    initHolder()   // 初始化场景持有者
    clientSocket() // 启动客户端连接服务
  }

  /**
   * 加载所有配置类
   * 扫描指定包下的所有类，如果是IConfig的实现类则调用其loadConfigs方法
   */
  private def loadConfigs(): Unit = {
    val classes = ClassUtil.getClassesInPackage("com.example.config")
    classes.foreach { cls =>
      try {
        val obj = cls.getDeclaredField("MODULE$").get(null)
        obj match {
          case config: IConfig => config.loadConfigs()  // 调用配置加载方法
          case _ =>  // 非配置类则忽略
        }
      } catch {
        case _: Exception =>  // 忽略所有异常
      }
    }
  }

  /**
   * 初始化场景持有者
   * 扫描并初始化所有场景数据
   */
  private def initHolder(): Unit = {
    SceneHolder.scan()  // 扫描场景数据
  }

  /**
   * 初始化客户端连接处理
   * 设置消息编解码器和处理器，并绑定到指定端口
   */
  private def clientSocket(): Unit = {
    // 创建客户端连接处理器
    val childHandler: ChannelHandler = new ChannelInitializer[SocketChannel] {
      override def initChannel(ch: SocketChannel): Unit = {
        // 添加消息编码器、解码器和主处理器到管道
        ch.pipeline().addLast(MessageEncoder, new MessageDecoder, MasterHandler)
      }
    }
    // 绑定服务器端口
    serverSocketBind(ServerConfig.clientPort, childHandler)
  }

  /**
   * 绑定服务器socket
   * @param port 服务器监听端口
   * @param childHandler 客户端连接处理器
   */
  private def serverSocketBind(port: Int, childHandler: ChannelHandler): Unit = {
    // 创建服务器引导对象
    val bootstrap = new ServerBootstrap
    // 设置使用NioServerSocketChannel
    bootstrap.channel(classOf[NioServerSocketChannel])
    // 设置线程组，分别用于处理accept和read/write事件
    bootstrap.group(new NioEventLoopGroup(ServerConfig.bossThreadNum),
      new NioEventLoopGroup(ServerConfig.workerThreadNum))
    // 设置子处理器，用于处理客户端连接
    bootstrap.childHandler(childHandler)
    // 绑定端口并同步等待
    bootstrap.bind(ServerConfig.clientHost,port).sync()
    // 打印服务器启动信息
    println(s"游戏服务器在 ${ServerConfig.clientHost}:$port 上启动成功")
  }
}
