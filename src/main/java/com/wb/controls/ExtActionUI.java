package com.wb.controls;

import com.wb.common.XwlBuffer;

/**
 * 模块绑定的解析类。如果当前用户对指定模块不可访问在控件中添加hidden:true属性。
 */
public class ExtActionUI extends ExtControl {
	protected void extendConfig() throws Exception {
		String bindModule = gs("bindModule");
		if (!bindModule.isEmpty() && !XwlBuffer.canAccess(request, bindModule)) {
			if (hasItems)
				headerScript.append(',');
			else
				hasItems = true;
			headerScript.append("hidden:true");
		}
	}
}
