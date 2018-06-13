package com.wb.common;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.tool.Encrypter;
import com.wb.util.DbUtil;
import com.wb.util.StringUtil;
import com.wb.util.WebUtil;

public class Session {
	/**
	 * 存放所有Session的HashMap。每个用户帐户允许保持多个session。
	 */
	public static ConcurrentHashMap<String, HashSet<HttpSession>> userList = new ConcurrentHashMap<String, HashSet<HttpSession>>();
	/**
	 * 同步锁。
	 */
	private static Object lock = new Object();

	/**
	 * 验证登录是否合法，合法则成功登录，否则招聘异常。
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 登录验证过程发生异常。
	 */
	public static void verify(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String referer = StringUtil.opt(request.getHeader("Referer"));
		HttpSession session = request.getSession(false);
		if (Var.getBool("sys.session.verifyImage.enabled")) {
			if (session == null)
				throw new Exception(Str.format(request, "vcExpired"));
			String verifyCode = (String) session.getAttribute("sys.verifyCode");
			// 每个验证码只允许一次有效，以防止暴力破解
			session.removeAttribute("sys.verifyCode");
			if (StringUtil.isEmpty(verifyCode)
					|| !StringUtil.isSame(verifyCode, request
							.getParameter("verifyCode"))) {
				throw new Exception(Str.format(request, "vcInvalid"));
			}
		}
		if (session != null)
			session.invalidate();
		createSession(request);
		if (referer.endsWith("/login")
				|| referer.endsWith("m?xwl=sys/session/login"))
			referer = Var.getString("sys.home");
		if (referer.endsWith("/tlogin")
				|| referer.endsWith("m?xwl=sys/session/tlogin"))
			referer = Var.getString("sys.homeMobile");
		WebUtil.send(response, referer);
	}

	/**
	 * 获取指定用户的所有会话列表信息。
	 */
	public static void getSessionList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String user = request.getParameter("user");
		int start = Integer.parseInt(request.getParameter("start"));
		int limit = Integer.parseInt(request.getParameter("limit"));
		int index = -1, end;
		JSONArray rows = new JSONArray();
		JSONObject row, result;
		HashSet<HttpSession> sessions = userList.get(user);

		if (limit > Var.limitRecords)
			limit = Var.limitRecords;
		end = start + limit;
		if (sessions != null) {
			for (HttpSession session : sessions) {
				index++;
				if (index < start)
					continue;
				else if (index >= end)
					break;
				row = new JSONObject();
				row.put("ip", session.getAttribute("sys.ip"));
				row.put("userAgent", session.getAttribute("sys.userAgent"));
				row.put("createDate", new Date(session.getCreationTime()));
				row.put("lastAccessDate", new Date(session
						.getLastAccessedTime()));
				rows.put(row);
			}
		}
		result = new JSONObject();
		result.put("rows", rows);
		result.put("total", sessions == null ? 0 : sessions.size());
		WebUtil.send(response, result);
	}

	/**
	 * 获取所有在线用户的列表信息。
	 */
	public static void getUserList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		int start = Integer.parseInt(request.getParameter("start"));
		int limit = Integer.parseInt(request.getParameter("limit"));
		int index = -1, end, sessionCount;
		JSONArray rows = new JSONArray();
		JSONObject row, result;
		HashSet<HttpSession> sessions;
		HttpSession session;

		Set<Entry<String, HashSet<HttpSession>>> es = userList.entrySet();
		if (limit > Var.limitRecords)
			limit = Var.limitRecords;
		end = start + limit;
		for (Entry<String, HashSet<HttpSession>> e : es) {
			index++;
			if (index < start)
				continue;
			else if (index >= end)
				break;
			sessions = e.getValue();
			sessionCount = 0;
			session = null;
			for (HttpSession sess : sessions) {
				if (session == null)
					session = sess;
				sessionCount++;
			}
			if (sessionCount == 0)
				continue;
			row = new JSONObject();
			row.put("sessionCount", sessionCount);
			row.put("user", e.getKey());
			row.put("username", session.getAttribute("sys.username"));
			row.put("dispname", session.getAttribute("sys.dispname"));
			row.put("ip", session.getAttribute("sys.ip"));
			row.put("userAgent", session.getAttribute("sys.userAgent"));
			rows.put(row);
		}
		result = new JSONObject();
		result.put("rows", rows);
		result.put("total", userList.size());
		WebUtil.send(response, result);
	}

	/**
	 * 注销当前登录用户的会话。
	 * @param request 请求对象
	 * @param response 响应对象
	 * @throws Exception 调用过程发生异常
	 */
	public static void logout(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		HttpSession session = request.getSession(false);
		if (session != null)
			session.invalidate();
	}

	/**
	 * 获取当前用户的角色列表。如果当前用户未登录或未关联角色数据将返回null。
	 * @param request 当前用户关联的请求对象。
	 * @return 角色列表。
	 */
	public static String[] getRoles(HttpServletRequest request)
			throws Exception {
		HttpSession session = request.getSession(false);
		if (session == null)
			return null;
		else
			return (String[]) session.getAttribute("sys.roles");
	}

	/**
	 * 创建会话。首先验证用户名称和密码是否合法，如果非法抛出异常。如果合法，创建HTTP会话，
	 * 并存储当前用户数据至会话Attribute。
	 * @param request 请求对象。
	 * @throws Exception 创建会话失败。
	 */
	private static void createSession(HttpServletRequest request)
			throws Exception {
		int timeout = Var.sessionTimeout;
		HttpSession session;
		String username = request.getParameter("username");
		String password = request.getParameter("password");
		ResultSet rs = (ResultSet) DbUtil
				.run(
						request,
						"select USER_ID,USER_NAME,DISPLAY_NAME,PASSWORD,USE_LANG from WB_USER where USER_NAME={?username?} and STATUS=1");
		if (!rs.next())
			throw new IllegalArgumentException(Str.format(request,
					"userNotExist", username));
		String userId = rs.getString("USER_ID");
		username = rs.getString("USER_NAME");
		String dispname = rs.getString("DISPLAY_NAME");
		String useLang = rs.getString("USE_LANG");
		if (!rs.getString("PASSWORD").equals(Encrypter.getMD5(password)))
			throw new IllegalArgumentException(Str.format(request,
					"passwordInvalid"));
		session = request.getSession(true);
		// 登录的标志为存在会话且属性sys.logined为非null
		session.setAttribute("sys.logined", true);
		if (timeout == -1)
			timeout = Integer.MAX_VALUE;
		if (timeout > 0)
			session.setMaxInactiveInterval(timeout);
		session.setAttribute("sys.user", userId);
		session.setAttribute("sys.username", username);
		session.setAttribute("sys.dispname", dispname);
		// userAgent最大长度限制为500个字符，防止被注入大字符串
		session.setAttribute("sys.userAgent", StringUtil.substring(request
				.getHeader("user-agent"), 0, 500));
		session.setAttribute("sys.ip", request.getRemoteAddr());
		session.setAttribute("sys.lang", StringUtil.select(useLang, "auto"));
		DbUtil
				.run(request,
						"update WB_USER set LOGIN_TIMES=LOGIN_TIMES+1 where USER_ID={?sys.user?}");
		storeUserValues(request, session, userId);
		SimpleListener simpleListener = new SimpleListener();
		if (Var.uniqueLogin) {
			String usernames[] = { username };
			synchronized (lock) {
				removeSession(usernames);
				session.setAttribute("sys.simpleListener", simpleListener);
			}
		} else
			session.setAttribute("sys.simpleListener", simpleListener);
	}

	/**
	 *  存储用户数据至Session的Attribute.
	 * @param session 会话对象。
	 * @throws Exception 存储过程发生异常。
	 */
	private static void storeUserValues(HttpServletRequest request,
			HttpSession session, String userId) throws Exception {
		// 定义需要存储的名称列表，可以根据业务需要扩充列表
		String names[] = Var.sessionVars.split(",");
		String valueIds[] = new String[names.length];
		String fieldName, fieldValue;
		ArrayList<String> roles = new ArrayList<String>();
		ResultSet rs;
		int i = 0;

		// 角色
		rs = (ResultSet) DbUtil
				.run(
						request,
						"select ROLE_ID from WB_USER_ROLE where USER_ID={?sys.user?} and ROLE_ID<>'default'");
		roles.add("default");// 默认每个用户都具有的角色
		while (rs.next()) {
			roles.add(rs.getString(1));
		}
		session.setAttribute("sys.roles", roles
				.toArray(new String[roles.size()]));
		// 其他数据
		for (String name : names) {
			valueIds[i++] = StringUtil.concat("'", name, "@", userId, "'");
		}
		rs = (ResultSet) DbUtil.run(request,
				"select VAL_ID,VAL_CONTENT from WB_VALUE where VAL_ID in ("
						+ StringUtil.join(valueIds, ',') + ")");
		while (rs.next()) {
			fieldName = rs.getString("VAL_ID");
			fieldName = StringUtil.substring(fieldName, 0, fieldName
					.indexOf('@'));
			fieldValue = rs.getString("VAL_CONTENT");
			session.setAttribute("sys." + fieldName, fieldValue);
		}
	}

	/**
	 * 移除指定用户帐户下所有session。
	 * @param userId 用户id列表。
	 */
	public static void removeSession(String[] userIds) {
		if (!Var.recordSession)
			return;
		HashSet<HttpSession> sessions;
		HttpSession[] sessionArray;
		int i;

		for (String userId : userIds) {
			sessions = userList.get(userId);
			if (sessions != null) {
				sessionArray = new HttpSession[sessions.size()];
				i = 0;
				for (HttpSession session : sessions) {
					sessionArray[i++] = session;
				}
				// 写入数组再invalid目的是避免HashMap ConcurrentModificationException异常
				for (HttpSession session : sessionArray) {
					session.invalidate();
				}
				userList.remove(userId);
			}
		}
	}

	/**
	 * 更新指定用户关联的session。
	 * @param userId 用户id。
	 * @param roles 角色列表。
	 * @param status 状态，true更新角色，false删除session。
	 */
	public static void updateSession(String userId, String roles[],
			boolean status) {
		if (!Var.recordSession)
			return;
		HashSet<HttpSession> sessions = userList.get(userId);
		if (sessions != null) {
			if (status) {
				for (HttpSession session : sessions)
					session.setAttribute("sys.roles", roles);
			} else {
				String[] userIds = { userId };
				removeSession(userIds);
			}
		}
	}
}
