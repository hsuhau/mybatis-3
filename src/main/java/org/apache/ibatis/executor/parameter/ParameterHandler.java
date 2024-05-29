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
package org.apache.ibatis.executor.parameter;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * A parameter handler sets the parameters of the {@code PreparedStatement}
 *
 * 我们要执行的 SQL 语句中可能包含占位符 "?"，而每个 "?" 都对应了 BoundSql 中 parameterMappings 集合中的一个元素，在该 ParameterMapping 对象中记录了对应的参数名称以及该参数的相关属性。ParameterHandler 接口定义了一个非常重要的方法 setParameters()，该方法主要负责调用 PreparedStatement 的 set＊() 系列方法，为 SQL 语句绑定实参。MyBatis 只为 ParameterHandler 接口提供了唯一一个实现类 DefaultParameterHandler。
 *
 * @author Clinton Begin
 */
public interface ParameterHandler {

  // 获取用户传入的实参对象
  Object getParameterObject();

  // 本方法主要负责调用PreparedStatement.set*()方法，为SQL语句绑定实参。
  void setParameters(PreparedStatement ps)
      throws SQLException;

}
