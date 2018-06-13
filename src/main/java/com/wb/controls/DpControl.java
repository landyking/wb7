package com.wb.controls;

import java.sql.ResultSet;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.tool.DataProvider;
import com.wb.util.DbUtil;
import com.wb.util.StringUtil;
import com.wb.util.WebUtil;

/**
 * 数据库内容查询控件，见：{@link DataProvider}
 */
public class DpControl extends Control {
	public void create() throws Exception {
		getContent(true);
	}

	/**
	 * 获取查询的内容，该内容为从数据库查询获得的结果转换为指定类型的脚本或二进制流。
	 * @param directOutput 是否直接输出到客户端，如果为false仅返回脚本类型的内容。
	 * @return 当directOutput为false时，返回脚本类内容，否则返回null。
	 */
	public String getContent(boolean directOutput) throws Exception {
		if (gb("disabled", false))
			return null;
		long startTime = System.currentTimeMillis();
		DataProvider dp;
		Long totalCount = null;
		ResultSet resultSet, totalResultSet;
		Object result, totalResult;
		String sql, totalSql, jndi = gs("jndi");
		String limitRecords = gs("limitRecords");
		String limitExportRecords = gs("limitExportRecords");
		String startParam = request.getParameter("start");
		String limitParam = request.getParameter("limit");
		String type = gs("type"), dictTableNames;
		long beginIndex, endIndex;

		if (StringUtil.isEmpty(type) || "array".equals(type))
			setOrderVars();
		sql = gs("sql");
		totalSql = gs("totalSql");
		if (StringUtil.isEmpty(startParam)) {
			beginIndex = 1;
			request.setAttribute("start", 0l);
		} else
			beginIndex = Long.parseLong(startParam) + 1;
		if (StringUtil.isEmpty(limitParam)) {
			endIndex = Long.MAX_VALUE;
			request.setAttribute("limit", endIndex);
		} else
			endIndex = beginIndex + Long.parseLong(limitParam) - 1;
		// beginIndex和endIndex可用于SQL between语句
		request.setAttribute("beginIndex", beginIndex);
		request.setAttribute("endIndex", endIndex);
		result = getResult(DbUtil.run(request, sql, jndi));
		if (result instanceof ResultSet) {
			resultSet = (ResultSet) result;// ResultSet在请求结束后自动关闭
		} else {
			WebUtil
					.send(
							response,
							StringUtil
									.concat(
											"{\"total\":1,\"metaData\":{\"fields\":[{\"name\":\"result\",\"type\":\"string\"}]},\"columns\":[{\"xtype\":\"rownumberer\",\"width\":40},{\"dataIndex\":\"result\",flex:1,\"text\":\"result\"}],\"rows\":[{\"result\":",
											result == null ? "null"
													: StringUtil.quote(result
															.toString()),
											"}],\"elapsed\":",
											Long.toString(System
													.currentTimeMillis()
													- startTime), "}"));
			return null;
		}
		if (!StringUtil.isEmpty(totalSql)) {
			totalResult = getResult(DbUtil.run(request, totalSql, jndi));
			if (totalResult == null)
				throw new NullPointerException("No value in the totalSql.");
			if (totalResult instanceof ResultSet) {
				totalResultSet = (ResultSet) totalResult;// ResultSet在请求结束后自动关闭
				if (totalResultSet.next()) {
					// 比使用totalResultSet.getLong更安全和通用
					totalCount = Long.parseLong(totalResultSet.getString(1));
				} else
					throw new NullPointerException("Empty total ResultSet.");
			} else
				totalCount = Long.parseLong(totalResult.toString());
		}
		dp = new DataProvider();
		dp.startTime = startTime;
		dp.request = request;
		dp.response = response;
		dp.resultSet = resultSet;
		dp.fields = gs("fields");
		dp.fieldsTag = gs("fieldsTag");
		dp.keyDefines = gs("keyDefines");
		dp.totalCount = totalCount;
		dp.createColumns = gb("createColumns", true);
		if (gb("autoPage", true)) {
			dp.beginIndex = beginIndex;
			dp.endIndex = endIndex;
		}
		if (!limitRecords.isEmpty())
			dp.limitRecords = Integer.parseInt(limitRecords);
		if (!limitExportRecords.isEmpty())
			dp.limitExportRecords = Integer.parseInt(limitExportRecords);
		dp.tag = gs("tag");
		dp.type = type;
		dictTableNames = gs("dictTableNames");
		dp.createKeyValues = gb("createKeyValues", false);
		if (dictTableNames.isEmpty())
			dp.dictTableNames = null;
		else
			dp.dictTableNames = StringUtil.split(dictTableNames, ',', true);
		dp.dictFieldsMap = gs("dictFieldsMap");
		if (directOutput) {
			dp.output();
			return null;
		} else
			return dp.getScript();
	}

	/**
	 * 获取Query运行返回的ResultSet或影响记录数；如果存在输出参数，则尝试获取名称为
	 * result的输出参数否则返回运行Query返回的值（结果集或影响记录数）。
	 * @param object 运行Query返回的对象。
	 * @return 获取的结果集或影响记录数。
	 */
	private Object getResult(Object result) {
		if (result instanceof HashMap<?, ?>) {
			HashMap<?, ?> map = (HashMap<?, ?>) result;
			Object val = map.get(StringUtil.select(gs("resultName"), "result"));
			if (val == null)
				return map.get("return");
			else
				return val;
		} else
			return result;
	}

	/**
	 * 设置sql.orderBy和sql.orderFields变量，以方便使用前端参数进行排序。仅适用于type为array。
	 * @throws Exception 设置过程发生异常。
	 */
	private void setOrderVars() throws Exception {
		String sort = request.getParameter("sort");

		if (StringUtil.isEmpty(sort)
				|| request.getAttribute("sql.orderBy") != null)
			return;
		JSONArray ja = new JSONArray(sort);
		int i, j = ja.length();
		if (j > 0) {
			JSONObject jo;
			StringBuilder exp = new StringBuilder();
			String property, defaultPrefix, prefix;
			JSONObject orderJo;
			String orderFields = gs("orderFields");

			if (StringUtil.isEmpty(orderFields)) {
				orderJo = null;
				defaultPrefix = null;
			} else {
				orderJo = new JSONObject(orderFields);
				defaultPrefix = orderJo.optString("default", null);
			}
			for (i = 0; i < j; i++) {
				jo = ja.getJSONObject(i);
				if (i > 0)
					exp.append(',');
				property = jo.getString("property");
				// 检查名称合法性，防止被SQL注入
				if (!StringUtil.checkName(property)) {
					throw new IllegalArgumentException("Invalid name \""
							+ property + "\".");
				}
				if (orderJo != null) {
					if (orderJo.has(property)) {
						prefix = orderJo.optString(property);
						if (!prefix.isEmpty()) {
							exp.append(prefix);
							exp.append('.');
						}
					} else if (defaultPrefix != null) {
						exp.append(defaultPrefix);
						exp.append('.');
					}
				}
				exp.append(property);
				if (StringUtil.isSame(jo.optString("direction"), "desc"))
					exp.append(" desc");
			}
			request.setAttribute("sql.orderBy", " order by " + exp);
			// sql.orderFields前置","以方便使用，如：order by field{#sql.orderFields#}
			request.setAttribute("sql.orderFields", "," + exp);
		}
	}
}