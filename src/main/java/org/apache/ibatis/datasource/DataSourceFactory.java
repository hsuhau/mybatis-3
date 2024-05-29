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
package org.apache.ibatis.datasource;

import java.util.Properties;
import javax.sql.DataSource;

/**
 * 在数据持久层，数据源和事务是两个非常重要的组件，对数据持久层的影响很大，在实际开发中，一般会使用 Mybatis 集成第三方数据源组件，如：c3p0、Druid，另外，Mybatis 也提供了自己的数据库连接池实现，本文会通过 Mybatis 的源码实现来了解数据库连接池的设计。而事务方面，一般使用 Spring 进行事务的管理，这里不做详细分析。下面我们看一下 Mybatis 是如何对这两部分进行封装的。
 *
 * @author Clinton Begin
 */
public interface DataSourceFactory {

  // 设置 DataSource 的属性，一般紧跟在 DataSource 初始化之后
  void setProperties(Properties props);

  // 获取 DataSource对象
  DataSource getDataSource();

}
