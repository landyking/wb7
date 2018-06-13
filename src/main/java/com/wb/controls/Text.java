package com.wb.controls;

import java.util.Set;
import java.util.Map.Entry;

/**
 * 用于存储大段的文本。控件将把第一个字符串属性存储到以name为名称的request attribute中。
 */
public class Text extends Control {
	public void create() throws Exception {
		String key;

		Set<Entry<String, Object>> es = configs.entrySet();
		for (Entry<String, Object> entry : es) {
			key = entry.getKey();
			if (key.equals("itemId"))
				continue;
			request.setAttribute(gs("itemId"), gs(key));
			return;
		}
	}
}