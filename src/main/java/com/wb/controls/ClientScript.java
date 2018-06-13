package com.wb.controls;

/**
 * 客户端脚本控件，用于输出用户自定义的客户端html和js脚本。
 */
public class ClientScript extends ScriptControl {
	public void create() throws Exception {
		headerHtml.append(gs("headerHtml"));
		footerHtml.insert(0, gs("footerHtml"));
		headerScript.append(gs("headerScript"));
		footerScript.insert(0, gs("footerScript"));
	}
}
