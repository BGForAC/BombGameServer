package com.example.reflect

/**
 * ScanAble是一个特质（trait），它定义了一个泛型类型参数T
 * 这个特质可能用于表示某种可扫描的集合或结构
 * @tparam T 泛型类型参数，表示集合中元素的类型
 */
trait ScanAble[T] {
  /**
   * 定义一个val类型的keySet属性
   * 该属性是一个Set集合，存储类型为T的元素
   * 初始化值为null，表示默认情况下没有键集合
   */
  val keySet: Set[T] = null
}
