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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 类型转换是实现 ORM 的重要一环，由于数据库中的数据类型与 Java 语言 的数据类型并不对等，所以在 PrepareStatement 为 sql 语句 绑定参数时，需要从 Java 类型 转换成 JDBC 类型，而从结果集获取数据时，又要将 JDBC 类型 转换成 Java 类型，Mybatis 使用 TypeHandler 完成了上述的双向转换。
 *
 * TypeHandler 是 Mybatis 中所有类型转换器的顶层接口，主要用于实现数据从 Java 类型 到 JdbcType 类型 的相互转换。
 *
 * 除了 Mabatis 本身自带的 TypeHandler 实现，我们还可以添加自定义的 TypeHandler 实现类，在配置文件 mybatis-config.xml 中的 <typeHandler> 标签下配置好 自定义 TypeHandler，Mybatis 就会在初始化时解析该标签内容，完成 自定义 TypeHandler 的注册。
 *
 * @author Clinton Begin
 */
public interface TypeHandler<T> {

  /** 通过 PreparedStatement 为 SQL语句 绑定参数时，将数据从 Java类型 转换为 JDBC类型 */
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /** 从结果集获取数据时，将数据由 JDBC类型 转换成 Java类型 */
  T getResult(ResultSet rs, String columnName) throws SQLException;

  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
