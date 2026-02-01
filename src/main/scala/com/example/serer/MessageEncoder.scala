package com.example.serer

import com.example.message.Message
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

@Sharable
object MessageEncoder extends MessageToByteEncoder[AnyRef] {
  override protected def encode(ctx: ChannelHandlerContext, msg: AnyRef, out: ByteBuf): Unit = {
    msg match {
      case message: Message =>
        val buffer = message.toByteBuf
        out.writeInt(buffer.capacity())
        out.writeBytes(buffer)
        println(s"Encoded message: $message Cmd=${message.getCommand.toHexString} Length=${buffer.capacity()}")
//      case bytes: Array[Byte] =>
      case _ => throw new IllegalArgumentException("Unsupported message type")
    }
  }
}