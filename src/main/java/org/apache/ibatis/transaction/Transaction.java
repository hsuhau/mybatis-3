/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wraps a database connection.
 * Handles the connection lifecycle that comprises: its creation, preparation, commit/rollback and close.
 *
 * 遵循 “接口-实现类” 的设计原则，Mybatis 也是先使用 Transaction 接口 对数据库事务做了抽象，而实现类则只提供了两个，即：JdbcTransaction 和 ManagedTransaction。这两种对象的获取，使用了两个对应的工厂类 JdbcTransactionFactory 和 ManagedTransactionFactory。
 * 不过一般我们并不会使用 Mybatis 管理事务，而是将 Mybatis 集成到 Spring，由 Spring 进行事务的管理。细节部分会在后面的文章中详细讲解。
 *
 * @author Clinton Begin
 */
public interface Transaction {

  /**
   * Retrieve inner database connection
   * 获取连接对象
   *
   * @return DataBase connection
   * @throws SQLException
   */
  Connection getConnection() throws SQLException;

  /**
   * Commit inner database connection.
   * 提交事务
   *
   * @throws SQLException
   */
  void commit() throws SQLException;

  /**
   * Rollback inner database connection.
   * 回滚事务
   *
   * @throws SQLException
   */
  void rollback() throws SQLException;

  /**
   * Close inner database connection.
   * 关闭数据库连接
   *
   * @throws SQLException
   */
  void close() throws SQLException;

  /**
   * Get transaction timeout if set
   * 获取配置的事务超时时间
   *
   * @throws SQLException
   */
  Integer getTimeout() throws SQLException;
  
}
