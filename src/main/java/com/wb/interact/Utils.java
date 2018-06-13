package com.wb.interact;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.wb.util.DbUtil;
import com.wb.util.StringUtil;
import com.wb.util.WebUtil;

public class Utils {
	/**
	 * 获取指定表的特定SQL语句。
	 */
	public static void getSQLDefines(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String sql = request.getParameter("sql");
		String type = request.getParameter("type");
		String jndi = request.getParameter("jndi");
		String table = request.getParameter("table");
		String prefix = request.getParameter("prefix");
		String[] sqlTypes = { "insert", "update", "delete", "select" };
		String[] types = { "native", "params", "replace" };
		String[] sqls = DbUtil.buildSQLs(jndi, table, false, StringUtil
				.indexOf(types, type), null, null, null, null);
		boolean isSelect = "select".equals(sql);

		sql = sqls[StringUtil.indexOf(sqlTypes, sql)];
		if (!StringUtil.isEmpty(prefix) && isSelect)
			sql = StringUtil.concat("select ", prefix, ".", StringUtil
					.replaceAll(sql.substring(7), ",", "," + prefix + "."));
		WebUtil.send(response, sql);
	}
}
