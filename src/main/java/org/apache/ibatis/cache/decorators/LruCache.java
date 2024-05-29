/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.cache.decorators;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (least recently used) cache decorator
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  // 被装饰者
  private final Cache delegate;

  // 这里使用的是 LinkedHashMap，它继承了 HashMap，但它的元素是有序的
  private Map<Object, Object> keyMap;

  // 最近最少被使用的缓存项的 key
  private Object eldestKey;

  // 国际惯例，构造方法中进行属性初始化
  public LruCache(Cache delegate) {
    this.delegate = delegate;
    // 这里初始化了 keyMap，并定义了 eldestKey 的取值规则
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(final int size) {
    // 初始化 keyMap，同时指定该 Map 的初始容积及加载因子，第三个参数true 表示 该LinkedHashMap
    // 记录的顺序是 accessOrder，即，LinkedHashMap.get()方法 会改变其中元素的顺序
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;

      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) {
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }


  // 存储缓存项
  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    // 记录缓存项的 key，超出容量则清除最久未使用的缓存项
    cycleKeyList(key);
  }

  @Override
  public Object getObject(Object key) {
    // 访问 key元素 会改变该元素在 LinkedHashMap 中的顺序
    keyMap.get(key); //touch
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private void cycleKeyList(Object key) {
    // eldestKey 不为空，则表示已经达到缓存上限
    keyMap.put(key, key);
    if (eldestKey != null) {
      // 清除最久未使用的缓存
      delegate.removeObject(eldestKey);
      // 制空
      eldestKey = null;
    }
  }

}
