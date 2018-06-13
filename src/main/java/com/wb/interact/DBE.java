package com.wb.interact;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Base;
import com.wb.util.DbUtil;
import com.wb.util.JsonUtil;
import com.wb.util.SortUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;

public class DBE {
	/**
	 * 获取数据库表树数据。
	 */
	public static void getTree(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String jndi = request.getParameter("jndi");
		boolean hasSchem = Boolean.parseBoolean(request
				.getParameter("hasSchem"));
		WebUtil.send(response, StringUtil.isEmpty(jndi) ? getDbList()
				: getTableList(jndi, hasSchem, null));
	}

	/**
	 * 如果用户包含演示角色且非管理员，SQL语句仅允许执行select * from table，否则抛出异常。
	 */
	public static void checkSelectSql(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String sql = request.getParameter("sql");
		String roles[] = (String[]) WebUtil.fetchObject(request, "sys.roles");
		if (StringUtil.indexOf(roles, "demo") != -1
				&& StringUtil.indexOf(roles, "admin") == -1
				&& (!sql.startsWith("select * from ")
						|| !StringUtil.checkName(sql.substring(14), true) || sql
						.substring(14).equalsIgnoreCase("WB_USER")))
			SysUtil.accessDenied();
	}

	/**
	 * 从变量sys.jndi节点获得所有配置的jndi，并生成树组件脚本。
	 */
	public static String getDbList() throws Exception {
		JSONObject config = JsonUtil.readObject(new File(Base.path,
				"wb/system/var.json"));
		HashMap<String, Object> map = new HashMap<String, Object>();
		ArrayList<Entry<String, Object>> sortedItems;
		config = config.optJSONObject("sys").optJSONObject("jndi");
		Set<Entry<String, Object>> es = config.entrySet();
		String key;
		JSONObject jo;
		JSONArray ja = new JSONArray();

		// 默认jndi，插入到首行
		config.remove("default");
		for (Entry<String, Object> e : es) {
			key = e.getKey();
			map.put(key, ((JSONArray) e.getValue()).optString(0));
		}
		sortedItems = SortUtil.sortKey(map);
		jo = new JSONObject();
		jo.put("text", "default");
		jo.put("jndi", "default");
		jo.put("iconCls", "db_icon");
		ja.put(jo);

		for (Entry<String, Object> e : sortedItems) {
			jo = new JSONObject();
			jo.put("text", e.getKey());
			jo.put("jndi", e.getKey());
			jo.put("iconCls", "db_icon");
			ja.put(jo);
		}
		return ja.toString();
	}

	/**
	 * 获取指定jndi所有数据库表JSON脚本。
	 * @param jndi jndi名称
	 * @param hasSchem 表名是否包含前缀。
	 * @param tables 表定义。如果该值不为null，匹配的表将显示“存在”图标，
	 * 未匹配的表将显示“不存在”图表。
	 * @return 表列表JSON脚本。 
	 */
	public static String getTableList(String jndi, boolean hasSchem,
			HashSet<String> tables) throws Exception {
		Connection conn = null;
		Statement st = null;
		ResultSet rs = null;
		boolean isOracle, isFirst = true, hasTableDefine = tables != null;
		String types[] = { "TABLE" }, tableSchem, tableName, upperTableName;
		StringBuilder buf = new StringBuilder();
		ArrayList<Entry<String, String>> sortedEntries;
		HashMap<String, String> tableMap = new HashMap<String, String>();

		try {
			conn = DbUtil.getConnection(jndi);
			isOracle = conn.getMetaData().getDatabaseProductName()
					.toLowerCase().indexOf("oracle") != -1;
			if (isOracle) {
				// Oracle由于列出了系统表，因此作单独处理
				st = conn.createStatement();
				rs = st
						.executeQuery("select TABLE_NAME,TABLESPACE_NAME from user_tables");
			} else
				rs = conn.getMetaData().getTables(null, null, null, types);
			while (rs.next()) {
				if (isOracle) {
					tableName = rs.getString(1);
					tableSchem = StringUtil.opt(rs.getString(2));
				} else {
					tableSchem = StringUtil.opt(rs.getString(2));
					tableName = rs.getString(3);
				}
				tableMap.put(tableName, tableSchem);
			}
			sortedEntries = SortUtil.sortKey(tableMap);
			buf.append('[');
			for (Entry<String, String> entry : sortedEntries) {
				if (isFirst)
					isFirst = false;
				else
					buf.append(',');
				tableName = entry.getKey();
				tableSchem = entry.getValue();
				buf.append("{\"text\":\"");
				if (hasSchem && !StringUtil.isEmpty(tableSchem)) {
					buf.append(tableSchem);
					buf.append('.');
				}
				buf.append(tableName);
				buf.append("\",\"table\":\"");
				buf.append(tableName);
				buf.append("\",\"schem\":\"");
				buf.append(tableSchem);
				buf.append("\",\"leaf\":true,\"iconCls\":\"");
				upperTableName = tableName.toUpperCase();
				if (hasTableDefine && tables.contains(upperTableName)) {
					tables.remove(upperTableName);
					buf.append("table_add_icon\"}");
				} else {
					buf.append("table_icon\"}");
				}
			}
			// 不匹配的表添加到最后
			if (hasTableDefine) {
				for (String fullName : tables) {
					if (isFirst)
						isFirst = false;
					else
						buf.append(',');
					buf.append("{\"text\":\"");
					buf.append(fullName);
					buf.append("\",\"table\":\"");
					buf.append(fullName);
					buf
							.append("\",\"schem\":\"\",\"leaf\":true,\"iconCls\":\"table_delete_icon\"}");
				}
			}
			buf.append(']');
			return buf.toString();
		} finally {
			DbUtil.close(rs);
			DbUtil.close(st);
			DbUtil.close(conn);
		}
	}

	/**
	 * 从指定表下载二进制字段内容。
	 */
	public static void downloadBlob(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String jndi = request.getParameter("__jndi"), tableName = request
				.getParameter("__tableName"), fieldName = request
				.getParameter("__fieldName");
		String selectSql = DbUtil.buildSQLs(jndi, tableName, false, 1, null,
				new JSONObject().put(fieldName, 1), null, null)[3];
		ResultSet rs = (ResultSet) DbUtil.run(request, selectSql, jndi);
		DbUtil.outputBlob(rs, request, response, "download");
	}

	/**
	 * 上传文件数据至指定数据库表二进制字段。
	 */
	public static void uploadBlob(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		setBlob(request, false);
	}

	/**
	 * 上传文件数据至指定数据库表二进制字段。
	 */
	public static void clearBlob(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		setBlob(request, true);
	}

	/**
	 * 上传文件数据至指定数据库表二进制字段或清除该字段。
	 * @param isClear 是否清除二进制字段，true清除，false更新。
	 */
	private static void setBlob(HttpServletRequest request, boolean isClear)
			throws Exception {
		String jndi = WebUtil.fetch(request, "__jndi"), tableName = WebUtil
				.fetch(request, "__tableName"), fieldName = WebUtil.fetch(
				request, "__fieldName");

		if (isClear)
			request.setAttribute(fieldName, "");
		else
			request.setAttribute(fieldName, request.getAttribute("file"));
		String updateSql = DbUtil.buildSQLs(jndi, tableName, false, 1, null,
				new JSONObject().put(fieldName, 1), null, null)[1];
		DbUtil.run(request, updateSql, jndi);
	}
}
