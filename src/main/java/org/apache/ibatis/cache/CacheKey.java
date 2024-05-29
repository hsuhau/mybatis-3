/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.reflection.ArrayUtil;

// 在 Cache 中唯一确定一个缓存项，需要使用缓存项的 key 进行比较，MyBatis 中因为涉及 动态 SQL 等多方面因素， 其缓存项的 key 不能仅仅通过一个 String 表示，所以 MyBatis 提供了 CacheKey 类 来表示缓存项的 key，在一个 CacheKey 对象 中可以封装多个影响缓存项的因素。 CacheKey 中可以添加多个对象，由这些对象共同确定两个 CacheKey 对象 是否相同。

/**
 * @author Clinton Begin
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;

  public static final CacheKey NULL_CACHE_KEY = new NullCacheKey();

  // 参与计算hashcode，默认值DEFAULT_MULTIPLYER = 37
  private static final int DEFAULT_MULTIPLYER = 37;

  // 当前CacheKey对象的hashcode，默认值DEFAULT_HASHCODE = 17
  private static final int DEFAULT_HASHCODE = 17;

  // 参与计算hashcode，默认值DEFAULT_MULTIPLYER = 37
  private final int multiplier;

  // 当前CacheKey对象的hashcode，默认值DEFAULT_HASHCODE = 17
  private int hashcode;

  // 校验和
  private long checksum;

  private int count;

  // 8/21/2017 - Sonarlint flags this as needing to be marked transient.  While true if content is not serializable, this is not always true and thus should not be marked transient.

  // 由该集合中的所有元素 共同决定两个CacheKey对象是否相同，一般会使用一下四个元素
  // MappedStatement的id、查询结果集的范围参数（RowBounds的offset和limit）
  // SQL语句（其中可能包含占位符"?"）、SQL语句中占位符的实际参数
  private List<Object> updateList;

  // 构造方法初始化属性
  public CacheKey() {
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLYER;
    this.count = 0;
    this.updateList = new ArrayList<Object>();
  }

  public CacheKey(Object[] objects) {
    this();
    updateAll(objects);
  }

  public int getUpdateCount() {
    return updateList.size();
  }

  public void update(Object object) {
    int baseHashCode = object == null ? 1 : ArrayUtil.hashCode(object);

    // 重新计算count、checksum和hashcode的值
    count++;
    checksum += baseHashCode;
    baseHashCode *= count;

    hashcode = multiplier * hashcode + baseHashCode;

    // 将object添加到updateList集合
    updateList.add(object);
  }

  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  /**
   * CacheKey重写了 equals() 和 hashCode()方法，这两个方法使用上面介绍
   * 的 count、checksum、hashcode、updateList 比较两个 CacheKey对象 是否相同
   */
  @Override
  public boolean equals(Object object) {
    // 如果为同一对象，直接返回 true
    if (this == object) {
      return true;
    }

    // 如果 object 都不是 CacheKey类型，直接返回 false
    if (!(object instanceof CacheKey)) {
      return false;
    }

    // 类型转换一下
    final CacheKey cacheKey = (CacheKey) object;

    // 依次比较 hashcode、checksum、count，如果不等，直接返回 false
    if (hashcode != cacheKey.hashcode) {
      return false;
    }
    if (checksum != cacheKey.checksum) {
      return false;
    }
    if (count != cacheKey.count) {
      return false;
    }

    // 比较 updateList 中的元素是否相同，不同直接返回 false
    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (!ArrayUtil.equals(thisObject, thatObject)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  @Override
  public String toString() {
    StringBuilder returnValue = new StringBuilder().append(hashcode).append(':').append(checksum);
    for (Object object : updateList) {
      returnValue.append(':').append(ArrayUtil.toString(object));
    }
    return returnValue.toString();
  }

  @Override
  public CacheKey clone() throws CloneNotSupportedException {
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    clonedCacheKey.updateList = new ArrayList<Object>(updateList);
    return clonedCacheKey;
  }

}
