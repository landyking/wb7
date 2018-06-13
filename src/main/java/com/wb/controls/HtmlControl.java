package com.wb.controls;

import java.util.ArrayList;
import java.util.Set;
import java.util.Map.Entry;

import org.json.JSONObject;

import com.wb.util.StringUtil;
import com.wb.util.WebUtil;

/**
 * HTML控件的解析类。
 */
public class HtmlControl extends ScriptControl {
	public void create() throws Exception {
		String html, tag = StringUtil.select(gs("tagType"),
				(String) generalMeta.opt("type"));
		StringBuilder tagEnd = new StringBuilder(tag.length() + 3);
		String value, glyph, xtype = (String) generalMeta.opt("xtype");
		tagEnd.append("</").append(tag).append(">");
		ArrayList<String> classList = new ArrayList<String>();
		ArrayList<String> styleList = new ArrayList<String>();

		// tag开始
		headerHtml.append('<');
		headerHtml.append(tag);
		if (xtype != null) {
			// xtype在html控件中定义为固定的输出项
			headerHtml.append(' ');
			headerHtml.append(xtype);
		}
		// 初始化baseClass
		value = generalMeta.optString("baseClass");
		if (!value.isEmpty())
			classList.add(value);
		// 输出配置项
		processConfigs(classList, styleList);
		// 添加合并的class
		value = gs("class");
		if (!value.isEmpty())
			classList.add(value);
		value = StringUtil.join(classList, ' ');
		if (!value.isEmpty()) {
			headerHtml.append(" class=\"");
			headerHtml.append(value);
			headerHtml.append('"');
		}
		// 添加合并的style
		value = gs("style");
		if (!value.isEmpty())
			styleList.add(value);
		value = StringUtil.join(styleList, ';');
		if (!value.isEmpty()) {
			headerHtml.append(" style=\"");
			headerHtml.append(value);
			if (!value.endsWith(";"))
				headerHtml.append(';');
			headerHtml.append('"');
		}
		// tag结尾
		headerHtml.append('>');
		// glyph图标
		glyph = gs("glyph");
		if (!glyph.isEmpty()) {
			headerHtml.append("<span class=\"wb_glyph\">&#"
					+ Integer.valueOf(glyph, 16) + ";</span> ");
		}
		// text为短html
		html = gs("text");
		if (!html.isEmpty())
			headerHtml.append(html);
		html = gs("html");
		if (!html.isEmpty())
			headerHtml.append(html);
		if (!Boolean.FALSE.equals(generalMeta.opt("tagEnd")))
			footerHtml.insert(0, tagEnd);
	}

	/**
	 * 处理配置项。
	 * @param classList class属性列表。
	 * @param styleList style属性列表。
	 */
	protected void processConfigs(ArrayList<String> classList,
			ArrayList<String> styleList) {
		char firstChar;
		String key, value, rename, type, tagItems, group;
		JSONObject itemObject;
		Set<Entry<String, Object>> es = configs.entrySet();
		boolean hasGroup = classList != null && styleList != null;

		for (Entry<String, Object> entry : es) {
			key = entry.getKey();
			value = (String) entry.getValue();
			itemObject = (JSONObject) configsMeta.opt(key);
			if (itemObject == null)
				continue;
			else {
				if (Boolean.TRUE.equals(itemObject.opt("hidden")))
					continue;
				rename = (String) itemObject.opt("rename");
				if (rename != null)
					key = rename;
				if (hasGroup) {
					group = (String) itemObject.opt("group");
					if (group != null) {
						if ("class".equals(group))
							classList.add(value);
						else
							styleList.add(StringUtil.concat(key, ":", value));
						continue;
					}
				}
			}
			headerHtml.append(' ');
			headerHtml.append(key);
			headerHtml.append('=');
			firstChar = value.charAt(0);
			if (firstChar == '@') {
				headerHtml.append(WebUtil.replaceParams(request, value
						.substring(1)));
			} else {
				type = (String) itemObject.opt("type");
				if (type.startsWith("exp")) {
					headerHtml.append(WebUtil.replaceParams(request, value));
				} else {
					headerHtml.append(StringUtil.quote(WebUtil.replaceParams(
							request, value)));
				}
			}
		}
		tagItems = gs("tagConfigs");
		if (!tagItems.isEmpty()) {
			headerHtml.append(' ');
			headerHtml.append(tagItems);
		}
	}
}
