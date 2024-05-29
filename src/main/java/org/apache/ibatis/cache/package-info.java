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
/**
 * Base package for caching stuff
 */
package org.apache.ibatis.cache;

/**
 * 至此 Mybatis 的基础支持层的主要模块就分析完了。本模块首先介绍了 MyBatis 对 Java 反射机制的封装；然后分析了类型转换 TypeHandler 组件，了解了 MyBatis 如何实现数据在 Java 类型 与 JDBC 类型 之间的转换。
 *
 * 之后分析了 MyBatis 提供的 DataSource 模块 的实现和原理，深入解析了 MyBatis 自带的连接池 PooledDataSource 的详细实现；后面紧接着介绍了 Transaction 模块 的功能。然后分析了 binding 模块 如何将 Mapper 接口 与映射配置信息相关联，以及其中的原理。最后介绍了 MyBatis 的缓存模块，分析了 Cache 接口 以及多个实现类的具体实现，它们是 Mybatis 中一级缓存和二级缓存的基础。
 */

/**
 * 常见的应用系统中，数据库是比较珍贵的资源，很容易成为整个系统的瓶颈。在设计和维护系统时，会进行多方面的权衡，并且利用多种优化手段，减少对数据库的直接访问。
 *
 * 使用缓存是一种比较有效的优化手段，使用缓存可以减少应用系统与数据库的网络交互、减少数据库访问次数、降低数据库的负担、降低重复创建和销毁对象等一系列开销，从而提高整个系统的性能。
 *
 * MyBatis 提供的缓存功能，分别为一级缓存和二级缓存。BaseExecutor 主要实现了一级缓存的相关内容。一级缓存是会话级缓存，在 MyBatis 中每创建一个 SqlSession 对象，就表示开启一次数据库会话。在一次会话中，应用程序可能会在短时间内 (一个事务内)，反复执行完全相同的查询语句，如果不对数据进行缓存，那么每一次查询都会执行一次数据库查询操作，而多次完全相同的、时间间隔较短的查询语句得到的结果集极有可能完全相同，这会造成数据库资源的浪费。
 *
 * 为了避免上述问题，MyBatis 会在 Executor 对象中建立一个简单的一级缓存，将每次查询的结果集缓存起来。在执行查询操作时，会先查询一级缓存，如果存在完全一样的查询情况，则直接从一级缓存中取出相应的结果对象并返回给用户，减少数据库访问次数，从而减小了数据库的压力。
 *
 * 一级缓存的生命周期与 SqlSession 相同，其实也就与 SqISession 中封装的 Executor 对象的生命周期相同。当调用 Executor 对象的 close() 方法时（断开连接），该 Executor 对象对应的一级缓存就会被废弃掉。一级缓存中对象的存活时间受很多方面的影响，例如，在调用 Executor 的 update() 方法时，也会先请空一级缓存。一级缓存默认是开启的，一般情况下，不需要用户进行特殊配置。
 */
