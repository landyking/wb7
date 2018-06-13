package com.wb.tool;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.wb.common.Var;

public class Console {
	/**
	 * 向当前用户的浏览器控制台中输出指定对象的日志信息。
	 * @param request 请求对象。该请求对象用于关联到对应用户。
	 * @param object 打印的对象。
	 */
	public static void log(HttpServletRequest request, Object object) {
		System.out.println(object);
		print(request, object, "log");
	}

	/**
	 * 向当前用户的浏览器控制台中输出指定对象的调试信息。
	 * @param request 请求对象。该请求对象用于关联到对应用户。
	 * @param object 打印的对象。
	 */
	public static void debug(HttpServletRequest request, Object object) {
		print(request, object, "debug");
	}

	/**
	 * 向当前用户的浏览器控制台中输出指定对象的提示信息。
	 * @param request 请求对象。该请求对象用于关联到对应用户。
	 * @param object 打印的对象。
	 */
	public static void info(HttpServletRequest request, Object object) {
		print(request, object, "info");
	}

	/**
	 * 向当前用户的浏览器控制台中输出指定对象的警告信息。
	 * @param request 请求对象。该请求对象用于关联到对应用户。
	 * @param object 打印的对象。
	 */
	public static void warn(HttpServletRequest request, Object object) {
		print(request, object, "warn");
	}

	/**
	 * 向当前用户的浏览器控制台中输出指定对象的错误信息。
	 * @param request 请求对象。该请求对象用于关联到对应用户。
	 * @param object 打印的对象。
	 */
	public static void error(HttpServletRequest request, Object object) {
		print(request, object, "error");
	}

	/**
	 * 向当前用户的浏览器控制台中输出指定对象的字符串信息。
	 * @param request 请求对象。该请求对象用于关联到对应用户。
	 * @param object 打印的对象。
	 * @param type 输出类型。
	 * @param encoded 是否被编码，被编码的内容可在客户端解码。
	 */
	public static void print(HttpServletRequest request, Object object,
			String type, boolean encoded) {
		if (Var.consolePrint) {
			try {
				HttpSession session = request.getSession(false);

				if (session != null) {
					QueueWriter out = (QueueWriter) session
							.getAttribute("sys.out");
					if (out != null)
						out.print(object, type, encoded);
				}
			} catch (Throwable e) {
				// 忽略
			}
		}
	}

	/**
	 * 向当前用户的浏览器控制台中输出指定对象的字符串信息。
	 * @param request 请求对象。该请求对象用于关联到对应用户。
	 * @param object 打印的对象。
	 * @param type 输出类型。
	 */
	public static void print(HttpServletRequest request, Object object,
			String type) {
		print(request, object, type, false);
	}
}
