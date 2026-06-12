package com.example.serer

import com.example.message.Message
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

/**
 * Sharable注解表示该处理器可以被多个Channel安全地共享
 * 这是一个自定义的消息编码器，将消息转换为字节流
 */
@Sharable
object MessageEncoder extends MessageToByteEncoder[AnyRef] {
  /**
   * 将消息编码为字节流并写入ByteBuf
   * @param ctx ChannelHandlerContext，包含Channel通道的相关信息
   * @param msg 需要编码的消息对象
   * @param out 输出的ByteBuf，用于写入编码后的数据
   */
  override protected def encode(ctx: ChannelHandlerContext, msg: AnyRef, out: ByteBuf): Unit = {
    // 使用模式匹配处理不同类型的消息
    msg match {
      case message: Message =>
        // 将消息转换为ByteBuf
        val buffer = message.toByteBuf
        // 先写入消息长度，再写入消息内容
        out.writeInt(buffer.capacity())
        out.writeBytes(buffer)
      // 预编码的 ByteBuf：已经包含 [长度前缀 + cmdType + body]，直接透传
      case buf: ByteBuf =>
        out.writeBytes(buf)
      // 其他不支持的类型抛出异常
      case _ => throw new IllegalArgumentException("Unsupported message type")
    }
  }
}