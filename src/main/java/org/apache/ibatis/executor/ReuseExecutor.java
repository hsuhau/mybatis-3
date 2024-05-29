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
package org.apache.ibatis.executor;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

// 在传统的 JDBC 编程中，复用 Statement 对象是常用的一种优化手段，该优化手段可以减少 SQL 预编译的开销以及创建和销毁 Statement 对象的开销，从而提高性能（Reuse，复用）。
//
//ReuseExecutor 提供了 Statement 复用的功能，ReuseExecutor 中通过 statementMap 字段缓存使用过的 Statement 对象，key 是 SQL 语句，value 是 SQL 对应的 Statement 对象。
//
//ReuseExecutor.doQuery()、doQueryCursor()、doUpdate() 方法的实现与 SimpleExecutor 中对应方法的实现一样，区别在于其中调用的 prepareStatement() 方法，SimpleExecutor 每次都会通过 JDBC 的 Connection 对象创建新的 Statement 对象，而 ReuseExecutor 则会先尝试重用 StaternentMap 中缓存的 Statement 对象。

/**
 * @author Clinton Begin
 */
public class ReuseExecutor extends BaseExecutor {

  // 本map用于缓存使用过的Statement，以提升本框架的性能
  // key SQL语句，value 该SQL语句对应的Statement
  private final Map<String, Statement> statementMap = new HashMap<String, Statement>();

  public ReuseExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    return handler.update(stmt);
  }

  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    return handler.<E>query(stmt, resultHandler);
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    return handler.<E>queryCursor(stmt);
  }

  /**
   * 当事务提交或回滚、连接关闭时，都需要关闭这些缓存的Statement对象。前面分析的BaseExecutor的
   * commit()、rollback()和close()方法中都会调用doFlushStatements()方法，
   * 所以在该方法中关闭Statement对象的逻辑非常合适
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    // 遍历Statement对象集合，并依次关闭
    for (Statement stmt : statementMap.values()) {
      closeStatement(stmt);
    }
    // 清除对Statement对象的缓存
    statementMap.clear();
    // 返回一个空集合
    return Collections.emptyList();
  }

  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    BoundSql boundSql = handler.getBoundSql();
    // 获取要执行的sql语句
    String sql = boundSql.getSql();
    // 如果之前执行过该sql，则从缓存中取出对应的Statement对象
    // 不再创建新的Statement，减少系统开销
    if (hasStatementFor(sql)) {
      stmt = getStatement(sql);
      // 修改超时时间
      applyTransactionTimeout(stmt);
    } else {
      // 获取数据库连接
      Connection connection = getConnection(statementLog);
      // 从连接中获取Statement对象
      stmt = handler.prepare(connection, transaction.getTimeout());
      // 将sql语句 和 其对应的Statement对象缓存起来
      putStatement(sql, stmt);
    }
    // 处理占位符
    handler.parameterize(stmt);
    return stmt;
  }

  private boolean hasStatementFor(String sql) {
    try {
      return statementMap.keySet().contains(sql) && !statementMap.get(sql).getConnection().isClosed();
    } catch (SQLException e) {
      return false;
    }
  }

  private Statement getStatement(String s) {
    return statementMap.get(s);
  }

  private void putStatement(String sql, Statement stmt) {
    statementMap.put(sql, stmt);
  }

}
