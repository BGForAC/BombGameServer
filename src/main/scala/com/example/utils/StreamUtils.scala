package com.example.utils

import java.io.InputStream

/**
 * StreamUtils 工具类，提供流相关的实用方法
 */
object StreamUtils {
  /**
   * 将输入流转换为字节数组
   * @param is 输入流(InputStream)
   * @return 返回包含输入流所有数据的字节数组(Array[Byte])
   */
  def inputStream2ByteArray(is: InputStream): Array[Byte] = {
    // 创建一个与输入流可用大小相同的字节数组缓冲区
    val buffer = new Array[Byte](is.available())
    // 从输入流中读取数据到缓冲区
    is.read(buffer)
    // 返回填充了数据的缓冲区
    buffer
  }
}
