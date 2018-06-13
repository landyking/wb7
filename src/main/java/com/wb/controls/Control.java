package com.wb.controls;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import com.wb.util.DateUtil;
import com.wb.util.WebUtil;

/**
 * 控件基类。
 */
public class Control {
	/**
	 * 请求对象
	 */
	public HttpServletRequest request;
	/**
	 * 响应对象
	 */
	public HttpServletResponse response;
	/**
	 * 控件节点数据
	 */
	public JSONObject controlData;
	/**
	 * 控件节点配置项
	 */
	public JSONObject configs;
	/**
	 * 控件节点事件
	 */
	protected JSONObject events;
	/**
	 * 控件元数据信息
	 */
	protected JSONObject controlMeta;
	/**
	 * 控件元数据常规配置信息
	 */
	protected JSONObject generalMeta;
	/**
	 * 控件元数据配置项配置信息
	 */
	protected JSONObject configsMeta;
	/**
	 * 控件元数据事件配置信息
	 */
	protected JSONObject eventsMeta;
	/**
	 * 父控件元数据常规配置信息
	 */
	protected JSONObject parentGeneral;
	/**
	 * 控件节点是否是末尾子节点
	 */
	protected boolean lastNode;
	/**
	 * 是否为普通运行模式
	 */
	protected boolean normalRunType;

	/**
	 * 创建控件时执行的方法
	 * 
	 * @throws Exception 创建过程中发生异常
	 */
	public void create() throws Exception {
	};

	/**
	 * 初始化控件，获取控件的配置数据。
	 * 
	 * @param request 请求对象
	 * @param response 响应对象
	 * @param controlData 控件节点数据
	 * @param controlMeta 控件节点元数据
	 * @param parentGeneral 父控件元数据常规配置信息
	 * @param lastNode 控件节点是否是末尾子节点
	 * @param normalRunType 是否为普通运行模式
	 */
	public void init(HttpServletRequest request, HttpServletResponse response,
			JSONObject controlData, JSONObject controlMeta,
			JSONObject parentGeneral, boolean lastNode, boolean normalRunType) {
		this.request = request;
		this.response = response;
		this.controlData = controlData;
		configs = (JSONObject) controlData.opt("configs");
		events = (JSONObject) controlData.opt("events");
		this.controlMeta = controlMeta;
		generalMeta = (JSONObject) controlMeta.opt("general");
		configsMeta = (JSONObject) controlMeta.opt("configs");
		eventsMeta = (JSONObject) controlMeta.opt("events");
		this.parentGeneral = parentGeneral;
		this.lastNode = lastNode;
		this.normalRunType = normalRunType;
	}

	/**
	 * 获取控件中指定配置项的字符串值，如果值为null则返回空串。
	 * 
	 * @param name 配置项名称
	 * @param defaultValue 默认值
	 * @return 配置项值
	 */
	protected String gs(String name) {
		Object value = configs.opt(name);
		if (value == null)
			return "";
		else
			return WebUtil.replaceParams(request, (String) value);
	}

	/**
	 * 获取控件中指定配置项的整数值，如果值为空则返回0。
	 * 
	 * @param name 配置项名称
	 * @return 配置项值
	 */
	protected int gi(String name) {
		return gi(name, 0);
	}

	/**
	 * 获取控件中指定配置项的整数值，如果值为空则返回默认值。
	 * 
	 * @param name 配置项名称
	 * @param defaultValue 默认值
	 * @return 配置项值
	 */
	protected int gi(String name, int defaultValue) {
		String value = gs(name);
		if (value.isEmpty())
			return defaultValue;
		else
			return Integer.parseInt(value);
	}

	/**
	 * 获取控件中指定配置项的浮点数值，如果值为空则返回0。
	 * 
	 * @param name 配置项名称
	 * @return 配置项值
	 */
	protected float gf(String name) {
		return gf(name, 0);
	}

	/**
	 * 获取控件中指定配置项的浮点值，如果值为空则返回默认值。
	 * 
	 * @param name 配置项名称
	 * @param defaultValue 默认值
	 * @return 配置项值
	 */
	protected float gf(String name, float defaultValue) {
		String value = gs(name);
		if (value.isEmpty())
			return defaultValue;
		else
			return Integer.parseInt(value);
	}

	/**
	 * 获取控件中指定配置项的日期值，如果值为空则返回null。
	 * 
	 * @param name 配置项名称
	 * @return 配置项值
	 */
	protected Date gd(String name) {
		return gd(name, null);
	}

	/**
	 * 获取控件中指定配置项的日期值，如果值为空则返回默认值。
	 * 
	 * @param name 配置项名称
	 * @param defaultValue 默认值
	 * @return 配置项值
	 */
	protected Date gd(String name, Date defaultValue) {
		String value = gs(name);
		if (value.isEmpty())
			return defaultValue;
		else
			return DateUtil.strToDate(value);
	}

	/**
	 * 获取控件中指定配置项的布尔值，如果值为空则返回false。
	 * 
	 * @param name 配置项名称
	 * @return 配置项值
	 */
	protected boolean gb(String name) {
		return gb(name, false);
	}

	/**
	 * 获取控件中指定配置项的布尔值，如果值为空则返回默认值。
	 * 
	 * @param name 配置项名称
	 * @param defaultValue 默认值
	 * @return 配置项值
	 */
	protected boolean gb(String name, boolean defaultValue) {
		String value = gs(name);
		if (value.isEmpty())
			return defaultValue;
		else
			return Boolean.parseBoolean(value);
	}

	/**
	 * 获取控件中指定事件的脚本。
	 * 
	 * @param name 事件名称
	 * @return 事件脚本。
	 */
	protected String ge(String name) {
		if (events == null)
			return "";
		Object event = events.opt(name);
		if (event == null)
			return "";
		else
			return WebUtil.replaceParams(request, (String) event);
	}

	/**
	 * 获取当前请求对象指定名称的attribute或parameter值。
	 * 如果attribute存在则返回attribute，否则返回paramter。
	 * 如果相同名称的attribute或parameter都存在，则返回前者。
	 * 如果都不存在则返回null。
	 * @param name 属性或参数名称
	 * @return 属性或参数值
	 */
	protected String gp(String name) {
		return WebUtil.fetch(request, name);
	}
}
