package com.wb.interact;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Base;
import com.wb.util.FileUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;

/**
 * 控件管理器后台部分应用。
 */
public class Controls {
	/**
	 * 控件的缓存HashMap。
	 * */
	private static ConcurrentHashMap<String, JSONObject> buffer;
	/**
	 * 控件文件。
	 */
	private static final File file = new File(Base.path,
			"wb/system/controls.json");

	/**
	 * 读取指定控件的数据，并发送至客户端。
	 * 
	 * @param request
	 *            请求对象。
	 * @param response
	 *            响应对象。
	 * @throws Exception
	 *             处理过程中发生异常。
	 */
	public static void open(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject json = JsonUtil.readObject(file), control;
		JSONArray data = new JSONArray(), nodes = new JSONArray(request
				.getParameter("controls"));
		int i, j = nodes.length();
		String id;

		for (i = 0; i < j; i++) {
			id = nodes.getString(i);
			control = JsonUtil.findObject(json, "children", "id", id);
			if (control == null)
				throw new IOException("\"" + id + "\" 没有找到。");
			data.put(control);
		}
		WebUtil.send(response, data);
	}

	/**
	 * 获取控件树数据，并发送到客户端。
	 * 
	 * @param request
	 *            请求对象。
	 * @param response
	 *            响应对象。
	 * @throws Exception
	 *             处理过程中发生异常。
	 */
	public static void getControlTree(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject json = JsonUtil.readObject(file);

		if ("ide".equals(request.getParameter("type")))
			setControlNode(json);
		WebUtil.send(response, json);
	}

	/**
	 * IDE模式下设置控件节点为目录节点，并标识控件control属性为true。
	 * 
	 * @param node
	 *            需要设置的控件。
	 */
	private static void setControlNode(JSONObject node) {
		if (node.has("leaf")) {
			node.remove("leaf");
			node.put("type", node.get("id"));
			node.put("control", true);
			node.put("children", new JSONArray());
		} else {
			JSONArray children = node.getJSONArray("children");
			int i, j = children.length();
			for (i = 0; i < j; i++)
				setControlNode(children.getJSONObject(i));
		}
	}

	/**
	 * 在当前的目录结构中添加控件。
	 * 
	 * @param request
	 *            请求对象。
	 * @param response
	 *            响应对象。
	 * @throws Exception
	 *             添加过程中发生异常。
	 */
	public static synchronized void addControl(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject json = JsonUtil.readObject(file), newNode, parentNode;
		JSONArray children;
		String name = request.getParameter("name");
		String parent = request.getParameter("parent");
		String selNode = request.getParameter("selNode");
		String folderId = "";
		boolean isFolder = Boolean.parseBoolean(request
				.getParameter("isFolder"));

		folderId = StringUtil.concat("n", SysUtil.getId());
		if (JsonUtil.findObject(json, "children", "id", isFolder ? folderId
				: name) != null)
			throw new IOException("名称 \"" + name + "\" 已经存在。");
		parentNode = JsonUtil.findObject(json, "children", "id", parent);
		newNode = new JSONObject();
		newNode.put("text", name);
		if (isFolder) {
			newNode.put("id", folderId);
			newNode.put("children", new JSONArray());
		} else {
			newNode.put("id", name);
			newNode.put("iconCls", "item_icon");
			newNode.put("leaf", true);
			newNode.put("general", new JSONObject("{iconCls:'item_icon'}"));
			newNode.put("configs", new JSONObject("{itemId:{type:'string'}}"));
			newNode.put("events", new JSONObject());
		}
		children = parentNode.getJSONArray("children");
		if (selNode.isEmpty())
			children.put(newNode);
		else {
			JSONObject node = JsonUtil.findObject(json, "children", "id",
					selNode);
			int index = children.indexOf(node);
			children.add(++index, newNode);
		}
		save(json);
		if (!isFolder)
			buffer.put("name", new JSONObject(newNode.toString()));
		WebUtil.send(response, folderId);
	}

	/**
	 * 保存一个或多个指定的控件。
	 * 
	 * @param request
	 *            请求对象。
	 * @param response
	 *            响应对象。
	 * @throws Exception
	 *             保存过程中发生异常。
	 */
	public static synchronized void saveControls(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject json = JsonUtil.readObject(file), control;
		JSONArray nodes = new JSONArray(StringUtil.getString(request
				.getInputStream()));
		int i, j = nodes.length();
		String id;

		for (i = 0; i < j; i++) {
			control = nodes.getJSONObject(i);
			id = control.getString("id");
			if (!JsonUtil.replace(json, "children", "id", id, control
					.get("data")))
				throw new IOException("\"" + id + "\" 没有找到。");
		}
		save(json);
		for (i = 0; i < j; i++) {
			control = nodes.getJSONObject(i);
			buffer.put(control.getString("id"), new JSONObject(control.get(
					"data").toString()));
		}
	}

	/**
	 * 对控件目录名称进行重命名。
	 * 
	 * @param request
	 *            请求对象。
	 * @param response
	 *            响应对象。
	 * @throws Exception
	 *             重命名过程中发生异常。
	 */
	public static synchronized void renameFolder(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject json = JsonUtil.readObject(file);
		String id = request.getParameter("id");
		JSONObject folder = JsonUtil.findObject(json, "children", "id", id);

		if (folder == null)
			throw new IOException("目录 \"" + id + "\" 已经被删除。");
		folder.put("text", request.getParameter("newName"));
		save(json);
	}

	/**
	 * 删除控件或目录。
	 * 
	 * @param request
	 *            请求对象。
	 * @param response
	 *            响应对象。
	 * @throws Exception
	 *             删除过程中发生异常。
	 */
	public static synchronized void deleteControls(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject json = JsonUtil.readObject(file);
		JSONArray nodes = new JSONArray(request.getParameter("controls"));
		int i, j = nodes.length();

		for (i = 0; i < j; i++)
			JsonUtil.remove(json, "children", "id", nodes.getString(i));
		save(json);
		buffer.clear();
		saveToBuffer(json);
	}

	/**
	 * 移动控件或目录。
	 * 
	 * @param request
	 *            请求对象。
	 * @param response
	 *            响应对象。
	 * @throws Exception
	 *             移动过程中发生异常。
	 */
	public static synchronized void moveControls(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject json = JsonUtil.readObject(file);
		JSONArray sourceNodes = new JSONArray(request
				.getParameter("sourceNodes")), destChildren, parentNode;
		String destNodeId = request.getParameter("destNode");
		JSONObject destNode = JsonUtil.findObject(json, "children", "id",
				destNodeId);
		String id, movedNode, dropPosition = request
				.getParameter("dropPosition");
		int i, j = sourceNodes.length(), index;

		for (i = 0; i < j; i++) {
			id = sourceNodes.getString(i);
			movedNode = JsonUtil.remove(json, "children", "id", id);
			if (dropPosition.equals("append")) {
				destChildren = destNode.getJSONArray("children");
				destChildren.put(new JSONObject(movedNode));
			} else {
				parentNode = JsonUtil.findArray(json, "children", "id",
						destNodeId);
				index = parentNode.indexOf(destNode);
				if (dropPosition.equals("after"))
					index++;
				parentNode.add(index, new JSONObject(movedNode));
			}
		}
		save(json);
	}

	/**
	 * 复制控件。
	 * 
	 * @param request
	 *            请求对象。
	 * @param response
	 *            响应对象。
	 * @throws Exception
	 *             复制过程中发生异常。
	 */
	public static synchronized void copyControl(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject json = JsonUtil.readObject(file), control, newControl;
		String source = request.getParameter("source");
		String dest = request.getParameter("dest");

		control = JsonUtil.findObject(json, "children", "id", source);
		if (control == null)
			throw new IOException("控件 \"" + source + "\" 没有找到。");
		if (JsonUtil.findObject(json, "children", "id", dest) != null)
			throw new IOException("名称 \"" + dest + "\" 已经存在。");
		JSONArray parentNode = JsonUtil.findArray(json, "children", "id",
				source);
		int index = parentNode.indexOf(control);
		newControl = new JSONObject(control.toString()).put("id", dest).put(
				"text", dest);
		parentNode.add(index + 1, newControl);
		save(json);
		buffer.put("name", new JSONObject(newControl.toString()));
	}

	/**
	 * 获取缓存中指定控件的数据。
	 * 
	 * @param id
	 *            控件id编号。
	 * @return 控件数据。
	 * @throws IOException
	 *             控件没有找到。
	 */
	public static JSONObject get(String id) throws IOException {
		JSONObject object = buffer.get(id);
		if (object == null)
			throw new IOException("控件 \"" + id + "\" 没有找到。");
		return object;
	}

	/**
	 * 保存控件文件的数据至控件文件。
	 * 
	 * @param json
	 *            控件文件数据。
	 * @throws Exception
	 *             保存过程中发生异常。
	 */
	private static void save(JSONObject json) throws Exception {
		FileUtil.syncSave(file, json.toString());
	}

	/**
	 * 加载和初始化。
	 */
	public static synchronized void load() {
		try {
			buffer = new ConcurrentHashMap<String, JSONObject>();
			saveToBuffer(new JSONObject(FileUtil.readString(file)));
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 把控件文件中的所有控件缓存到HashMap中。
	 * 
	 * @param json 控件文件的数据。
	 */
	private static void saveToBuffer(JSONObject json) {
		JSONArray ja = json.getJSONArray("children");
		JSONObject jo;
		int i, j = ja.length();

		for (i = 0; i < j; i++) {
			jo = ja.getJSONObject(i);
			if (jo.optBoolean("leaf"))
				buffer.put(jo.getString("id"), new JSONObject(jo.toString()));
			else
				saveToBuffer(jo);
		}
	}
}
