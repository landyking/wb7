package com.wb.controls;

import java.util.Set;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;
import com.wb.util.WebUtil;

/**
 * Ext控件的解析类。
 */
public class ExtControl extends ScriptControl {
	/**
	 * 是否为普通模式。非普通模式下不创建变量和items脚本。
	 */
	public boolean normalMode = true;
	protected boolean hasItems;
	protected boolean hasMediaItems;
	private StringBuilder mediaScript = new StringBuilder();

	public void create() throws Exception {
		if (directOutput())
			return;
		String type, itemId, xtype, userXtype;
		boolean hasXtype, typeAdded = false;
		boolean parentRoot = Boolean.TRUE.equals(parentGeneral.opt("root"));

		userXtype = gs("xtype");
		if (userXtype.isEmpty()) {
			// 如果用户未设置xtype使用控件指定的xtype
			xtype = (String) generalMeta.opt("xtype");
			hasXtype = xtype != null;
		} else {
			// 使用由用户设置的xtype
			xtype = userXtype;
			hasXtype = true;
		}
		type = (String) generalMeta.opt("type");
		if (parentRoot) {
			itemId = gs("itemId");
			if (normalMode) {
				headerScript.append("app.");
				// 实例变量
				headerScript.append(itemId);
				headerScript.append("=app._");
				// 原始变量，加下划线
				headerScript.append(itemId);
			}
			if (gb("createInstance", !Boolean.FALSE.equals(generalMeta
					.opt("autoCreate")))) {
				if (normalMode) {
					if (type == null) {
						headerScript.append("={");
						footerScript.insert(0, "};");
					} else {
						headerScript.append("=new ");
						if (!normalRunType && type.endsWith(".Viewport"))
							headerScript.append("Ext.container.Container");
						else if (!normalRunType && type.equals("tviewport"))
							headerScript.append("Ext.Container");
						else
							headerScript.append(type);
						headerScript.append("({");
						if (normalRunType
								&& hasXtype
								&& !Boolean.FALSE.equals(generalMeta
										.opt("render"))) {
							headerScript.append("renderTo:document.body");
							hasItems = true;
						}
						footerScript.insert(0, "});");
						typeAdded = true;
					}
				} else {
					headerScript.append("{");
					footerScript.insert(0, "}");
				}
			} else {
				headerScript.append("={");
				footerScript.insert(0, "};");
			}
		} else {
			// 如果父控件不是容器则添加为属性
			if (!Boolean.TRUE.equals(parentGeneral.opt("container"))) {
				headerScript.append((String) configs.opt("itemId"));
				headerScript.append(':');
			}
			headerScript.append('{');
			if (lastNode)
				footerScript.insert(0, '}');
			else
				footerScript.insert(0, "},");
		}
		if (hasItems)
			headerScript.append(',');
		else
			hasItems = true;
		if (normalMode)
			headerScript.append("appScope:app");
		else
			headerScript.append("appScope:null");// appScope在代码中设置
		if ("tviewport".equals(type)) {
			// TViewport作为子控件(container)
			headerScript.append(",isViewport:true");
			// TViewport默认为card布局
			if (gs("layout").isEmpty())
				headerScript.append(",layout:\"card\"");
		}
		if (!typeAdded && hasXtype) {
			if (hasItems)
				headerScript.append(',');
			else
				hasItems = true;
			headerScript.append("xtype:\"");
			headerScript.append(xtype);
			headerScript.append("\"");
		}
		extendConfig();
		processConfigs();
		if (events != null)
			processEvents();
		if (controlData.has("children")) {
			addMedia(true);
			if (normalMode) {
				if (Boolean.TRUE.equals(generalMeta.opt("container"))) {
					if (hasItems)
						headerScript.append(',');
					headerScript.append("items:[");
					footerScript.insert(0, ']');
				} else
					headerScript.append(',');
			}
		} else
			addMedia(false);
	}

	/**
	 * 如果存在直接输出的配置项，则直接输出数据。
	 * @return true存在，false不存在。
	 */
	private boolean directOutput() {
		JSONObject config = (JSONObject) generalMeta.opt("directOutput");
		if (config == null)
			return false;
		String value = gs((String) config.opt("name"));
		if (!value.isEmpty()) {
			JSONArray array = (JSONArray) config.optJSONArray("values");
			if (array.indexOf(value) != -1) {
				headerScript.append('"');
				headerScript.append(value);
				headerScript.append('"');
				if (!lastNode)
					headerScript.append(',');
				return true;
			}
		}
		return false;
	}

	/**
	 * 添加中间项脚本。
	 * @param hasChildren 是否有子项。
	 */
	protected void addMedia(boolean hasChildren) {
		JSONObject media = (JSONObject) generalMeta.opt("media");
		if (media == null)
			return;
		String xtypeName = (String) media.opt("xtypeName"), xtype = null;
		boolean xtypeEmpty;
		if (xtypeName == null)
			xtypeEmpty = true;
		else {
			xtype = gs(xtypeName);
			xtypeEmpty = xtype.isEmpty();
		}
		if (!hasChildren && !hasMediaItems && xtypeEmpty)
			return;
		if (hasItems)
			headerScript.append(',');
		headerScript.append((String) media.opt("name"));
		headerScript.append(":{");
		footerScript.insert(0, '}');
		if (hasMediaItems) {
			headerScript.append(mediaScript.toString());
			hasItems = true;
		} else
			hasItems = false;
		if (!xtypeEmpty) {
			if (hasMediaItems)
				headerScript.append(',');
			else
				hasMediaItems = true;
			headerScript.append("xtype:\"");
			headerScript.append(xtype);
			headerScript.append('\"');
			hasItems = true;
		}
	}

	/**
	 * 处理配置项。
	 */
	protected void processConfigs() {
		Set<Entry<String, Object>> es = configs.entrySet();
		String key, value, type, rename;
		StringBuilder script;
		JSONObject itemObject;
		JSONArray params;
		boolean addComma = hasItems, addMediaComma = false;
		char firstChar;

		for (Entry<String, Object> entry : es) {
			key = entry.getKey();
			value = (String) entry.getValue();
			itemObject = (JSONObject) configsMeta.opt(key);
			if (itemObject == null
					|| Boolean.TRUE.equals(itemObject.opt("hidden")))
				continue;
			if (Boolean.TRUE.equals(itemObject.opt("media"))) {
				if (addMediaComma)
					mediaScript.append(',');
				else {
					addMediaComma = true;
					hasMediaItems = true;
				}
				script = mediaScript;
			} else {
				if (addComma)
					headerScript.append(',');
				else {
					addComma = true;
					hasItems = true;
				}
				script = headerScript;
			}
			rename = (String) itemObject.opt("rename");
			if (rename == null)
				script.append(key);
			else
				script.append(rename);
			script.append(':');
			firstChar = value.charAt(0);
			if (firstChar == '@') {
				script.append(WebUtil
						.replaceParams(request, value.substring(1)));
			} else {
				type = (String) itemObject.opt("type");
				if (type.startsWith("exp"))
					script.append(WebUtil.replaceParams(request, value));
				else if (type.equals("glyph")) {
					script.append("0x");
					script.append(WebUtil.replaceParams(request, value));
				} else if (type.equals("js")) {
					script.append("function(");
					params = (JSONArray) itemObject.opt("params");
					if (params != null)
						script.append(JsonUtil.join(params, ","));
					script.append("){\n");
					script.append(WebUtil.replaceParams(request, value));
					script.append("\n}");
				} else
					script.append(StringUtil.quote(WebUtil.replaceParams(
							request, value)));
			}
		}
		addTags(configs, hasItems);
	}

	/**
	 * 处理事件。
	 */
	protected void processEvents() {
		Set<Entry<String, Object>> es = events.entrySet();
		String key, value, rename;
		StringBuilder script;
		JSONObject itemObject;
		JSONArray params;
		boolean addComma = false, addMediaComma = false;

		for (Entry<String, Object> entry : es) {
			key = entry.getKey();
			value = (String) entry.getValue();
			itemObject = (JSONObject) eventsMeta.opt(key);
			if (itemObject == null
					|| Boolean.TRUE.equals(itemObject.opt("hidden")))
				continue;
			if (Boolean.TRUE.equals(itemObject.opt("media"))) {
				if (addMediaComma)
					mediaScript.append(',');
				else {
					addMediaComma = true;
					if (hasMediaItems)
						mediaScript.append(',');
					else
						hasMediaItems = true;
					mediaScript.append("listeners:{");
				}
				script = mediaScript;
			} else {
				if (addComma)
					headerScript.append(',');
				else {
					addComma = true;
					if (hasItems)
						headerScript.append(',');
					else
						hasItems = true;
					headerScript.append("listeners:{");
				}
				script = headerScript;
			}
			script.append('\n');
			rename = (String) itemObject.opt("rename");
			if (rename == null)
				script.append(key);
			else
				script.append(rename);
			script.append(":function(");
			params = (JSONArray) itemObject.opt("params");
			if (params != null)
				script.append(JsonUtil.join(params, ","));
			script.append("){\n");
			script.append(WebUtil.replaceParams(request, value));
			script.append("\n}");
		}
		// 事件在单独的对象中描述，因此使用addComma判断是否有事件。
		if (addTags(events, addComma))
			headerScript.append("\n}");
		if (addMediaComma)
			mediaScript.append("\n}");
	}

	/**
	 * 添加配置项或事件的附加项。
	 * @param object 配置项或事件数据对象。
	 * @param hasContent 是否已经存在内容。
	 * @return true已经添加，false未添加。
	 */
	private boolean addTags(JSONObject object, boolean hasContent) {
		boolean isEvents = object == events;
		String tags;
		if (isEvents)
			tags = (String) object.opt("tagEvents");
		else
			tags = (String) object.opt("tagConfigs");
		if (tags != null) {
			tags = WebUtil.replaceParams(request, tags);
			String trimsTag = tags.trim();
			int beginPos = trimsTag.indexOf('{'), endPos = trimsTag
					.lastIndexOf('}');
			if (beginPos == 0 && endPos == trimsTag.length() - 1)
				tags = trimsTag.substring(beginPos + 1, endPos).trim();
			if (tags.isEmpty())
				return hasContent;
			if (hasContent)
				headerScript.append(',');
			else {
				hasContent = true;
				if (isEvents) {
					if (hasItems)
						headerScript.append(',');
					else
						hasItems = true;
					headerScript.append("listeners:{");
				}
			}
			headerScript.append('\n');
			headerScript.append(tags);
			hasItems = true;
		}
		return hasContent;
	}

	/**
	 * 扩展配置项处理方法，子类可以继承和扩充。
	 */
	protected void extendConfig() throws Exception {
	}
}
