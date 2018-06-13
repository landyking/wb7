package com.wb.tool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Date;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.util.DbUtil;
import com.wb.util.JsonUtil;
import com.wb.util.SysUtil;

public class Flow {
	/**
	 * 开始一个新的流程路由，并返回路由对象。如果流程中包含开始节点， 流程自动激活开始节点，否则自动激活所有可作为起点的节点。
	 * 如果未能找到可用的开始节点，将抛出异常。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * Route route = Flow.start(workflowId);
	 * route.set(&quot;param1&quot;, &quot;foo&quot;); //设置参数param1
	 * route.set(&quot;param2&quot;, &quot;bar&quot;); //设置参数param2
	 * route.set(request); //把request中的parameters和attributes中的值设置到路由对象中
	 * route.commit(); //提交保存
	 * </pre>
	 * </blockquote>
	 * @param workflowId 工作流模板的id号。
	 * @return 路由对象
	 * @exception Exception 如果工作流模板文件不存在或开始节点未找到将抛出异常。
	 */
	public static Route start(String workflowId) throws Exception {
		return start(workflowId, null);
	}

	/**
	 * 开始一个新的流程路由，并返回路由对象。路由将激活startNodes指定的所有节点。 如果未能找到可用的开始节点，将抛出异常。
	 * @param workflowId 工作流模板的id号。
	 * @param startNodes 开始的结点名称列表。
	 * @return 路由对象
	 * @exception Exception 如果工作流模板文件不存在或开始节点未找到将抛出异常。
	 */
	public static Route start(String workflowId, String[] startNodes)
			throws Exception {
		String workflow = getContent(workflowId, true);
		Date date = new Date();
		if (workflow == null)
			throw new Exception("Workflow \"" + workflowId
					+ "\" does not exist.");
		JSONObject template = new JSONObject(workflow), jo = new JSONObject();
		JSONArray nodes;
		Route route;

		if (startNodes == null)
			nodes = getStartNodes(template);
		else
			nodes = new JSONArray(startNodes);
		if (nodes.length() == 0)
			throw new Exception("Please select some nodes to start the route.");
		route = new Route(jo, template);
		route.set("CURRENT_NODES", nodes);
		route.set("ACTIVE_NODE", nodes.optString(0));
		route.set("ROUTE_ID", SysUtil.getId());
		route.set("FLOW_ID", workflowId);
		route.set("CREATE_DATE", date);
		route.set("MODIFY_DATE", date);
		return route;
	}

	/**
	 * 获取开始节点列表。
	 * @param template 流程模板。
	 * @return 开始节点列表。
	 */
	private static JSONArray getStartNodes(JSONObject template)
			throws Exception {
		JSONArray list = template.getJSONArray("list"), conn, currentNodes = new JSONArray();
		JSONObject obj;
		obj = JsonUtil.findObject(list, "label", "开始");
		if (obj == null) {
			HashSet<String> nodes = new HashSet<String>();
			int i, j;
			String name;

			conn = template.getJSONArray("conn");
			j = conn.length();
			for (i = 0; i < j; i++) {
				obj = conn.getJSONObject(i);
				nodes.add(obj.getString("dst"));
			}
			j = list.length();
			for (i = 0; i < j; i++) {
				obj = list.getJSONObject(i);
				if (obj.getBoolean("isObj")) {
					name = obj.getString("label");
					if (!nodes.contains(name))
						currentNodes.put(name);
				}
			}
		} else
			currentNodes.put("开始");
		return currentNodes;
	}

	/**
	 * 通过路由id号创建新的路由实例。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * Route route = Flow.create(routeId);
	 * </pre>
	 * </blockquote>
	 * @param routeId 路由id。
	 * @return 路由实例对象。
	 * @exception Exception 如果路由不存在将抛出异常。
	 */
	public static Route create(String routeId) throws Exception {
		String route = getContent(routeId, false);
		if (route == null)
			throw new Exception("Route \"" + routeId + "\" does not exist.");
		JSONObject routeObj = new JSONObject(route);
		String tplId = routeObj.optString("FLOW_ID");
		String tpl = getContent(tplId, true);
		if (tpl == null)
			throw new Exception("Workflow \"" + tplId + "\" does not exist.");
		return new Route(routeObj, new JSONObject(tpl));
	}

	/**
	 * 通过路由数据和流程数据创建新的路由实例。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * Route route = Flow.create(routeText, flowJson);
	 * </pre>
	 * </blockquote>
	 * @param route 路由数据JSON对象。
	 * @param flow 流程数据JSON对象。
	 * @return 路由实例对象。
	 */
	public static Route create(String routeText, JSONObject flowJson) {
		return new Route(new JSONObject(routeText), flowJson);
	}

	/**
	 * 删除指定的路由。<br>
	 * 示例: <blockquote>
	 * <pre>
	 * Flow.remove(routeId);
	 * </pre>
	 * </blockquote>
	 * @param routeId 路由id号。
	 * @exception Exception 如果删除过程出现错误将抛出异常。
	 */
	public static void remove(String routeId) throws Exception {
		Connection conn = null;
		PreparedStatement st = null;

		try {
			conn = DbUtil.getConnection();
			st = conn.prepareStatement("delete from WB_ROUTE where ROUTE_ID=?");
			st.setString(1, routeId);
			st.executeUpdate();
		} finally {
			DbUtil.close(st);
			DbUtil.close(conn);
		}
	}

	/**
	 * 获得新的指定id流程模板对象实例。
	 * @param id 流程id号。
	 * @return 流程模板对象实例。
	 */
	public static JSONObject get(String id) {
		return new JSONObject(getContent(id, true));
	}

	/**
	 * 获取指定ID的流程或路由数据。
	 * @param id 编号。
	 * @param isFlow true流程数据，false路由数据。
	 * @return 获取的内容。如果未找到指定id的内容返回null。
	 */
	public static String getContent(String id, boolean isFlow) {
		Connection conn = null;
		PreparedStatement st = null;
		ResultSet rs = null;

		try {
			conn = DbUtil.getConnection();
			if (isFlow)
				st = conn
						.prepareStatement("select FLOW_CONTENT from WB_FLOW where FLOW_ID=?");
			else
				st = conn
						.prepareStatement("select ROUTE_CONTENT from WB_ROUTE where ROUTE_ID=?");
			st.setString(1, id);
			rs = st.executeQuery();
			if (rs.next()) {
				return (String) DbUtil.getObject(rs, 1, Types.CLOB);
			}
		} catch (Throwable e) {
			throw new RuntimeException(e);
		} finally {
			DbUtil.close(rs);
			DbUtil.close(st);
			DbUtil.close(conn);
		}
		return null;
	}
}
