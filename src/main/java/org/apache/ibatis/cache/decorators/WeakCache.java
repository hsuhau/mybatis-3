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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

// WeakCache 的实现与 SoftCache 基本类似，唯一的区别在于其中使用 WeakEntry（继承了 WeakReference）封装真正的 value 对象，其他实现完全一样。
//
//另外，还有 ScheduledCache、LoggingCache、SynchronizedCache、SerializedCache 等。ScheduledCache 是周期性清理缓存的装饰器，它的 clearInterval 字段 记录了两次缓存清理之间的时间间隔，默认是一小时，lastClear 字段 记录了最近一次清理的时间戳。 ScheduledCache 的 getObject()、putObject()、removeObject() 等核心方法，在执行时都会根据这两个字段检测是否需要进行清理操作，清理操作会清空缓存中所有缓存项。
//
//LoggingCache 在 Cache 的基础上提供了日志功能，它通过 hit 字段 和 request 字段 记录了 Cache 的命中次数和访问次数。在 LoggingCache.getObject()方法 中，会统计命中次数和访问次数 这两个指标，井按照指定的日志输出方式输出命中率。
//
//SynchronizedCache 通过在每个方法上添加 synchronized 关键字，为 Cache 添加了同步功能，有点类似于 JDK 中 Collections 的 SynchronizedCollection 内部类。
//
//SerializedCache 提供了将 value 对象 序列化的功能。SerializedCache 在添加缓存项时，会将 value 对应的 Java 对象 进行序列化，井将序列化后的 byte[]数组 作为 value 存入缓存 。 SerializedCache 在获取缓存项时，会将缓存项中的 byte[]数组 反序列化成 Java 对象。不使用 SerializedCache 装饰器 进行装饰的话，每次从缓存中获取同一 key 对应的对象时，得到的都是同一对象，任意一个线程修改该对象都会影响到其他线程，以及缓存中的对象。而使用 SerializedCache 每次从缓存中获取数据时，都会通过反序列化得到一个全新的对象。 SerializedCache 使用的序列化方式是 Java 原生序列化。

/**
 * Weak Reference cache decorator.
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 * 
 * @author Clinton Begin
 */
public class WeakCache implements Cache {
  private final Deque<Object> hardLinksToAvoidGarbageCollection;
  private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
  private final Cache delegate;
  private int numberOfHardLinks;

  public WeakCache(Cache delegate) {
    this.delegate = delegate;
    this.numberOfHardLinks = 256;
    this.hardLinksToAvoidGarbageCollection = new LinkedList<Object>();
    this.queueOfGarbageCollectedEntries = new ReferenceQueue<Object>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    removeGarbageCollectedItems();
    return delegate.getSize();
  }

  public void setSize(int size) {
    this.numberOfHardLinks = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    removeGarbageCollectedItems();
    delegate.putObject(key, new WeakEntry(key, value, queueOfGarbageCollectedEntries));
  }

  @Override
  public Object getObject(Object key) {
    Object result = null;
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
    WeakReference<Object> weakReference = (WeakReference<Object>) delegate.getObject(key);
    if (weakReference != null) {
      result = weakReference.get();
      if (result == null) {
        delegate.removeObject(key);
      } else {
        hardLinksToAvoidGarbageCollection.addFirst(result);
        if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
          hardLinksToAvoidGarbageCollection.removeLast();
        }
      }
    }
    return result;
  }

  @Override
  public Object removeObject(Object key) {
    removeGarbageCollectedItems();
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    hardLinksToAvoidGarbageCollection.clear();
    removeGarbageCollectedItems();
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private void removeGarbageCollectedItems() {
    WeakEntry sv;
    while ((sv = (WeakEntry) queueOfGarbageCollectedEntries.poll()) != null) {
      delegate.removeObject(sv.key);
    }
  }

  private static class WeakEntry extends WeakReference<Object> {
    private final Object key;

    private WeakEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      super(value, garbageCollectionQueue);
      this.key = key;
    }
  }

}
