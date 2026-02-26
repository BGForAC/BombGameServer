package com.example.reflect

import com.example.utils.ClassUtil

import scala.collection.mutable

/**
 * Scanner是一个特质（trait），用于扫描指定包中的类并将它们存储在一个可变的映射中。
 * 它是一个泛型特质，接受两个类型参数：K（键类型）和T（值类型）。
 *
 * @tparam K 映射的键类型
 * @tparam T 映射的值类型
 */
trait Scanner[K, T] {
  // 声明一个可变的映射，用于存储扫描结果
  val map: mutable.Map[K, T] = mutable.Map()

  /**
   * 获取要扫描的包名
   * @return 包名字符串
   */
  def packageName: String

  /**
   * 扫描指定包中的类，并将符合条件的对象存储到map中
   * 该方法会查找包中所有带有MODULE$静态字段的类，并检查这些类的实例是否实现了ScanAble特质
   */
  def scan(): Unit = {
    // 获取指定包中的所有类
    val classes = ClassUtil.getClassesInPackage(packageName)
    // 遍历包中的所有类
    for (cls <- classes) {
      try {
        // 尝试获取类的MODULE$静态字段，这通常是Scala单例对象的引用
        val obj = cls.getDeclaredField("MODULE$").get(null)
        // 检查对象是否实现了ScanAble特质
        obj match {
          case s: ScanAble[K] =>
            // 如果实现了ScanAble，则获取其所有键，并将对象存储到map中
            s.keySet.foreach { key =>
              map += (key -> obj.asInstanceOf[T])
            }
          case _ =>

          // 如果没有实现ScanAble，则不做任何处理
        }
      } catch {
        // 如果在处理过程中发生任何异常，则捕获并忽略
        case _: Exception =>
      }
    }
  }
}
