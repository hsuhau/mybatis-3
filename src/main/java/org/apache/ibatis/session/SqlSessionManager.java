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
package org.apache.ibatis.session;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * SqlSessionManager 同时实现了 SqlSession 接口和 SqlSessionFactory 接口，所以同时提供了 SqlSessionFactory 创建 SqlSession 对象，以及 SqlSession 操纵数据库的功能。
 *
 * SqlSessionManager 与 DefaultSqlSessionFactory 的主要不同点 SqlSessionManager 提供了两种模式，第一种模式与 DefaultSqlSessionFactory 的行为相同，同一线程每次通过 SqlSessionManager 对象访问数据库时，都会创建新的 SqlSession 对象完成数据库操作。第二种模式是 SqlSessionManager 通过 localSqlSession 这 ThreadLocal 变量，记录与当前线程绑定的 SqlSession 对象，供当前线程循环使用，从而避免在同一线程多次创建 SqlSession 对象带来的性能损失。
 *
 * SqlSessionManager 的构造方法是唯一且私有的，如果要创建 SqlSessionManager 对象，需要调用其 newInstance() 方法（但需要注意的是，这不是单例模式，因为每次调用 newInstance() 方法都返回了一个新的对象）。
 *
 * SqlSessionManager 的 openSession() 系列方法，都是通过直接调用其持有的
 * DefaultSqlSessionFactory 实例来实现的。
 *
 * @author Larry Meadors
 */
public class SqlSessionManager implements SqlSessionFactory, SqlSession {

  // 通过持有DefaultSqlSessionFactory对象 来产生SqlSession对象
  private final SqlSessionFactory sqlSessionFactory;

  // localSqlSession中记录的SqlSession对象的代理对象（JDK动态代理）
  // SqlSessionManager初始化时 生成本代理对象，可以看下 下面的构造函数
  private final SqlSession sqlSessionProxy;

  // 用于记录一个与当前线程绑定的SqlSession对象
  private final ThreadLocal<SqlSession> localSqlSession = new ThreadLocal<SqlSession>();

  // 私有的构造函数，也是SqlSessionManager唯一的构造函数
  private SqlSessionManager(SqlSessionFactory sqlSessionFactory) {
    // 传入的这个SqlSessionFactory对象 往往是DefaultSqlSessionFactory的实例
    this.sqlSessionFactory = sqlSessionFactory;
    // JDK动态代理生成代理对象，可以看得出，SqlSessionInterceptor一定实现了
    // InvocationHandler接口
    this.sqlSessionProxy = (SqlSession) Proxy.newProxyInstance(
        SqlSessionFactory.class.getClassLoader(),
        new Class[]{SqlSession.class},
        new SqlSessionInterceptor());
  }

  // 通过newInstance()方法创建SqlSessionManager对象，有多种重载，
  // 但最后都是new了一个DefaultSqlSessionFactory的实例
  public static SqlSessionManager newInstance(Reader reader) {
    return new SqlSessionManager(new SqlSessionFactoryBuilder().build(reader, null, null));
  }

  public static SqlSessionManager newInstance(Reader reader, String environment) {
    return new SqlSessionManager(new SqlSessionFactoryBuilder().build(reader, environment, null));
  }

  public static SqlSessionManager newInstance(Reader reader, Properties properties) {
    return new SqlSessionManager(new SqlSessionFactoryBuilder().build(reader, null, properties));
  }

  public static SqlSessionManager newInstance(InputStream inputStream) {
    return new SqlSessionManager(new SqlSessionFactoryBuilder().build(inputStream, null, null));
  }

  public static SqlSessionManager newInstance(InputStream inputStream, String environment) {
    return new SqlSessionManager(new SqlSessionFactoryBuilder().build(inputStream, environment, null));
  }

  public static SqlSessionManager newInstance(InputStream inputStream, Properties properties) {
    return new SqlSessionManager(new SqlSessionFactoryBuilder().build(inputStream, null, properties));
  }

  public static SqlSessionManager newInstance(SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionManager(sqlSessionFactory);
  }

  public void startManagedSession() {
    this.localSqlSession.set(openSession());
  }

  public void startManagedSession(boolean autoCommit) {
    this.localSqlSession.set(openSession(autoCommit));
  }

  public void startManagedSession(Connection connection) {
    this.localSqlSession.set(openSession(connection));
  }

  public void startManagedSession(TransactionIsolationLevel level) {
    this.localSqlSession.set(openSession(level));
  }

  public void startManagedSession(ExecutorType execType) {
    this.localSqlSession.set(openSession(execType));
  }

  public void startManagedSession(ExecutorType execType, boolean autoCommit) {
    this.localSqlSession.set(openSession(execType, autoCommit));
  }

  public void startManagedSession(ExecutorType execType, TransactionIsolationLevel level) {
    this.localSqlSession.set(openSession(execType, level));
  }

  public void startManagedSession(ExecutorType execType, Connection connection) {
    this.localSqlSession.set(openSession(execType, connection));
  }

  public boolean isManagedSessionStarted() {
    return this.localSqlSession.get() != null;
  }

  // openSession()系列方法都是通过当前SqlSessionManager对象持有的
  // DefaultSqlSessionFactory实例的openSession()实现的
  @Override
  public SqlSession openSession() {
    return sqlSessionFactory.openSession();
  }

  @Override
  public SqlSession openSession(boolean autoCommit) {
    return sqlSessionFactory.openSession(autoCommit);
  }

  @Override
  public SqlSession openSession(Connection connection) {
    return sqlSessionFactory.openSession(connection);
  }

  @Override
  public SqlSession openSession(TransactionIsolationLevel level) {
    return sqlSessionFactory.openSession(level);
  }

  @Override
  public SqlSession openSession(ExecutorType execType) {
    return sqlSessionFactory.openSession(execType);
  }

  @Override
  public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
    return sqlSessionFactory.openSession(execType, autoCommit);
  }

  @Override
  public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
    return sqlSessionFactory.openSession(execType, level);
  }

  @Override
  public SqlSession openSession(ExecutorType execType, Connection connection) {
    return sqlSessionFactory.openSession(execType, connection);
  }

  @Override
  public Configuration getConfiguration() {
    return sqlSessionFactory.getConfiguration();
  }

  @Override
  public <T> T selectOne(String statement) {
    return sqlSessionProxy.<T> selectOne(statement);
  }

  @Override
  public <T> T selectOne(String statement, Object parameter) {
    return sqlSessionProxy.<T> selectOne(statement, parameter);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
    return sqlSessionProxy.<K, V> selectMap(statement, mapKey);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
    return sqlSessionProxy.<K, V> selectMap(statement, parameter, mapKey);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
    return sqlSessionProxy.<K, V> selectMap(statement, parameter, mapKey, rowBounds);
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement) {
    return sqlSessionProxy.selectCursor(statement);
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter) {
    return sqlSessionProxy.selectCursor(statement, parameter);
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds) {
    return sqlSessionProxy.selectCursor(statement, parameter, rowBounds);
  }

  @Override
  public <E> List<E> selectList(String statement) {
    return sqlSessionProxy.<E> selectList(statement);
  }

  @Override
  public <E> List<E> selectList(String statement, Object parameter) {
    return sqlSessionProxy.<E> selectList(statement, parameter);
  }

  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    return sqlSessionProxy.<E> selectList(statement, parameter, rowBounds);
  }

  @Override
  public void select(String statement, ResultHandler handler) {
    sqlSessionProxy.select(statement, handler);
  }

  @Override
  public void select(String statement, Object parameter, ResultHandler handler) {
    sqlSessionProxy.select(statement, parameter, handler);
  }

  @Override
  public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    sqlSessionProxy.select(statement, parameter, rowBounds, handler);
  }

  @Override
  public int insert(String statement) {
    return sqlSessionProxy.insert(statement);
  }

  @Override
  public int insert(String statement, Object parameter) {
    return sqlSessionProxy.insert(statement, parameter);
  }

  @Override
  public int update(String statement) {
    return sqlSessionProxy.update(statement);
  }

  @Override
  public int update(String statement, Object parameter) {
    return sqlSessionProxy.update(statement, parameter);
  }

  @Override
  public int delete(String statement) {
    return sqlSessionProxy.delete(statement);
  }

  @Override
  public int delete(String statement, Object parameter) {
    return sqlSessionProxy.delete(statement, parameter);
  }

  @Override
  public <T> T getMapper(Class<T> type) {
    return getConfiguration().getMapper(type, this);
  }

  @Override
  public Connection getConnection() {
    final SqlSession sqlSession = localSqlSession.get();
    if (sqlSession == null) {
      throw new SqlSessionException("Error:  Cannot get connection.  No managed session is started.");
    }
    return sqlSession.getConnection();
  }

  @Override
  public void clearCache() {
    final SqlSession sqlSession = localSqlSession.get();
    if (sqlSession == null) {
      throw new SqlSessionException("Error:  Cannot clear the cache.  No managed session is started.");
    }
    sqlSession.clearCache();
  }

  @Override
  public void commit() {
    final SqlSession sqlSession = localSqlSession.get();
    if (sqlSession == null) {
      throw new SqlSessionException("Error:  Cannot commit.  No managed session is started.");
    }
    sqlSession.commit();
  }

  @Override
  public void commit(boolean force) {
    final SqlSession sqlSession = localSqlSession.get();
    if (sqlSession == null) {
      throw new SqlSessionException("Error:  Cannot commit.  No managed session is started.");
    }
    sqlSession.commit(force);
  }

  @Override
  public void rollback() {
    final SqlSession sqlSession = localSqlSession.get();
    if (sqlSession == null) {
      throw new SqlSessionException("Error:  Cannot rollback.  No managed session is started.");
    }
    sqlSession.rollback();
  }

  @Override
  public void rollback(boolean force) {
    final SqlSession sqlSession = localSqlSession.get();
    if (sqlSession == null) {
      throw new SqlSessionException("Error:  Cannot rollback.  No managed session is started.");
    }
    sqlSession.rollback(force);
  }

  @Override
  public List<BatchResult> flushStatements() {
    final SqlSession sqlSession = localSqlSession.get();
    if (sqlSession == null) {
      throw new SqlSessionException("Error:  Cannot rollback.  No managed session is started.");
    }
    return sqlSession.flushStatements();
  }

  @Override
  public void close() {
    final SqlSession sqlSession = localSqlSession.get();
    if (sqlSession == null) {
      throw new SqlSessionException("Error:  Cannot close.  No managed session is started.");
    }
    try {
      sqlSession.close();
    } finally {
      localSqlSession.set(null);
    }
  }

  private class SqlSessionInterceptor implements InvocationHandler {
    public SqlSessionInterceptor() {
        // Prevent Synthetic Access
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      // 获取 与当前线程绑定的SqlSession
      final SqlSession sqlSession = SqlSessionManager.this.localSqlSession.get();
      // 如果有绑定的SqlSession对象
      if (sqlSession != null) {
        // 模式二
        try {
          // 调用真正的sqlSession对象，完成数据库操作
          return method.invoke(sqlSession, args);
        } catch (Throwable t) {
          throw ExceptionUtil.unwrapThrowable(t);
        }
      }
      // 如果没有绑定的SqlSession对象
      else {
        // 模式一
        // 创建一个新的SqlSession对象
        final SqlSession autoSqlSession = openSession();
        try {
          // 通过反射调用该SqlSession对象的方法，完成数据库操作
          final Object result = method.invoke(autoSqlSession, args);
          // 提交事务
          autoSqlSession.commit();
          return result;
        } catch (Throwable t) {
          // 出异常就回滚
          autoSqlSession.rollback();
          throw ExceptionUtil.unwrapThrowable(t);
        } finally {
          // 关闭该SqlSession对象
          autoSqlSession.close();
        }
      }
    }
  }

}
