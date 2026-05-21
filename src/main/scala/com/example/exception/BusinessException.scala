package com.example.exception

import com.example.message.{ErrorMessage, Message}

import scala.util.control.NoStackTrace

/**
 * 业务异常抛出对象，提供便捷的异常创建和抛出方法
 * 使用apply方法可以直接抛出BusinessException异常
 */
object ThrowBusinessException {
  /**
   * 创建并抛出业务异常
   * @param message 异常信息
   * @param exceptionType 异常类型，默认为ExceptionType.DEFAULT
   * @return Nothing 表示此方法永不正常返回，总是抛出异常
   */
  def apply(message: String, exceptionType: Int = ExceptionType.DEFAULT): Nothing = {
    throw new BusinessException(message, exceptionType)
  }
}

/**
 * 业务异常类，继承自RuntimeException，并且实现了NoStackTrace接口
 * 这种异常不会记录堆栈信息，适用于业务逻辑异常
 * @param message 异常信息
 * @param exceptionType 异常类型，默认为ExceptionType.DEFAULT
 */
class BusinessException(message: String, exceptionType: Int = ExceptionType.DEFAULT) extends RuntimeException with NoStackTrace {
  /**
   * 将异常转换为消息对象
   * @return 返回包含异常信息和异常类型的ErrorMessage
   */
  def toMessage: Message = {
    ErrorMessage(message, exceptionType)
  }
}
