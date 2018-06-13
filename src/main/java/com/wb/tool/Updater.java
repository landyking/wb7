package com.wb.tool;

import java.sql.Connection;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.util.DbUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;
import com.wb.util.WebUtil;

/**
 * 执行上下文绑定的insert, update, delete数据库更新操作。
 */
public class Updater {
	/** 请求对象。 */
	public HttpServletRequest request;
	/** 数据库连接jndi。 */
	public String jndi;
	/** 执行何种SQL操作，可为"query","update","execute","call"，默认为自动。 */
	public String type;
	/** 执行何种数据库事务操作，可为"start","commit","none"。 */
	public String transaction;
	/** 是否允许批处理操作。 */
	public boolean batchUpdate;
	/** 指定插入、更改或删除记录操作是否有且只有1条。 */
	public boolean uniqueUpdate = true;
	/** 数据库事务隔离级别。 */
	public String isolation;
	/** 需要更新的表名。 */
	public String tableName;
	/** insert语句。 */
	public String sqlInsert;
	/** update语句。 */
	public String sqlUpdate;
	/** delete语句。 */
	public String sqlDelete;
	/** insert数据源参数名称，默认为create。 */
	public String paramInsert;
	/** update数据源参数名称，默认为update。 */
	public String paramUpdate;
	/** delete数据源参数名称，默认为destroy。 */
	public String paramDelete;
	/** 执行SQL的类型，分为insert，update，delete，如果为空表示数据来自特定参数且执行所有类型。 */
	public String mode;
	/** 是否忽略对二进制字段的处理，默认为false。 */
	public boolean ignoreBlob = false;
	/** SQL语句中的字段是否只使用存在参数值的字段，默认为true。 */
	public boolean useExistFields = true;
	/** where条件部分使用的字段列表，字段之间使用“,”分隔。 */
	public String whereFields;
	/** 用于把字段名称列表转换为指定名称的Map。 */
	public JSONObject fieldsMap;

	/** 运行指定的delete, insert和update操作。 */
	public void run() throws Exception {
		JSONArray destroyData = null, createData = null, updateData = null;
		JSONObject fields = null, whereFieldsObj;
		if (StringUtil.isEmpty(mode)) {
			// 根据destroy/create/update参数执行指定SQL语句
			String params;

			params = WebUtil.fetch(request, StringUtil.select(paramDelete,
					"destroy"));
			if (!StringUtil.isEmpty(params)) {
				destroyData = new JSONArray(params);
				if (useExistFields && fields == null
						&& destroyData.length() > 0)
					fields = destroyData.getJSONObject(0);
			}
			params = WebUtil.fetch(request, StringUtil.select(paramInsert,
					"create"));
			if (!StringUtil.isEmpty(params)) {
				createData = new JSONArray(params);
				if (useExistFields && fields == null && createData.length() > 0)
					fields = createData.getJSONObject(0);
			}
			params = WebUtil.fetch(request, StringUtil.select(paramUpdate,
					"update"));
			if (!StringUtil.isEmpty(params)) {
				updateData = new JSONArray(params);
				if (useExistFields && fields == null && updateData.length() > 0)
					fields = updateData.getJSONObject(0);
			}

		} else {
			// 指定模式，执行单一类型SQL语句，数据源来自所有参数
			JSONArray data;
			if (useExistFields) {
				fields = WebUtil.fetch(request);
				data = new JSONArray().put(fields);
			} else
				data = new JSONArray().put(WebUtil.fetch(request));
			if (mode.equals("delete"))
				destroyData = data;
			else if (mode.equals("update"))
				updateData = data;
			else
				// insert
				createData = data;
		}
		if ((destroyData == null ? 0 : destroyData.length())
				+ (createData == null ? 0 : createData.length())
				+ (updateData == null ? 0 : updateData.length()) == 0)
			return; // 无有效数据返回
		Query query = new Query();
		String sql;
		Connection connection = DbUtil.getConnection(request, jndi);
		boolean isCommit;

		isCommit = "commit".equals(transaction);
		if ((isCommit || StringUtil.isEmpty(transaction))
				&& connection.getAutoCommit()) {
			transaction = "start";
		}
		if ("start".equals(transaction))
			DbUtil.startTransaction(connection, isolation);
		query.request = request;
		query.jndi = jndi;
		query.type = type;
		query.batchUpdate = batchUpdate;
		query.transaction = "none";// 事务由updater控制，因此query事务设置为none
		query.uniqueUpdate = uniqueUpdate;
		if (!StringUtil.isEmpty(tableName)) {
			if (StringUtil.isEmpty(whereFields))
				whereFieldsObj = fields;
			else
				whereFieldsObj = JsonUtil.fromCSV(whereFields);
			String[] sqls = DbUtil.buildSQLs(jndi, tableName, ignoreBlob, 1,
					request, fields, whereFieldsObj, fieldsMap);
			if (StringUtil.isEmpty(sqlInsert))
				sqlInsert = sqls[0];
			if (StringUtil.isEmpty(sqlUpdate))
				sqlUpdate = sqls[1];
			if (StringUtil.isEmpty(sqlDelete))
				sqlDelete = sqls[2];
		}
		if (!StringUtil.isEmpty(sqlDelete) && !"-".equals(sqlDelete)
				&& destroyData != null && destroyData.length() > 0) {
			sql = sqlDelete;
			if (!sql.isEmpty()) {
				query.sql = sql;
				query.arrayData = destroyData;
				query.run();
			}
		}
		if (!StringUtil.isEmpty(sqlUpdate) && !"-".equals(sqlUpdate)
				&& updateData != null && updateData.length() > 0) {
			sql = sqlUpdate;
			if (!sql.isEmpty()) {
				query.sql = sql;
				query.arrayData = updateData;
				query.run();
			}
		}
		if (!StringUtil.isEmpty(sqlInsert) && !"-".equals(sqlInsert)
				&& createData != null && createData.length() > 0) {
			sql = sqlInsert;
			if (!sql.isEmpty()) {
				query.sql = sql;
				query.arrayData = createData;
				query.run();
			}
		}
		if (isCommit) {
			connection.commit();
			connection.setAutoCommit(true);
		}
	}
}