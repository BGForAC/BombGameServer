package com.example.utils

import java.io.InputStream

object StreamUtils {
  def inputStream2ByteArray(is: InputStream): Array[Byte] = {
    val buffer = new Array[Byte](is.available())
    is.read(buffer)
    buffer
  }
}
