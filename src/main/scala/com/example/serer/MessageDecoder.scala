package com.example.serer  // 包声明，表明该文件属于com.example.serer包

import com.example.message.Message  // 导入Message类，用于消息处理
import io.netty.buffer.ByteBuf  // 导入Netty的ByteBuf类，用于处理字节缓冲区
import io.netty.channel.ChannelHandlerContext  // 导入ChannelHandlerContext类，用于处理器上下文
import io.netty.handler.codec.LengthFieldBasedFrameDecoder  // 导入基于长度字段帧解码器

/**
 * MessageDecoder类，继承自LengthFieldBasedFrameDecoder
 * 用于解码网络消息，确保消息的完整性和正确性
 * @param MAX_LENGTH 最大消息长度，防止超大消息攻击
 * @param 0 长度字段偏移量
 * @param 4 长度字段长度
 * @param 0 长度字段调整值
 * @param 4 初始要跳过的字节数
 */
class MessageDecoder extends LengthFieldBasedFrameDecoder(Message.MAX_LENGTH, 0, 4, 0, 4) {
  /**
   * 重写decode方法，用于解码接收到的数据
   * @param ctx ChannelHandlerContext处理器上下文
   * @param in 输入的ByteBuf字节缓冲区
   * @return 解码后的Message对象，解码失败返回null
   */
  override def decode(ctx: ChannelHandlerContext, in: ByteBuf): Message = {
    try {
      // 调用父类的decode方法进行基础解码
      super.decode(ctx, in) match {
        case byteBuf: ByteBuf =>  // 如果解码结果是ByteBuf类型
          // 将ByteBuf转换为Message对象
          val message = Message.fromByteBuf(byteBuf)
          message
        case _ => null  // 其他情况返回null
      }
    } catch {
      case _ => null  // 捕获所有异常并返回null
    }
  }
}