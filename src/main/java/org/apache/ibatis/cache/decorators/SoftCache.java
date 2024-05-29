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
import java.lang.ref.SoftReference;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;


// 在分析 SoftCache 和 WeakCache 实现之前，我们再温习一下 Java 提供的 4 种引用类型，强引用 StrongReference、软引用 SoftReference、弱引用 WeakReference 和虚引用 PhantomReference。
//
//强引用
//平时用的最多的，如 Object obj ＝ new Object()，新建的 Object 对象 就是被强引用的。如果一个对象被强引用，即使是 JVM 内存空间不足，要抛出 OutOfMemoryError 异常，GC 也绝不会回收该对象。
//软引用
//仅次于强引用的一种引用，它使用类 SoftReference 来表示。当 JVM 内存不足时，GC 会回收那些只被软引用指向的对象，从而避免内存溢出。软引用适合引用那些可以通过其他方式恢复的对象，例如， 数据库缓存中的对象就可以从数据库中恢复，所以软引用可以用来实现缓存，下面要介绍的 SoftCache 就是通过软引用实现的。
//另外，由于在程序使用软引用之前的某个时刻，其所指向的对象可能己经被 GC 回收掉了，所以通过 Reference.get()方法 来获取软引用所指向的对象时，总是要通过检查该方法返回值是否为 null，来判断被软引用的对象是否还存活。
//弱引用
//弱引用使用 WeakReference 表示，它不会阻止所引用的对象被 GC 回收。在 JVM 进行垃圾回收时，如果指向一个对象的所有引用都是弱引用，那么该对象会被回收。
//所以，只被弱引用所指向的对象，其生存周期是 两次 GC 之间 的这段时间，而只被软引用所指向的对象可以经历多次 GC，直到出现内存紧张的情况才被回收。
//虚引用
//最弱的一种引用类型，由类 PhantomReference 表示。虚引用可以用来实现比较精细的内存使用控制，但很少使用。
//引用队列（ReferenceQueue )
//很多场景下，我们的程序需要在一个对象被 GC 时得到通知，引用队列就是用于收集这些信息的队列。在创建 SoftReference 对象 时，可以为其关联一个引用队列，当 SoftReference 所引用的对象被 GC 时， JVM 就会将该 SoftReference 对象 添加到与之关联的引用队列中。当需要检测这些通知信息时，就可以从引用队列中获取这些 SoftReference 对象。不仅是 SoftReference，弱引用和虚引用都可以关联相应的队列。
/**
 * Soft Reference cache decorator
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 *
 * @author Clinton Begin
 */
public class SoftCache implements Cache {
  // 这里使用了 LinkedList 作为容器，在 SoftCache 中，最近使用的一部分缓存项不会被 GC
  // 这是通过将其 value 添加到 hardLinksToAvoidGarbageCollection集合 实现的（即，有强引用指向其value）
  private final Deque<Object> hardLinksToAvoidGarbageCollection;
  // 引用队列，用于记录已经被 GC 的缓存项所对应的 SoftEntry对象
  private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
  // 持有的被装饰者
  private final Cache delegate;
  // 强连接的个数，默认为 256
  private int numberOfHardLinks;

  // 构造方法进行属性的初始化
  public SoftCache(Cache delegate) {
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
    // 清除已经被 GC 的缓存项
    removeGarbageCollectedItems();
    // 添加缓存
    delegate.putObject(key, new SoftEntry(key, value, queueOfGarbageCollectedEntries));
  }

  @Override
  public Object getObject(Object key) {
    Object result = null;
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
    // 用一个软引用指向 key 对应的缓存项
    SoftReference<Object> softReference = (SoftReference<Object>) delegate.getObject(key);
    // 检测缓存中是否有对应的缓存项
    if (softReference != null) {
      // 获取 softReference 引用的 value
      result = softReference.get();
      // 如果 softReference 引用的对象已经被 GC，则从缓存中清除对应的缓存项
      if (result == null) {
        delegate.removeObject(key);
      } else {
        // See #586 (and #335) modifications need more than a read lock
        // 将缓存项的 value 添加到 hardLinksToAvoidGarbageCollection集合 中保存
        synchronized (hardLinksToAvoidGarbageCollection) {
          hardLinksToAvoidGarbageCollection.addFirst(result);
          // 如果 hardLinksToAvoidGarbageCollection 的容积已经超过 numberOfHardLinks
          // 则将最老的缓存项从 hardLinksToAvoidGarbageCollection 中清除，FIFO
          if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
            hardLinksToAvoidGarbageCollection.removeLast();
          }
        }
      }
    }
    return result;
  }

  @Override
  public Object removeObject(Object key) {
    // 清除指定的缓存项之前，也会先清理被 GC 的缓存项
    removeGarbageCollectedItems();
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    // 清理强引用集合
    synchronized (hardLinksToAvoidGarbageCollection) {
      hardLinksToAvoidGarbageCollection.clear();
    }
    // 清理被 GC 的缓存项
    removeGarbageCollectedItems();
    // 清理最底层的缓存项
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private void removeGarbageCollectedItems() {
    SoftEntry sv;
    // 遍历 queueOfGarbageCollectedEntries集合，清除已经被 GC 的缓存项 value
    while ((sv = (SoftEntry) queueOfGarbageCollectedEntries.poll()) != null) {
      delegate.removeObject(sv.key);
    }
  }

  private static class SoftEntry extends SoftReference<Object> {
    private final Object key;

    SoftEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      // 指向 value 的引用是软引用，并且关联了 引用队列
      super(value, garbageCollectionQueue);
      // 强引用
      this.key = key;
    }
  }

}