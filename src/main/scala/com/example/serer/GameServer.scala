package com.example.serer

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelHandler, ChannelInitializer}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}

object GameServer {
  def run(): Unit = {
    clientSocket()
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
