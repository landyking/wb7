package com.wb.controls;

import com.wb.util.SysUtil;

public class Method extends Control {
	public void create() throws Exception {
		String method = gs("method");
		SysUtil.executeMethod(method, request, response);
	}
}
