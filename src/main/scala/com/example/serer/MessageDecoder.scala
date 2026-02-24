package com.example.serer

import com.example.message.Message
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.LengthFieldBasedFrameDecoder

class MessageDecoder extends LengthFieldBasedFrameDecoder(Message.MAX_LENGTH, 0, 4, 0, 4) {
  override def decode(ctx: ChannelHandlerContext, in: ByteBuf): Message = {
    try {
      super.decode(ctx, in) match {
        case byteBuf: ByteBuf =>
          val message = Message.fromByteBuf(byteBuf)
          message
        case _ => null
      }
    } catch {
      case _ => null
    }
  }
}