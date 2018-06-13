package com.wb.tool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Resource;
import com.wb.common.ScriptBuffer;
import com.wb.util.DateUtil;
import com.wb.util.DbUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;

public class Route {
	/** 流程中的所有节点 */
	private JSONArray allNodes;
	/** 流程中的节点流向 */
	private JSONArray connections;
	/** 路由数据对象。 */
	private JSONObject data;

	/** 创建一个新的路由对象。
	 * @param jo 路由数据对象。
	 * @param tpl 流程模板对象。
	 */
	public Route(JSONObject jo, JSONObject tpl) {
		allNodes = getNodes(tpl);
		connections = tpl.getJSONArray("conn");
		data = jo;
	}

	/**
	 * 获取当前激活的节点名称列表。
	 * @return 节点列表。
	 */
	public JSONArray getCurrentNodes() {
		return (JSONArray) data.opt("CURRENT_NODES");
	}

	/**
	 * 把路由中激活的节点从src移动到dst。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * route.move(&quot;节点1&quot;, &quot;节点2&quot;);
	 * </pre>
	 * </blockquote>
	 * @param src 源节点，源节点必须是当前激活节点中的一个。
	 * @param dst 目标节点。
	 * @exception Exception 如果源节点不是激活的节点或目标节点不存在将抛出异常。
	 */
	public void move(String src, String dst) throws Exception {
		JSONArray currentNodes = getCurrentNodes();
		int fromIndex = JsonUtil.indexOf(currentNodes, src);
		if (fromIndex == -1)
			throw new Exception("Node \"" + src + "\" is not active.");
		JSONObject dstObj = JsonUtil.findObject(allNodes, "label", dst);
		if (dstObj == null)
			throw new Exception("Node \"" + dst + "\" does not exist.");
		currentNodes.put(fromIndex, dst);
	}

	/**
	 * 把路由中当前激活的节点转移到name指定的节点。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * route.to(&quot;dstNode&quot;);
	 * </pre>
	 * </blockquote>
	 * @param name 目标节点名称
	 */
	public void to(String name) throws Exception {
		String[] names = { name };
		deactivateAll();
		activate(names);
	}

	/**
	 * 激活names列表指定的节点。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * String[] nodes = { &quot;节点1&quot;, &quot;节点2&quot; };
	 * route.activate(nodes);
	 * </pre>
	 * </blockquote>
	 * @param names 需要激活的节点列表
	 * @exception Exception 如果列表中的任意一个节点不存在将抛出异常。
	 */
	public void activate(String[] names) throws Exception {
		JSONObject obj;
		JSONArray currentNodes = getCurrentNodes();
		for (String n : names) {
			obj = JsonUtil.findObject(allNodes, "label", n);
			if (obj == null)
				throw new Exception("Node \"" + n + "\" does not exist.");
			if (JsonUtil.indexOf(currentNodes, n) == -1)
				currentNodes.put(n);
		}
	}

	/**
	 * 取消names列表指定的节点激活状态。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * String[] nodes = { &quot;节点1&quot;, &quot;节点2&quot; };
	 * route.deactivate(nodes);
	 * </pre>
	 * </blockquote>
	 * @param names 需要取消激活状态的节点列表。
	 */
	public void deactivate(String[] names) {
		int i;
		JSONArray currentNodes = getCurrentNodes();
		for (String n : names) {
			i = JsonUtil.indexOf(currentNodes, n);
			if (i != -1)
				currentNodes.remove(i);
		}
	}

	/**
	 * 停止所有节点的激活状态。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * route.deactivateAll();
	 * </pre>
	 * </blockquote>
	 */
	public void deactivateAll() {
		JSONArray currentNodes = getCurrentNodes();
		int i, j = currentNodes.length();
		for (i = j - 1; i >= 0; i--)
			currentNodes.remove(i);
	}

	/**
	 * 把当前所有激活的节点流转到下一个。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * route.next();
	 * </pre>
	 * </blockquote>
	 * @exception Exception 如果流转过程中发生错误将抛出异常。
	 */
	public void next() throws Exception {
		JSONArray currentNodes = getCurrentNodes();
		int i, j = currentNodes.length();
		for (i = 0; i < j; i++)
			next(currentNodes.optString(i));
	}

	/**
	 * 流转并激活指定节点的下一个节点。
	 * @param fromNode 指定从哪个节点流转
	 * @exception Exception 如果流转过程中发生错误将抛出异常。
	 */
	public void next(String fromNode) throws Exception {
		JSONObject obj = JsonUtil.findObject(allNodes, "label", fromNode);
		if (obj == null)
			throw new Exception("Node \"" + fromNode + "\" does not exist.");
		String name = obj.optString("label");
		String script = obj.optString("script");
		boolean needMove = true;

		if (!StringUtil.isEmpty(script)) {
			Object result = ScriptBuffer.run(get("FLOW_ID") + "." + name,
					script, this);
			if (result instanceof Boolean && (Boolean) result == false)
				needMove = false;
		}
		if (needMove)
			forward(name);
	}

	/**
	 * 为路由标上暂停执行标识。
	 */
	public void pause() {
		data.put("paused", true);
	}

	/**
	 * 为路由移去暂停执行标识。
	 */
	public void resume() {
		data.put("paused", false);
	}

	/**
	 * 完成路由运行，如果路由存在结束节点，将调用结束节点的脚本。
	 */
	public void stop() throws Exception {
		String stopName = "结束";
		JSONObject obj = JsonUtil.findObject(allNodes, "label", stopName);
		if (obj != null) {
			next(stopName);
			to(stopName);
		}
	}

	/**
	 * 删除路由。
	 * @exception Exception 如果删除过程中发生错误将抛出异常。
	 */
	public void remove() throws Exception {
		Flow.remove((String) get("ROUTE_ID"));
	}

	/**
	 * 获取路由的暂停状态标记。
	 * @return 状态标记
	 */
	public boolean isPaused() {
		return data.optBoolean("paused", false);
	}

	/**
	 * 保存路由的任何改动。
	 */
	public void commit() throws Exception {
		Connection conn = null;
		PreparedStatement st = null;
		Date date = new Date();
		String routeId = data.optString("ROUTE_ID");
		String userId = data.optString("sys.user", "-");
		String activeNode = getCurrentNodes().optString(0);

		try {
			conn = DbUtil.getConnection();
			conn.setAutoCommit(false);
			st = conn.prepareStatement("delete from WB_ROUTE where ROUTE_ID=?");
			st.setString(1, routeId);
			st.executeUpdate();
			DbUtil.close(st);
			st = conn
					.prepareStatement("insert into WB_ROUTE values(?,?,?,?,?,?,?,?)");
			st.setString(1, routeId);
			st.setString(2, data.optString("FLOW_ID"));
			st.setTimestamp(3, new Timestamp(date.getTime()));
			st.setString(4, userId);
			st.setString(5, activeNode);
			st.setString(6, data.optString("TITLE", "-"));
			st.setString(7, data.optString("STATUS", "-"));
			DbUtil.setText(st, 8, data.toString());
			st.executeUpdate();
			conn.commit();
			set("MODIFY_DATE", date);
			set("USER_ID", userId);
			set("ACTIVE_NODE", activeNode);
		} finally {
			DbUtil.close(st);
			DbUtil.close(conn);
		}
	}

	/**
	 * 把指定参数设置到路由。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * route.set(&quot;param1&quot;, &quot;foo&quot;);
	 * route.set(&quot;param2&quot;, &quot;bar&quot;);
	 * route.commit();
	 * </pre>
	 * </blockquote>
	 * @param name 参数名称
	 * @param value 参数值
	 * @exception Exception 如果设置值过程中发生错误将抛出异常。
	 */
	public void set(String name, Object value) throws Exception {
		if (value instanceof Date) {
			value = "@@date." + DateUtil.dateToStr((Date) value);
		} else if (value instanceof InputStream) {
			String id = SysUtil.getId();
			Resource.set(id, getBytes((InputStream) value));
			value = "@@blob." + id;
		} else if (value instanceof byte[]) {
			String id = SysUtil.getId();
			Resource.set(id, (byte[]) value);
			value = "@@byte." + id;
		} else if (value instanceof Object[]) {
			value = "@@list." + new JSONArray((Object[]) value);
		}
		data.put(name, value);
	}

	/**
	 * 把request的parameters和attributes中的所有参数设置到路由。
	 * 如果parameters和attributes中的参数名称重复，前者将被覆盖。
	 * @param request HttpServletRequest对象
	 * @exception Exception 如果设置值过程中发生错误将抛出异常。
	 */
	public void set(HttpServletRequest request) throws Exception {
		JSONObject items = WebUtil.fetch(request);
		Set<Entry<String, Object>> es = items.entrySet();
		for (Entry<String, Object> e : es) {
			set(e.getKey(), e.getValue());
		}
	}

	/**
	 * 获取路由中指定名称的参数值。
	 * @param name 参数名称
	 * @return 参数值
	 */
	public Object get(String name) {
		Object value = data.opt(name);
		if (value instanceof String) {
			String prefix = (String) value;
			if (prefix.startsWith("@@date."))
				value = Timestamp.valueOf(prefix.substring(7));
			else if (prefix.startsWith("@@blob."))
				value = new ByteArrayInputStream(Resource.getBytes(prefix
						.substring(7), null));
			else if (prefix.startsWith("@@byte."))
				value = Resource.getBytes(prefix.substring(7), null);
			else if (prefix.startsWith("@@list."))
				value = getArray(new JSONArray(prefix.substring(7)));
		}
		return value;
	}

	/**
	 * 获取路由中所有参数值组成的对象值，参数名称为key，值为value。
	 */
	public JSONObject get() {
		JSONObject result = new JSONObject();
		String key;

		Set<Entry<String, Object>> es = data.entrySet();
		for (Entry<String, Object> entry : es) {
			key = entry.getKey();
			result.put(key, get(key));
		}
		return result;
	}

	/**
	 * 把JSONArray对象中的内容转换为数组。
	 * @param ja JSON数组对象。
	 * @return 转换后的数组。
	 */
	private Object[] getArray(JSONArray ja) {
		Object o[] = new Object[ja.length()];
		int i, j = ja.length();

		for (i = 0; i < j; i++)
			o[i] = ja.get(i);
		return o;
	}

	/**
	 * 读取指定输入流的所有字节数组。
	 * @param is 输入流对象。
	 * @return 读取的字节数组。
	 */
	private byte[] getBytes(InputStream is) throws Exception {
		ByteArrayOutputStream bos;
		try {
			bos = new ByteArrayOutputStream();
			IOUtils.copy(is, bos);
		} finally {
			is.close();
		}
		return bos.toByteArray();
	}

	/**
	 * 路由中指定节点流转到下一个节点列表。
	 * @param name 节点名称。
	 * @return true成功，false失败。
	 */
	private boolean forward(String name) throws Exception {
		int i, j = connections.length(), k;
		ArrayList<String> list = new ArrayList<String>();
		JSONObject jo;
		String[] names = { name };

		for (i = 0; i < j; i++) {
			jo = connections.getJSONObject(i);
			if (jo.optString("src").equals(name))
				list.add(jo.optString("dst"));
		}
		k = list.size();
		deactivate(names);
		activate(list.toArray(new String[k]));
		return k > 0;
	}

	/**
	 * 获取指定流程模板的所有节点对象组成的JSONArray。
	 * @param template 流程模板对象。
	 * @return 节点列表。
	 */
	private JSONArray getNodes(JSONObject template) {
		JSONArray list, nodes = new JSONArray();
		JSONObject obj;
		int i, j;

		list = template.getJSONArray("list");
		j = list.length();
		for (i = 0; i < j; i++) {
			obj = list.getJSONObject(i);
			if (obj.getBoolean("isObj")) {
				obj.remove("box");
				nodes.put(obj);
			}
		}
		return nodes;
	}
}