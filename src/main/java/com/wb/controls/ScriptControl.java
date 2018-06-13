package com.wb.controls;

/**
 * 产生客户端脚本的控件基类。如果控件产生客户端脚本可继承此类。
 */
public class ScriptControl extends Control {
	/** 上下文中的HTML头脚本 **/
	protected StringBuilder headerHtml = new StringBuilder();
	/** 上下文中的HTML尾脚本 **/
	protected StringBuilder footerHtml = new StringBuilder();
	/** 上下文中的JS头脚本 **/
	protected StringBuilder headerScript = new StringBuilder();
	/** 上下文中的JS尾脚本 **/
	protected StringBuilder footerScript = new StringBuilder();

	/**
	 * 获取控件生成的HTML头脚本。
	 * 
	 * @return 生成的脚本。
	 */
	public String getHeaderHtml() {
		return headerHtml.toString();
	}

	/**
	 * 获取控件生成的HTML尾脚本。
	 * 
	 * @return 生成的脚本。
	 */
	public String getFooterHtml() {
		return footerHtml.toString();
	}

	/**
	 * 获取控件生成的JS头脚本。
	 * 
	 * @return 生成的脚本。
	 */
	public String getHeaderScript() {
		return headerScript.toString();
	}

	/**
	 * 获取控件生成的JS尾脚本。
	 * 
	 * @return 生成的脚本。
	 */
	public String getFooterScript() {
		return footerScript.toString();
	}
}
