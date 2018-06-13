package com.wb.controls;

import java.util.Set;
import java.util.Map.Entry;

import com.wb.util.WebUtil;

/**
 * 用于把指定文本内容发送到客户端。控件将把第一个字符串发送到客户端。
 */
public class Response extends Control {
	public void create() throws Exception {
		String key;

		Set<Entry<String, Object>> es = configs.entrySet();
		for (Entry<String, Object> entry : es) {
			key = entry.getKey();
			if (key.equals("itemId"))
				continue;
			WebUtil.send(response, WebUtil.replaceParams(request,
					(String) entry.getValue()));
			return;
		}
	}
}