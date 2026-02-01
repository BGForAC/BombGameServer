package com.example.serer

import com.example.config.IConfig
import com.example.holder.SceneHolder
import com.example.utils.ClassUtil
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelHandler, ChannelInitializer}
import io.netty.channel.socket.nio.NioServerSocketChannel

object GameServer {
  def run(): Unit = {
    loadConfigs()
    initHolder()
    clientSocket()
  }

  private def loadConfigs(): Unit = {
    val classes = ClassUtil.getClassesInPackage("com.example.config")
    classes.foreach { cls =>
      try {
        val obj = cls.getDeclaredField("MODULE$").get(null)
        obj match {
          case config: IConfig => config.loadConfigs()
          case _ =>
        }
      } catch {
        case _: Exception =>
      }
    }
  }

  private def initHolder(): Unit = {
    SceneHolder.scan()
  }

  private def clientSocket(): Unit = {
    val childHandler: ChannelHandler = new ChannelInitializer[SocketChannel] {
      override def initChannel(ch: SocketChannel): Unit = {
        ch.pipeline().addLast(MessageEncoder, new MessageDecoder, MasterHandler)
      }
    }
    serverSocketBind(ServerConfig.clientPort, childHandler)
  }

  private def serverSocketBind(port: Int, childHandler: ChannelHandler): Unit = {
    val bootstrap = new ServerBootstrap
    bootstrap.channel(classOf[NioServerSocketChannel])
    bootstrap.group(new NioEventLoopGroup(ServerConfig.bossThreadNum), new NioEventLoopGroup(ServerConfig.workerThreadNum))
    bootstrap.childHandler(childHandler)
    bootstrap.bind(port).sync()
    println(s"Game server started on port $port")
  }
}
