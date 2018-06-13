package com.wb.controls;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;

import com.wb.common.Var;
import com.wb.tool.Query;
import com.wb.util.DbUtil;
import com.wb.util.StringUtil;

/**
 * 数据库SQL执行控件，见：{@link Query}
 */
public class QueryControl extends Control {
	public void create() throws Exception {
		if (gb("disabled", false))
			return;
		Query query;
		Object result;
		boolean isHashMap;
		String name, itemId = gs("itemId");

		query = new Query();
		query.sql = gs("sql");
		query.request = request;
		query.jndi = gs("jndi");
		query.arrayName = gs("arrayName");
		query.batchUpdate = gb("batchUpdate", Var.batchUpdate);
		query.type = gs("type");
		query.errorText = gs("errorText");
		query.transaction = gs("transaction");
		query.isolation = gs("isolation");
		query.uniqueUpdate = gb("uniqueUpdate", false);
		result = query.run();
		isHashMap = result instanceof HashMap<?, ?>;
		// 如果是hashmap表明含输出参数在下面设置值
		if (!isHashMap)
			request.setAttribute(itemId, result);
		if (result instanceof ResultSet) {
			if (gb("loadData", false))
				DbUtil.loadFirstRow(request, (ResultSet) result, itemId);
		} else if (isHashMap) {
			HashMap<?, ?> map = (HashMap<?, ?>) result;
			Set<?> es = map.entrySet();
			Entry<?, ?> entry;
			for (Object e : es) {
				entry = (Entry<?, ?>) e;
				name = (String) entry.getKey();
				// 在hashmap中返回值名称为return
				if (name.equals("return"))
					name = itemId;
				else
					name = StringUtil.concat(itemId, ".", name);
				request.setAttribute(name, entry.getValue());
			}
		}
	}
}