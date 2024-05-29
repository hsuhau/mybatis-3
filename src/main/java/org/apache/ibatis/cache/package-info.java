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
