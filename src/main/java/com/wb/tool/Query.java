package com.wb.tool;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Str;
import com.wb.common.Var;
import com.wb.util.DbUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;

/**
 * 执行数据库SQL语句的工具类。
 */
public class Query {
	/** 请求对象，用于读取参数和存储数据。在该请求结束后系统自动关闭和释放资源。 */
	public HttpServletRequest request;
	/** SQL语句。 */
	public String sql;
	/** 数据库连接jndi。 */
	public String jndi;
	/** 执行批处理时，指定数据源来自request中存储的该变量。 */
	public String arrayName;
	/** 执行批处理时，指定数据源来自JSONArray对象。  */
	public JSONArray arrayData;
	/** 是否允许批处理操作。 */
	public boolean batchUpdate;
	/** 执行何种SQL操作，可为"query","update","execute","call"，默认为自动。 */
	public String type;
	/** 执行何种数据库事务操作，可为"start","commit","none"。 */
	public String transaction;
	/** 数据库事务隔离级别，可为"readCommitted","readUncommitted","repeatableRead","serializable"。 */
	public String isolation;
	/** 指定插入、更改或删除记录操作是否有且只有1条。 */
	public boolean uniqueUpdate;
	/** 当该值不为空且查询结果集不为空，系统将抛出该信息的异常。 */
	public String errorText;
	/** 用于调试的SQL语句。 */
	private String debugSql;
	/** 经过格式化后可直接运行的SQL语句。 */
	private String formattedSql;
	/** 参数列表，0项名称，1项类型，2项标识（true输出参数，false输出参数）。 */
	private ArrayList<Object[]> paramList;
	/** 参数值列表 */
	private ArrayList<String> paramValList;
	/** 参数数量 */
	private int paramCount;
	/** 用于执行SQL的statement对象 */
	private PreparedStatement statement;

	/**
	 * 运行SQL，并返回运行结果。如果SQL为空或disabled为true将直接返回null。
	 * @return 运行的结果。可能值为结果集，影响记录数或输出参数结果Map。
	 */
	public Object run() throws Exception {
		checkProperties();
		boolean isCall, isCommit, hasArray = arrayData != null
				|| !StringUtil.isEmpty(arrayName);
		int affectedRows;
		Object result = null;
		Connection connection;

		sql = sql.trim();
		replaceMacros();
		connection = DbUtil.getConnection(request, jndi);
		isCommit = "commit".equals(transaction);
		if (isCommit) {
			if (connection.getAutoCommit())
				transaction = "start";

		} else if (StringUtil.isEmpty(transaction)
				&& (uniqueUpdate || hasArray) && connection.getAutoCommit())
			transaction = "start";
		if ("start".equals(transaction))
			DbUtil.startTransaction(connection, isolation);
		if (StringUtil.isEmpty(type)) {
			if (sql.startsWith("{"))
				type = "call";
			else
				type = "execute";
		}
		isCall = "call".equals(type);
		if (isCall)
			statement = connection.prepareCall(formattedSql);
		else
			statement = connection.prepareStatement(formattedSql);
		if (Var.fetchSize != -1)
			statement.setFetchSize(Var.fetchSize);
		WebUtil.setObject(request, SysUtil.getId(), statement);
		regParameters();
		if (hasArray)
			executeBatch();
		else {
			if (Var.debug)
				printSql();
			if ("query".equals(type)) {
				result = statement.executeQuery();
				// 如果返回多个resultset,可通过result获得statement进而获得更多resultset
				WebUtil.setObject(request, SysUtil.getId(), result);
			} else if ("update".equals(type)) {
				affectedRows = statement.executeUpdate();
				result = affectedRows;
				if (uniqueUpdate && affectedRows != 1)
					notUnique();
			} else {
				if (statement.execute()) {
					result = statement.getResultSet();
					WebUtil.setObject(request, SysUtil.getId(), result);
				} else {
					affectedRows = statement.getUpdateCount();
					result = affectedRows;
					if (uniqueUpdate && affectedRows != 1)
						notUnique();
				}
				if (isCall && paramCount > 0) {
					HashMap<String, Object> map = getOutParameter();
					if (map.size() > 0) {
						if (map.containsKey("return"))
							throw new IllegalArgumentException(
									"Invalid output parameter name \"return\"");
						map.put("return", result);
						result = map;
					}
				}
			}
		}
		if (isCommit) {
			connection.commit();
			connection.setAutoCommit(true);
		}
		checkError(result);
		return result;
	}

	/**
	 * 检查参数的合法性，如果非法将抛出异常。
	 */
	private void checkProperties() {
		if (!StringUtil.isEmpty(transaction)) {
			String trans[] = { "start", "commit", "none" };
			if (StringUtil.indexOf(trans, transaction) == -1)
				throw new IllegalArgumentException("Invalid transaction \""
						+ transaction + "\".");
		}
		if (!StringUtil.isEmpty(type)) {
			String types[] = { "query", "update", "execute", "call" };
			if (StringUtil.indexOf(types, type) == -1)
				throw new IllegalArgumentException("Invalid type \"" + type
						+ "\".");
		}
		if (!StringUtil.isEmpty(isolation)) {
			String isolations[] = { "readCommitted", "readUncommitted",
					"repeatableRead", "serializable" };
			if (StringUtil.indexOf(isolations, isolation) == -1)
				throw new IllegalArgumentException("Invalid isolation \""
						+ isolation + "\".");
		}
	}

	/**
	 * 根据arrayName指定的数组参数名称，执行批处理操作。
	 * 批处理中的记录如果未指定参数值将引用最后一次设置的值。
	 * @param arrayName 在parameters或attribute中的数据参数名称。
	 * @throws Exception 执行过程发生异常。
	 */
	private void executeBatch() throws Exception {
		Object param[], valObj;
		JSONArray ja;
		JSONObject jo;
		String val, name;
		int i, j, k, affectedRows;

		if (arrayData == null) {
			Object obj = WebUtil.fetchObject(request, arrayName);
			if (obj instanceof JSONArray)
				ja = (JSONArray) obj;
			else {
				if (obj == null)
					return;
				val = obj.toString();
				if (val.isEmpty())
					return;
				ja = new JSONArray(val);
			}
		} else
			ja = arrayData;
		j = ja.length();
		if (j == 0)
			return;
		for (i = 0; i < j; i++) {
			jo = ja.getJSONObject(i);
			for (k = 0; k < paramCount; k++) {
				param = paramList.get(k);
				name = (String) param[0];
				if (!((Boolean) param[2]) && jo.has(name)) {
					valObj = JsonUtil.opt(jo, name);
					DbUtil.setObject(statement, k + 1, (Integer) param[1],
							valObj);
					if (Var.debug)
						paramValList.set(k, StringUtil.toString(valObj));
				}
			}
			if (Var.debug)
				printSql();
			if (batchUpdate)
				statement.addBatch();
			else {
				affectedRows = statement.executeUpdate();
				if (uniqueUpdate && affectedRows != 1)
					notUnique();
			}
		}
		if (batchUpdate)
			statement.executeBatch();
		// 批处理时不判断uniqueUpdate，因为不是所有数据库都支持
		// 批处理如果要使用uniqueUpdate必须设置batchUpdate为false
	}

	/**
	 * 抛出更新不唯一异常。
	 */
	private void notUnique() {
		throw new RuntimeException(Str.format(request, "updateNotUnique"));
	}

	/**
	 * 替换SQL中的宏参数为SQL参数。
	 */
	private void replaceMacros() {
		StringBuilder buf = new StringBuilder();
		int startPos = 0, endPos = 0, lastPos = 0;

		while ((startPos = sql.indexOf("{?", startPos)) > -1
				&& (endPos = sql.indexOf("?}", endPos)) > -1) {
			buf.append(sql.substring(lastPos, startPos));
			startPos += 2;
			endPos += 2;
			buf.append("'{?");
			buf.append(sql.substring(endPos - 1, endPos));
			buf.append('\'');
			lastPos = endPos;
		}
		buf.append(sql.substring(lastPos));
		debugSql = buf.toString();
		formattedSql = StringUtil.replaceAll(debugSql, "'{?}'", "?");
	}

	/**
	 * 注册输入和输出参数。
	 * @throws Exception 注册参数发生异常。
	 */
	private void regParameters() throws Exception {
		String paraName, param, typeText, orgParam;
		Object obj, paramObjects[];
		int index = 1, dotPos, type, subType, startPos = 0, endPos = 0;
		Integer typeObj;
		boolean hasSub, isOutParam, isCall;
		CallableStatement callStatement;

		paramList = new ArrayList<Object[]>();
		if (Var.debug)
			paramValList = new ArrayList<String>();
		if (statement instanceof CallableStatement)
			callStatement = (CallableStatement) statement;
		else
			callStatement = null;
		isCall = callStatement != null;
		while ((startPos = sql.indexOf("{?", startPos)) > -1
				&& (endPos = sql.indexOf("?}", endPos)) > -1) {
			startPos += 2;
			param = sql.substring(startPos, endPos);
			endPos += 2;
			orgParam = param;
			isOutParam = isCall && param.startsWith("@");
			if (isOutParam) {
				param = param.substring(1);
				dotPos = param.indexOf('.');
				if (dotPos == -1) {
					typeText = "varchar";
					paraName = param;
				} else {
					typeText = param.substring(0, dotPos);
					paraName = param.substring(dotPos + 1);
				}
				hasSub = typeText.indexOf('=') != -1;
				if (hasSub) {
					type = DbUtil
							.getFieldType(StringUtil.getNamePart(typeText));
					subType = Integer.parseInt(StringUtil
							.getValuePart(typeText));
					callStatement.registerOutParameter(index, type, subType);
				} else {
					type = DbUtil.getFieldType(typeText);
					callStatement.registerOutParameter(index, type);
				}
				if (Var.debug) {
					// 输出参数直接输出参数类型和名称
					paramValList.add(orgParam);
				}
			} else {
				dotPos = param.indexOf('.');
				if (dotPos == -1) {
					type = Types.VARCHAR;
					paraName = param;
				} else {
					typeObj = DbUtil.getFieldType(param.substring(0, dotPos));
					if (typeObj == null) {
						type = Types.VARCHAR;
						paraName = param;
					} else {
						type = typeObj;
						paraName = param.substring(dotPos + 1);
					}
				}
				obj = WebUtil.fetchObject(request, paraName);
				DbUtil.setObject(statement, index, type, obj);
				if (Var.debug) {
					// 输入参数输出替换后的值
					paramValList.add(StringUtil.toString(obj));
				}
			}
			paramObjects = new Object[3];
			paramObjects[0] = paraName;
			paramObjects[1] = type;
			paramObjects[2] = isOutParam;
			paramList.add(paramObjects);
			index++;
		}
		paramCount = paramList.size();
	}

	/**
	 * 获得输出参数值。
	 * @throws Exception 获取参数过程发生异常。
	 */
	private HashMap<String, Object> getOutParameter() throws Exception {
		CallableStatement st = (CallableStatement) statement;
		HashMap<String, Object> map = new HashMap<String, Object>();
		Object object, param[];
		int i;

		for (i = 0; i < paramCount; i++) {
			param = paramList.get(i);
			if ((Boolean) param[2]) {
				object = DbUtil.getObject(st, i + 1, (Integer) param[1]);
				if (object instanceof ResultSet)
					WebUtil.setObject(request, SysUtil.getId(), object);
				map.put((String) param[0], object);
			}
		}
		return map;
	}

	/**
	 * 如果结果集不为空且指定errorText属性，将抛出该信息的异常。
	 */
	private void checkError(Object object) throws Exception {
		if (!StringUtil.isEmpty(errorText) && object instanceof ResultSet) {
			ResultSet rs = (ResultSet) object;
			if (rs.next())
				throw new RuntimeException(errorText);
		}
	}

	/**
	 * 打印用于调试的SQL语句。
	 */
	private void printSql() {
		String sql = debugSql;

		for (String s : paramValList) {
			sql = StringUtil.replaceFirst(sql, "{?}", s);
		}
		Console.log(request, sql);
	}
}