package com.wb.controls;

import com.wb.common.ScriptBuffer;

/**
 * 服务器端脚本控件，用以使用JavaScript语法执行Java代码。
 */
public class ServerScript extends Control {
	public void create() throws Exception {
		String script = gs("script");
		if (!script.isEmpty())
			ScriptBuffer.run(gs("id"), script, request, response,
					gs("sourceURL"));
	}
}