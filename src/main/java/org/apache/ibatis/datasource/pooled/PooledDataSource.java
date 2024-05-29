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
package org.apache.ibatis.datasource.pooled;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * This is a simple, synchronous, thread-safe database connection pool.
 *
 * 数据库建立连接是非常耗时的，且并发的连接数也非常有限。而数据库连接池可以实现数据库的重用、提高响应速度、防止数据库因连接过多而假死等。
 * 数据库连接池的设计思路一般为：
 *
 * 连接池初始化时创建一定数量的连接，并添加到连接池中备用；
 * 当程序需要使用数据库连接时，从连接池中请求，用完后会将其返还给连接池，而不是直接关闭；
 * 连接池会控制总连接上限及空闲连接上线，如果连接池中的连接总数已达上限，且都被占用，后续的连接请求会短暂阻塞后重新尝试获取连接，如此循环，直到有连接可用；
 * 如果连接池中空闲连接较多，已达到空闲连接上限，则返回的连接会被关闭掉，以降低系统开销。
 * PooledDataSource 实现了简易的数据库连接池功能，其创建数据库连接的功能依赖了上面的 UnpooledDataSource。
 *
 * @author Clinton Begin
 */
public class PooledDataSource implements DataSource {

  private static final Log log = LogFactory.getLog(PooledDataSource.class);

  // 管理连接池状态 并统计连接信息
  private final PoolState state = new PoolState(this);

  // 该对象用于生成真正的数据库连接对象，构造函数中会初始化该字段
  private final UnpooledDataSource dataSource;

  // OPTIONAL CONFIGURATION FIELDS
  // 最大活跃连接数
  protected int poolMaximumActiveConnections = 10;

  // 最大空闲连接数
  protected int poolMaximumIdleConnections = 5;

  // 最大Checkout时长
  protected int poolMaximumCheckoutTime = 20000;

  // 在无法获取连接时，线程需要等待的时间
  protected int poolTimeToWait = 20000;

  // 本地坏连接最大数
  protected int poolMaximumLocalBadConnectionTolerance = 3;

  // 检测数据库连接是否可用时，给数据库发送的sql语句
  protected String poolPingQuery = "NO PING QUERY SET";

  // 是否允许发送上述语句
  protected boolean poolPingEnabled;

  // 当连接超过poolPingConnectionsNotUsedFor毫秒未使用，
  // 就发送一次上述sql，检测连接连接是否正常
  protected int poolPingConnectionsNotUsedFor;

  // 根据数据库URL、用户名、密码 生成的一个hash值，
  // 该hash值用于标记当前的连接池，在构造函数中初始化
  private int expectedConnectionTypeCode;

  public PooledDataSource() {
    dataSource = new UnpooledDataSource();
  }

  public PooledDataSource(UnpooledDataSource dataSource) {
    this.dataSource = dataSource;
  }

  public PooledDataSource(String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  /**
   * 下面的两个 getConnection()方法 都会调用 popConnection()
   * 获取 PooledConnection对象，然后调用该对象的 getProxyConnection()方法
   * 获取数据库连接的代理对象
   */
  @Override
  public Connection getConnection() throws SQLException {
    return popConnection(dataSource.getUsername(), dataSource.getPassword()).getProxyConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return popConnection(username, password).getProxyConnection();
  }

  @Override
  public void setLoginTimeout(int loginTimeout) throws SQLException {
    DriverManager.setLoginTimeout(loginTimeout);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return DriverManager.getLoginTimeout();
  }

  @Override
  public void setLogWriter(PrintWriter logWriter) throws SQLException {
    DriverManager.setLogWriter(logWriter);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return DriverManager.getLogWriter();
  }

  public void setDriver(String driver) {
    dataSource.setDriver(driver);
    forceCloseAll();
  }

  public void setUrl(String url) {
    dataSource.setUrl(url);
    forceCloseAll();
  }

  public void setUsername(String username) {
    dataSource.setUsername(username);
    forceCloseAll();
  }

  public void setPassword(String password) {
    dataSource.setPassword(password);
    forceCloseAll();
  }

  public void setDefaultAutoCommit(boolean defaultAutoCommit) {
    dataSource.setAutoCommit(defaultAutoCommit);
    forceCloseAll();
  }

  public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
    dataSource.setDefaultTransactionIsolationLevel(defaultTransactionIsolationLevel);
    forceCloseAll();
  }

  public void setDriverProperties(Properties driverProps) {
    dataSource.setDriverProperties(driverProps);
    forceCloseAll();
  }

  /*
   * The maximum number of active connections
   *
   * @param poolMaximumActiveConnections The maximum number of active connections
   */
  public void setPoolMaximumActiveConnections(int poolMaximumActiveConnections) {
    this.poolMaximumActiveConnections = poolMaximumActiveConnections;
    forceCloseAll();
  }

  /*
   * The maximum number of idle connections
   *
   * @param poolMaximumIdleConnections The maximum number of idle connections
   */
  public void setPoolMaximumIdleConnections(int poolMaximumIdleConnections) {
    this.poolMaximumIdleConnections = poolMaximumIdleConnections;
    forceCloseAll();
  }

  /*
   * The maximum number of tolerance for bad connection happens in one thread
    * which are applying for new {@link PooledConnection}
   *
   * @param poolMaximumLocalBadConnectionTolerance
   * max tolerance for bad connection happens in one thread
   *
   * @since 3.4.5
   */
  public void setPoolMaximumLocalBadConnectionTolerance(
      int poolMaximumLocalBadConnectionTolerance) {
    this.poolMaximumLocalBadConnectionTolerance = poolMaximumLocalBadConnectionTolerance;
  }

  /*
   * The maximum time a connection can be used before it *may* be
   * given away again.
   *
   * @param poolMaximumCheckoutTime The maximum time
   */
  public void setPoolMaximumCheckoutTime(int poolMaximumCheckoutTime) {
    this.poolMaximumCheckoutTime = poolMaximumCheckoutTime;
    forceCloseAll();
  }

  /*
   * The time to wait before retrying to get a connection
   *
   * @param poolTimeToWait The time to wait
   */
  public void setPoolTimeToWait(int poolTimeToWait) {
    this.poolTimeToWait = poolTimeToWait;
    forceCloseAll();
  }

  /*
   * The query to be used to check a connection
   *
   * @param poolPingQuery The query
   */
  public void setPoolPingQuery(String poolPingQuery) {
    this.poolPingQuery = poolPingQuery;
    forceCloseAll();
  }

  /*
   * Determines if the ping query should be used.
   *
   * @param poolPingEnabled True if we need to check a connection before using it
   */
  public void setPoolPingEnabled(boolean poolPingEnabled) {
    this.poolPingEnabled = poolPingEnabled;
    forceCloseAll();
  }

  /*
   * If a connection has not been used in this many milliseconds, ping the
   * database to make sure the connection is still good.
   *
   * @param milliseconds the number of milliseconds of inactivity that will trigger a ping
   */
  public void setPoolPingConnectionsNotUsedFor(int milliseconds) {
    this.poolPingConnectionsNotUsedFor = milliseconds;
    forceCloseAll();
  }

  public String getDriver() {
    return dataSource.getDriver();
  }

  public String getUrl() {
    return dataSource.getUrl();
  }

  public String getUsername() {
    return dataSource.getUsername();
  }

  public String getPassword() {
    return dataSource.getPassword();
  }

  public boolean isAutoCommit() {
    return dataSource.isAutoCommit();
  }

  public Integer getDefaultTransactionIsolationLevel() {
    return dataSource.getDefaultTransactionIsolationLevel();
  }

  public Properties getDriverProperties() {
    return dataSource.getDriverProperties();
  }

  public int getPoolMaximumActiveConnections() {
    return poolMaximumActiveConnections;
  }

  public int getPoolMaximumIdleConnections() {
    return poolMaximumIdleConnections;
  }

  public int getPoolMaximumLocalBadConnectionTolerance() {
    return poolMaximumLocalBadConnectionTolerance;
  }

  public int getPoolMaximumCheckoutTime() {
    return poolMaximumCheckoutTime;
  }

  public int getPoolTimeToWait() {
    return poolTimeToWait;
  }

  public String getPoolPingQuery() {
    return poolPingQuery;
  }

  public boolean isPoolPingEnabled() {
    return poolPingEnabled;
  }

  public int getPoolPingConnectionsNotUsedFor() {
    return poolPingConnectionsNotUsedFor;
  }

  /*
   * Closes all active and idle connections in the pool
   *
   * 关闭连接池中 所有活跃的 及 空闲的连接
   * 当修改连接池的配置（如：用户名、密码、URL等），都会调用本方法
   */
  public void forceCloseAll() {
    // 日常上锁
    synchronized (state) {
      // 更新当前连接池的标识
      expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
      // 依次关闭活跃的连接对象
      for (int i = state.activeConnections.size(); i > 0; i--) {
        try {
          PooledConnection conn = state.activeConnections.remove(i - 1);
          conn.invalidate();

          Connection realConn = conn.getRealConnection();
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
      // 依次关闭空闲的连接对象
      for (int i = state.idleConnections.size(); i > 0; i--) {
        try {
          PooledConnection conn = state.idleConnections.remove(i - 1);
          conn.invalidate();

          Connection realConn = conn.getRealConnection();
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("PooledDataSource forcefully closed/removed all connections.");
    }
  }

  public PoolState getPoolState() {
    return state;
  }

  private int assembleConnectionTypeCode(String url, String username, String password) {
    return ("" + url + username + password).hashCode();
  }

  /**
   * 看一下之前讲过的 PooledConnection 中的 动态代理方法invoke()，可以发现
   * 当调用数据库连接代理对象的 close()方法 时，并未关闭真正的数据库连接，
   * 而是调用了本方法，将连接对象归还给连接池，方便后续使用，本方法也是 PooledDataSource 的核心逻辑之一
   */
  protected void pushConnection(PooledConnection conn) throws SQLException {

    // 国际惯例，操作公共资源先上个锁
    synchronized (state) {
      // 先将该连接从活跃的连接对象列表中剔除
      state.activeConnections.remove(conn);
      // 如果该连接有效
      if (conn.isValid()) {
        // 如果连接池中的空闲连接数未达到阈值 且 该连接确实属于
        // 本连接池（通过之前获取的 expectedConnectionTypeCode 进行校验）
        if (state.idleConnections.size() < poolMaximumIdleConnections && conn.getConnectionTypeCode() == expectedConnectionTypeCode) {
          // CheckoutTime = 应用从连接池取出连接到归还连接的时长
          // accumulatedCheckoutTime = 所有连接累计的CheckoutTime
          state.accumulatedCheckoutTime += conn.getCheckoutTime();
          // 不是自动提交事务的连接 先回滚一波
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          // 从 conn 中取出真正的 数据库连接对象，重新封装成 PooledConnection
          PooledConnection newConn = new PooledConnection(conn.getRealConnection(), this);
          // 将 newConn 放进空闲连接对象列表
          state.idleConnections.add(newConn);
          // 设置 newConn 的相关属性
          newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
          newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());
          // 将原本的 conn 作废
          conn.invalidate();
          if (log.isDebugEnabled()) {
            log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
          }
          // 唤醒阻塞等待的线程
          state.notifyAll();
        }
        // 如果空闲连接已达阈值 或 该连接对象不属于本连接池，则做好统计数据
        // 回滚连接的事务，关闭真正的连接，最后作废 该conn
        else {
          state.accumulatedCheckoutTime += conn.getCheckoutTime();
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          conn.getRealConnection().close();
          if (log.isDebugEnabled()) {
            log.debug("Closed connection " + conn.getRealHashCode() + ".");
          }
          conn.invalidate();
        }
      }
      // 如果该连接是无效的，则记录一下无效的连接数
      else {
        if (log.isDebugEnabled()) {
          log.debug("A bad connection (" + conn.getRealHashCode() + ") attempted to return to the pool, discarding connection.");
        }
        state.badConnectionCount++;
      }
    }
  }

  /**
   * 本方法实现了连接池获取连接对象的具体逻辑，是 PooledDataSource 的核心逻辑之一
   */
  private PooledConnection popConnection(String username, String password) throws SQLException {
    boolean countedWait = false;
    PooledConnection conn = null;
    long t = System.currentTimeMillis();
    int localBadConnectionCount = 0;

    // 循环获取数据库连接对象，直到获取成功
    while (conn == null) {
      // 连接池的连接是公共资源，要对线程加锁
      synchronized (state) {
        // 如果连接池中有空闲的 数据库连接对象，就取出一个
        if (!state.idleConnections.isEmpty()) {
          // Pool has available connection
          conn = state.idleConnections.remove(0);
          if (log.isDebugEnabled()) {
            log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
          }
        } else {
          // Pool does not have available connection
          // 没有空闲的连接对象，就判断一下 活跃的连接数是否已达 设定的峰值
          if (state.activeConnections.size() < poolMaximumActiveConnections) {
            // Can create new connection
            // 还没达到峰值 就创建一个新的连接
            conn = new PooledConnection(dataSource.getConnection(), this);
            if (log.isDebugEnabled()) {
              log.debug("Created connection " + conn.getRealHashCode() + ".");
            }
          } else {
            // Cannot create new connection
            // 如果活跃的连接已达上限，就取出最老的活跃连接对象，判断其是否超时
            PooledConnection oldestActiveConnection = state.activeConnections.get(0);
            long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
            if (longestCheckoutTime > poolMaximumCheckoutTime) {
              // Can claim overdue connection
              // 如果最老的连接超时了，就在 PoolState 中记录一下相关信息，然后将该连接对象释放掉
              state.claimedOverdueConnectionCount++;
              state.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime;
              state.accumulatedCheckoutTime += longestCheckoutTime;
              state.activeConnections.remove(oldestActiveConnection);
              // 如果最老的连接不是 自动提交事务的，就将事务回滚掉
              if (!oldestActiveConnection.getRealConnection().getAutoCommit()) {
                try {
                  oldestActiveConnection.getRealConnection().rollback();
                } catch (SQLException e) {
                  /*
                     Just log a message for debug and continue to execute the following
                     statement like nothing happend.
                     Wrap the bad connection with a new PooledConnection, this will help
                     to not intterupt current executing thread and give current thread a
                     chance to join the next competion for another valid/good database
                     connection. At the end of this loop, bad {@link @conn} will be set as null.
                   */
                  log.debug("Bad connection. Could not roll back");
                }  
              }
              // 从最老连接中取出真正的 数据库连接对象及相关信息，用来构建新的 PooledConnection对象
              conn = new PooledConnection(oldestActiveConnection.getRealConnection(), this);
              conn.setCreatedTimestamp(oldestActiveConnection.getCreatedTimestamp());
              conn.setLastUsedTimestamp(oldestActiveConnection.getLastUsedTimestamp());
              // 将最老活跃连接设为无效
              oldestActiveConnection.invalidate();
              if (log.isDebugEnabled()) {
                log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
              }
            } else {
              // Must wait
              // 如果最老的连接对象也没超时，则进入阻塞等待，
              // 等待时间 poolTimeToWait 可自行设置
              try {
                if (!countedWait) {
                  // 等待次数加一
                  state.hadToWaitCount++;
                  countedWait = true;
                }
                if (log.isDebugEnabled()) {
                  log.debug("Waiting as long as " + poolTimeToWait + " milliseconds for connection.");
                }
                long wt = System.currentTimeMillis();
                // native方法，使执行到这里的线程阻塞等待 poolTimeToWait毫秒
                state.wait(poolTimeToWait);
                // 统计累计等待的时间
                state.accumulatedWaitTime += System.currentTimeMillis() - wt;
              } catch (InterruptedException e) {
                break;
              }
            }
          }
        }
        // 到了这里 基本上就获取到连接对象咯，但我们还要确认一下该连接对象是否是有效的 可用的
        if (conn != null) {
          // ping to server and check the connection is valid or not
          // ping一下数据库服务器，确认该连接对象是否有效
          if (conn.isValid()) {
            // 如果事务提交配置为手动的，则先让该连接回滚一下事务，防止脏数据的出现
            if (!conn.getRealConnection().getAutoCommit()) {
              conn.getRealConnection().rollback();
            }
            // 设置 由数据库URL、用户名、密码 计算出来的hash值，可用于标识该连接所在的连接池
            conn.setConnectionTypeCode(assembleConnectionTypeCode(dataSource.getUrl(), username, password));
            // 设置 从连接池中取出该连接时的时间戳
            conn.setCheckoutTimestamp(System.currentTimeMillis());
            // 设置 最后一次使用的时间戳
            conn.setLastUsedTimestamp(System.currentTimeMillis());
            // 将该连接加入活跃的连接对象列表
            state.activeConnections.add(conn);
            // 请求数据库连接的次数加一
            state.requestCount++;
            // 计算 获取连接的累计时间（accumulate累计）
            state.accumulatedRequestTime += System.currentTimeMillis() - t;
          }
          // 如果获取到的连接无效
          else {
            if (log.isDebugEnabled()) {
              log.debug("A bad connection (" + conn.getRealHashCode() + ") was returned from the pool, getting another connection.");
            }
            // 对无效连接进行统计
            state.badConnectionCount++;
            localBadConnectionCount++;
            conn = null;
            // 如果无效连接超出 阈值，则抛出异常
            if (localBadConnectionCount > (poolMaximumIdleConnections + poolMaximumLocalBadConnectionTolerance)) {
              if (log.isDebugEnabled()) {
                log.debug("PooledDataSource: Could not get a good connection to the database.");
              }
              throw new SQLException("PooledDataSource: Could not get a good connection to the database.");
            }
          }
        }
      }

    }

    // 如果到了这里 连接还为空，则抛出一个未知的服务异常
    if (conn == null) {
      if (log.isDebugEnabled()) {
        log.debug("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
      }
      throw new SQLException("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
    }

    // 返回数据库连接对象
    return conn;
  }

  /*
   * Method to check to see if a connection is still usable
   *
   * ping 一下数据库，检测数据库连接是否正常
   *
   * @param conn - the connection to check
   * @return True if the connection is still usable
   */
  protected boolean pingConnection(PooledConnection conn) {
    boolean result = true;

    try {
      result = !conn.getRealConnection().isClosed();
    } catch (SQLException e) {
      if (log.isDebugEnabled()) {
        log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
      }
      result = false;
    }

    if (result) {
      // 是否允许发送检测语句，检测数据库连接是否正常，poolPingEnabled 可自行配置
      // 该检测会牺牲一定的系统资源，以提高安全性
      if (poolPingEnabled) {
        // 超过 poolPingConnectionsNotUsedFor毫秒 未使用的连接 才会检测其连接状态
        if (poolPingConnectionsNotUsedFor >= 0 && conn.getTimeElapsedSinceLastUse() > poolPingConnectionsNotUsedFor) {
          try {
            if (log.isDebugEnabled()) {
              log.debug("Testing connection " + conn.getRealHashCode() + " ...");
            }
            // 获取真正的连接对象，执行 poolPingQuery = "NO PING QUERY SET" sql语句
            Connection realConn = conn.getRealConnection();
            Statement statement = realConn.createStatement();
            ResultSet rs = statement.executeQuery(poolPingQuery);
            rs.close();
            statement.close();
            if (!realConn.getAutoCommit()) {
              realConn.rollback();
            }
            result = true;
            if (log.isDebugEnabled()) {
              log.debug("Connection " + conn.getRealHashCode() + " is GOOD!");
            }
          }
          // 如果上面这段代码抛出异常，则说明数据库连接有问题，将该连接关闭，返回false
          catch (Exception e) {
            log.warn("Execution of ping query '" + poolPingQuery + "' failed: " + e.getMessage());
            try {
              conn.getRealConnection().close();
            } catch (Exception e2) {
              //ignore
            }
            result = false;
            if (log.isDebugEnabled()) {
              log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
            }
          }
        }
      }
    }
    return result;
  }

  /*
   * Unwraps a pooled connection to get to the 'real' connection
   *
   * @param conn - the pooled connection to unwrap
   * @return The 'real' connection
   */
  public static Connection unwrapConnection(Connection conn) {
    if (Proxy.isProxyClass(conn.getClass())) {
      InvocationHandler handler = Proxy.getInvocationHandler(conn);
      if (handler instanceof PooledConnection) {
        return ((PooledConnection) handler).getRealConnection();
      }
    }
    return conn;
  }

  protected void finalize() throws Throwable {
    forceCloseAll();
    super.finalize();
  }

  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException(getClass().getName() + " is not a wrapper.");
  }

  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }

  public Logger getParentLogger() {
    return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME); // requires JDK version 1.6
  }

}
