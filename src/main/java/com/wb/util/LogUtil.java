package com.wb.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

import javax.servlet.http.HttpServletRequest;

import com.wb.common.Var;

/**
 * 存储在数据库中的日志工具方法类。
 */
public class LogUtil {
	/** 提示信息 */
	public static final int INFO = 1;
	/** 警告信息 */
	public static final int WARN = 2;
	/** 错误信息  */
	public static final int ERROR = 3;

	/**
	 * 把日志信息记入数据库日志表中。
	 * @param userName 用户名称。
	 * @param ip ip地址。
	 * @param type 日志类别，1信息，2警告，3错误。
	 * @param object 要记录日志的对象。
	 */
	private static void record(String userName, String ip, int type,
			Object object) {
		long milliSec = System.currentTimeMillis();
		int len;
		Connection conn = null;
		PreparedStatement st = null;
		String text;

		try {
			conn = DbUtil.getConnection();
			st = conn.prepareStatement("insert into WB_LOG values(?,?,?,?,?)");
			if (StringUtil.isEmpty(ip))
				ip = "-";
			if (StringUtil.isEmpty(userName))
				userName = "-";
			if (object == null)
				text = "-";
			else {
				text = object.toString();
				if (StringUtil.isEmpty(text))
					text = "-";
			}
			st.setTimestamp(1, new Timestamp(milliSec));
			st.setString(2, userName);
			st.setString(3, ip);
			st.setInt(4, type);
			// 确保最多只有255个字节被写入数据库
			len = Math.min(text.length(), 256);
			while (text.getBytes().length > 255) {
				len--;
				text = text.substring(0, len);
			}
			st.setString(5, text);
			st.executeUpdate();
		} catch (Throwable e) {
		} finally {
			DbUtil.close(st);
			DbUtil.close(conn);
		}
	}

	/**
	 * 把指定类型的日志信息记入数据库日志表中。
	 * @param type 日志类别，1信息，2警告，3错误。
	 * @param msg 日志信息。
	 */
	private static void recordMsg(int type, Object msg) {
		if (Var.log)
			record(null, null, type, msg);
	}

	/**
	 * 把当前用户指定类型的日志信息记入数据库日志表中。
	 * @param request 请求对象，该对象包含有当前用户和IP信息。
	 * @param type 日志类别，0日志，1信息，2警告，3错误。
	 * @param msg 日志信息。
	 */
	private static void recordUserMsg(HttpServletRequest request, int type,
			Object msg) {
		if (Var.log)
			record(WebUtil.fetch(request, "sys.username"), request
					.getRemoteAddr(), type, msg);
	}

	/**
	 * 把指定用户，IP，类型的日志信息记入数据库日志表中。
	 * @param userName 用户名称。
	 * @param ip ip地址。
	 * @param type 日志类别，1信息，2警告，3错误。
	 * @param msg 日志信息。
	 */
	public static void log(String userName, String ip, int type, Object msg) {
		if (Var.log)
			record(userName, ip, type, msg);
	}

	/**
	 * 把当前用户信息类日志信息记入数据库日志表中。
	 * @param request 请求对象，该对象包含有当前用户和IP信息。
	 * @param msg 日志信息。
	 */
	public static void info(HttpServletRequest request, Object msg) {
		recordUserMsg(request, INFO, msg);
	}

	/**
	 * 把信息类日志信息记入数据库日志表中。
	 * @param msg 日志信息。
	 */
	public static void info(Object msg) {
		recordMsg(INFO, msg);
	}

	/**
	 * 把当前用户警告类日志信息记入数据库日志表中。
	 * @param request 请求对象，该对象包含有当前用户和IP信息。
	 * @param msg 日志信息。
	 */
	public static void warn(HttpServletRequest request, Object s) {
		recordUserMsg(request, WARN, s);
	}

	/**
	 * 把警告类日志信息记入数据库日志表中。
	 * @param msg 日志信息。
	 */
	public static void warn(Object s) {
		recordMsg(WARN, s);
	}

	/**
	 * 把当前用户错误类日志信息记入数据库日志表中。
	 * @param request 请求对象，该对象包含有当前用户和IP信息。
	 * @param msg 日志信息。
	 */
	public static void error(HttpServletRequest request, Object s) {
		recordUserMsg(request, ERROR, s);
	}

	/**
	 * 把错误类日志信息记入数据库日志表中。
	 * @param msg 日志信息。
	 */
	public static void error(Object s) {
		recordMsg(ERROR, s);
	}
}
