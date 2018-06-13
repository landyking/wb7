package com.wb.interact;

import java.io.IOException;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Var;
import com.wb.util.FileUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;
import com.wb.util.WebUtil;

public class VarConfig {
	/**
	 * 获取变量树数据源。
	 */
	public static void getTree(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject jo = JsonUtil.readObject(Var.file);
		JSONObject tree = new JSONObject();
		buildTree(jo, tree);
		WebUtil.send(response, tree);
	}

	/**
	 * 设置变量值，更新缓存中的变量值并把变量值写进文件。
	 */
	public static synchronized void setVar(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject config, folder, object = JsonUtil.readObject(Var.file);
		JSONArray value;
		String name = request.getParameter("name");
		String path = request.getParameter("path");
		String type = request.getParameter("type");
		String valueStr = request.getParameter("value");
		String configStr = request.getParameter("config");
		boolean isNew = Boolean.parseBoolean(request.getParameter("isNew"));
		Object folderObject, primativeVal;

		if (name.indexOf('.') != -1)
			throw new RuntimeException("名称 \"" + name + "\" 不能包含符号 “.”。");
		folderObject = JsonUtil.getValue(object, path, '.');
		if (folderObject instanceof JSONObject) {
			folder = (JSONObject) folderObject;
			if (folder.has(name)) {
				if (isNew)
					throw new RuntimeException("名称 \"" + name + "\" 已经存在。");
			} else {
				if (!isNew)
					throw new RuntimeException("名称 \"" + name + "\" 不存在。");
			}
		} else {
			throw new RuntimeException("目录 \"" + path + "\" 不存在或不是一个目录。");
		}
		if (type.equals("int"))
			primativeVal = Integer.parseInt(valueStr);
		else if (type.equals("bool"))
			primativeVal = Boolean.parseBoolean(valueStr);
		else if (type.equals("double"))
			primativeVal = Double.parseDouble(valueStr);
		else
			primativeVal = valueStr;
		if (isNew) {
			value = new JSONArray();
			value.put(primativeVal);
			value.put(request.getParameter("remark"));
			if (configStr.isEmpty())
				config = new JSONObject();
			else
				config = new JSONObject(configStr);
			config.put("type", request.getParameter("type"));
			value.put(config);
			folder.put(name, value);
		} else {
			value = folder.getJSONArray(name);
			value.put(0, primativeVal);
		}
		FileUtil.syncSave(Var.file, object.toString(2));
		Var.buffer.put(path + '.' + name, primativeVal);
		Var.loadBasicVars();
	}

	/**
	 * 删除变量。
	 */
	public static synchronized void delVar(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String path = request.getParameter("path");
		if (StringUtil.isEmpty(path))
			throw new RuntimeException("Empty path value.");
		JSONArray names = new JSONArray(request.getParameter("names"));
		JSONObject object = JsonUtil.readObject(Var.file), selFolder = (JSONObject) JsonUtil
				.getValue(object, path, '.');
		int i, j = names.length();

		for (i = 0; i < j; i++) {
			selFolder.remove(names.optString(i));
		}
		FileUtil.syncSave(Var.file, object.toString(2));
		for (i = 0; i < j; i++) {
			Var.buffer.remove(StringUtil.concat(path, ".", names.optString(i)));
		}
	}

	/**
	 * 添加、删除或修改目录。
	 */
	public static synchronized void setFolder(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String type = request.getParameter("type");
		JSONObject folder, object = JsonUtil.readObject(Var.file);
		String path = request.getParameter("path"), name = request
				.getParameter("name");

		if (path == null)
			throw new RuntimeException("null path parameter");
		if (type.equals("add")) {
			if (name == null)
				throw new RuntimeException("null name parameter");
			if (name.indexOf('.') != -1)
				throw new RuntimeException("名称 \"" + name + "\" 不能包含符号 “.”。");
			folder = (JSONObject) JsonUtil.getValue(object, path, '.');
			if (folder.has(name))
				throw new RuntimeException("名称 \"" + name + "\" 已经存在。");
			folder.put(name, new JSONObject());
		} else if (type.equals("delete")) {
			JsonUtil.setValue(object, path, '.', null);
			path += '.';
			Set<Entry<String, Object>> es = Var.buffer.entrySet();
			String key;
			for (Entry<String, Object> e : es) {
				key = e.getKey();
				if (key.startsWith(path))
					Var.buffer.remove(key);
			}
		} else if (type.equals("update")) {
			String newPath, newName = request.getParameter("newName");
			if (newName.indexOf('.') != -1)
				throw new RuntimeException("名称 \"" + newName + "\" 不能包含符号 “.”。");
			folder = (JSONObject) JsonUtil.getValue(object, path, '.');
			if (folder.has(newName))
				throw new RuntimeException("名称 \"" + newName + "\" 已经存在。");
			JSONObject jo = folder.getJSONObject(name);
			folder.remove(name);
			folder.put(newName, jo);
			newPath = StringUtil.concat(path, ".", newName, ".");
			path = StringUtil.concat(path, ".", name, ".");
			Set<Entry<String, Object>> es = Var.buffer.entrySet();
			String key;
			int oldPathLen = path.length();

			for (Entry<String, Object> e : es) {
				key = e.getKey();
				if (key.startsWith(path)) {
					Var.buffer.remove(key);
					Var.buffer.put(newPath + key.substring(oldPathLen), e
							.getValue());
				}
			}
		}
		FileUtil.syncSave(Var.file, object.toString(2));
	}

	/**
	 * 递归生成变量目录树。
	 * @param jo 当前变量目录。
	 * @param tree 当前树。
	 */
	private static void buildTree(JSONObject jo, JSONObject tree)
			throws IOException {
		Set<Entry<String, Object>> entrySet = jo.entrySet();
		Object object;
		String key;
		JSONObject node;
		JSONArray children = new JSONArray();

		tree.put("children", children);
		for (Entry<String, Object> entry : entrySet) {
			key = entry.getKey();
			object = jo.opt(key);
			if (object instanceof JSONObject) {
				node = new JSONObject();
				node.put("text", key);
				children.put(node);
				buildTree((JSONObject) object, node);
			}
		}
	}

	/**
	 * 获取变量列表。
	 */
	public static void getVars(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject jo = JsonUtil.readObject(Var.file);
		JSONObject folder = (JSONObject) JsonUtil.getValue(jo, request
				.getParameter("path"), '.');
		if (folder == null)
			throw new IllegalArgumentException("指定路径变量不存在。");
		Set<Entry<String, Object>> entrySet = folder.entrySet();
		JSONArray items = new JSONArray(), jsonValue;
		JSONObject item;
		Object value;

		for (Entry<String, Object> entry : entrySet) {
			value = entry.getValue();
			if (value instanceof JSONArray) {
				jsonValue = (JSONArray) value;
				item = new JSONObject();
				item.put("name", entry.getKey());
				item.put("value", jsonValue.opt(0));
				item.put("remark", jsonValue.opt(1));
				item.put("meta", jsonValue.opt(2));
				items.put(item);
			}
		}
		WebUtil.send(response, new JSONObject().put("rows", items));
	}
}
