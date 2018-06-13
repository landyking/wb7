package com.wb.controls;

import com.wb.common.Var;

public class SqlSwitcher extends Control {
	public void create() throws Exception {
		String sql, varName = gs("varName");

		// 数据库类型由varName属性指定，如果为空使用defaultType变量指定类型
		sql = gs(Var.getString(varName.isEmpty() ? "sys.db.defaultType"
				: varName));
		if (sql.isEmpty())
			sql = gs("default");
		request.setAttribute(gs("itemId"), sql);
	}
}
