/*
Copyright (c) putdb.com, all rights reserved.
Contact: contact@putdb.com
Visit: http://www.putdb.com
 */
package com.wb.common;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.wb.tool.Encrypter;
import com.wb.tool.TaskManager;
import com.wb.util.FileUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;

/**
 * WebBuilder基本过滤器，拦截所有的请求路径。任何对应用发起的请求都会触发doFilter方法。
 * <p>
 * 主要作用有：
 * 初始化系统，完成对静态变量的设置和计划任务的加载。
 * 对XWL模块的访问处理，解析对模块文件的访问。
 * 缓存wb目录下的资源，缓存文件内容至内存并根据需要启用gzip压缩，以提高响应性能。
 * 禁止对系统目录的访问，禁止从外部对wb/system进行访问。
 * </p>
 * 
 * @author Jie Chen
 * @version 7
 * @see FileBuffer
 * @see Parser
 */
public class Base implements Filter {
	/** Servlet上下文对象。 */
	public static ServletContext servletContext;
	/** 应用目录的根路径。 */
	public static File path;
	/** 应用目录的根路径长度。 */
	public static int pathLen;
	/** 模块文件的根路径。 */
	public static File modulePath;
	/** 模块目录路径文本。 */
	public static String modulePathText;
	/** 模块目录路径长度。 */
	public static int modulePathLen;
	/** 系统的启动时间。 */
	public static Date startTime;
	/** 提供静态ConcurrentHashMap，该对象可为任何应用使用。 */
	public static ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<String, Object>();
	/** 是否初始化失败。 */
	private static boolean initFailed;
	/** 初始化失败异常对象。 */
	private static Throwable initError;

	/**
	 * WebBuilder过滤器，实现对xwl文件的执行，文件的缓存和系统文件的保护功能。
	 * 
	 * @param request ServletRequest 请求对象。
	 * @param response ServletResponse 响应对象。
	 * @param chain 过滤器链。
	 * @throws ServletException 如果执行过程中发生异常将抛出。
	 */
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		if (initFailed)
			throw new RuntimeException(initError);
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;
		String url, xwl;

		url = req.getServletPath();
		xwl = UrlBuffer.get(url);
		if(!StringUtil.isEmpty(xwl)) {
			System.out.println("request url: " + req.getRequestURL().toString() + ", xwl: " + xwl);
		}
		if (xwl != null) {
			// WebBuilder模块
			request.setCharacterEncoding("utf-8");
			if (xwl.isEmpty()) {
				xwl = request.getParameter("xwl");
				if (xwl == null) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
							"null xwl");
					return;
				}
				xwl = StringUtil.concat(xwl, ".xwl");
				System.out.println("request url: " + req.getRequestURL().toString() + ", xwl: " + xwl);
			}
			setRequest(req);
			Parser parser = new Parser(req, resp);
			parser.parse(xwl);
		} else {
			String lowerUrl = url.toLowerCase();
			if (lowerUrl.startsWith("/wb/modules/")
					|| lowerUrl.startsWith("/wb/system/")) {
				// 系统保护的目录
				resp.sendError(HttpServletResponse.SC_FORBIDDEN, url);
			} else if (Var.cacheEnabled && lowerUrl.startsWith("/wb/")) {
				// WebBuilder缓存目录
				FileBuffer.service(url, req, resp);
			} else {
				// 其他交由用户自定义处理
				chain.doFilter(request, response);
			}
		}
	}

	/**
	 * 系统的初始化以及定义系统常用的静态变量。
	 * 
	 * @param config 过滤器配置对象。
	 * @throws ServletException 如果执行过程中发生异常将抛出。
	 */
	public void init(FilterConfig config) throws ServletException {
		try {
			startTime = new Date();
			servletContext = config.getServletContext();
			path = new File(servletContext.getRealPath("/"));
			pathLen = FileUtil.getPath(path).length() + 1;
			modulePath = new File(path, "wb/modules");
			modulePathText = FileUtil.getPath(Base.modulePath);
			modulePathLen = modulePathText.length() + 1;
			SysUtil.reload(2);
			boolean allowInstall = Var.getBool("sys.service.allowInstall");
			if (!Var.jndi.isEmpty() && !allowInstall) {
				// 如果默认jndi为空，表示数据库未配置。待安装配置完成后加载数据库相关类。
				SysUtil.reload(3);
				TaskManager.start();
			}
//			checkLicense();
			runInitScript();
		} catch (Throwable e) {
			initFailed = true;
			initError = e;
		}
	}

	/**
	 * 对request对象进行一些属性设置。
	 */
	private void setRequest(HttpServletRequest request) {
		long time = System.currentTimeMillis();
		request.setAttribute("sys.date", new Date(time - (time % 1000)));
		request.setAttribute("sys.id", SysUtil.getId());
		WebUtil.setLanguage(request);
	}

	/**
	 * 检查注册码是否合法，如果非法将终止系统的运行。
	 * 该方法提供了授权控制的原理，将注册码同硬件mac绑定，使软件的运行限制在特定机器。
	 * 为增强保密和控制效果，实际验证代码可以放置在多处且能避免破解的系统和业务代码中。
	 */
	private void checkLicense() throws Exception {
		String code = FileUtil.readString(new File(Base.path,
				"wb/system/lic.dat"));
		String validCode = "2782051B84F1254EA33242FCC377861A";// 通用的注册码，可以更改
		String key = "wblic";// 生成注册码时使用的密钥，可以更改
		int index = code.indexOf("lic=");
		if (index != -1) {
			code = code.substring(index + 4);
			if (code.equals(validCode)
					|| code.equals(Encrypter.getMD5(SysUtil.getMacAddress()
							+ key)))
				return;
		}
		// 如果验证未通过终止运行
		System.exit(0);
	}

	/**
	 * 如果存在初始化脚本文件，运行初始化脚本。
	 */
	private void runInitScript() throws Exception {
		File file = new File(path, "wb/system/init.js");
		if (file.exists()) {
			ScriptBuffer.run(FileUtil.readString(file));
		}
	}

	/**
	 * 系统停止时执行，完成系统的清理。
	 */
	public void destroy() {
		try {
			TaskManager.stop();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
}
