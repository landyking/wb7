package com.wb.common;

import java.util.HashSet;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import com.wb.util.LogUtil;

/**
 * 监听会话创建和销毁过程，维护会话列表。
 */
public class SimpleListener implements HttpSessionBindingListener,
		java.io.Serializable {
	/**
	 * 序列化用的编号。
	 */
	private static final long serialVersionUID = 4490661288665900556L;
	/**
	 * Session对象。
	 */
	private transient HttpSession session;
	/**
	 * 用户ID。
	 */
	private String userId;
	/**
	 * 用户名称。
	 */
	private String username;
	/**
	 * IP地址。
	 */
	private String ip;
	/**
	 * 同步锁。
	 */
	private static Object lock = new Object();

	/**
	 * 当会话被创建时触发该方法。
	 */
	public void valueBound(HttpSessionBindingEvent event) {
		session = event.getSession();
		userId = (String) session.getAttribute("sys.user");
		if (Var.log) {
			username = (String) session.getAttribute("sys.username");
			ip = (String) (String) session.getAttribute("sys.ip");
			LogUtil.log(username, ip, LogUtil.INFO, "login");
		}
		// 允许同一用户账户在多终端上登录
		if (Var.recordSession) {
			synchronized (lock) {
				HashSet<HttpSession> sessions = Session.userList.get(userId);
				if (sessions == null)
					sessions = new HashSet<HttpSession>();
				sessions.add(session);
				Session.userList.put(userId, sessions);
			}
		}
	}

	/**
	 * 当会话被销毁时触发该方法。
	 */
	public void valueUnbound(HttpSessionBindingEvent event) {
		if (Var.log)
			LogUtil.log(username, ip, LogUtil.INFO, "logout");
		if (Var.recordSession) {
			synchronized (lock) {
				HashSet<HttpSession> sessions = Session.userList.get(userId);
				if (sessions != null && session != null) {
					sessions.remove(session);
					if (sessions.isEmpty())
						Session.userList.remove(userId);
				}
			}
		}
	}
}
