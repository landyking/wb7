package com.wb.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Parser;
import com.wb.common.Str;
import com.wb.common.UrlBuffer;
import com.wb.common.Var;
import com.wb.common.XwlBuffer;
import com.wb.tool.Console;

/**
 * Web工具方法类。
 */
public class WebUtil {
	/**
	 * 向指定url发起POST方式的web请求，并提交参数。
	 * @param url 请求的url地址。
	 * @param params 提交的参数。
	 * @return 请求返回的结果，结果以utf-8编码的字符串表示。
	 * @throws IOException 请求过程中发生异常。
	 */
	public static String submit(String url, JSONObject params)
			throws IOException {
		return submit(url, "POST", params);
	}

	/**
	 * 向指定url发起指定方式的web请求，并提交参数。
	 * @param url 请求的url地址。
	 * @param method 请求使用的方式，如POST, GET。
	 * @param params 提交的参数。
	 * @return 请求返回的结果，结果以utf-8编码的字符串表示。
	 * @throws IOException 请求过程中发生异常。
	 */
	public static String submit(String url, String method, JSONObject params)
			throws IOException {
		String s = new String(submitBytes(url, method, params), "utf-8");
		System.out.println("### submit to url: "+url+", get string: "+s);
		return s;
	}

	/**
	 * 向指定url发起POST方式的web请求，并提交参数。
	 * @param url 请求的url地址。
	 * @param params 提交的参数。
	 * @return 请求返回的结果，结果以字节数组表示。
	 * @throws IOException 请求过程中发生异常。
	 */
	public static byte[] submitBytes(String url, JSONObject params)
			throws IOException {
		byte[] posts = submitBytes(url, "POST", params);
		System.out.println("### submit to url: "+url +", get bytes: "+posts.length);
		return posts;
	}

	/**
	 * 向指定url发起指定方式的web请求，并提交参数。
	 * @param url 请求的url地址。
	 * @param method 请求使用的方式，如POST, GET。
	 * @param params 提交的参数。
	 * @return 请求返回的结果，结果以字节数组表示。
	 * @throws IOException 请求过程中发生异常。
	 */
	public static byte[] submitBytes(String url, String method,
			JSONObject params) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) (new URL(url))
				.openConnection();
		try {
			int timeout = Var.getInt("sys.session.submitTimeout");
			conn.setConnectTimeout(timeout);
			conn.setReadTimeout(timeout);
			conn.setUseCaches(false);
			conn.setRequestMethod(method);
			if (params != null) {
				byte[] data = getParamsText(params).getBytes("utf-8");
				conn.setDoOutput(true);
				conn.setRequestProperty("Content-Type",
						"application/x-www-form-urlencoded; charset=utf-8");
				conn.setRequestProperty("Content-Length", Integer
						.toString(data.length));
				OutputStream os = conn.getOutputStream();
				try {
					os.write(data);
					os.flush();
				} finally {
					os.close();
				}
			}
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			InputStream is = conn.getInputStream();
			try {
				IOUtils.copy(is, bos);
			} finally {
				is.close();
			}
			return bos.toByteArray();
		} finally {
			conn.disconnect();
		}
	}

	/**
	 * 把指定JSONObject中的参数转换成以web形式表示的参数字符串。
	 * @param jo 参数对象。
	 * @return 以web形式表示的参数字符串。
	 * @throws IOException 转换过程发生异常。
	 */
	private static String getParamsText(JSONObject jo) throws IOException {
		StringBuilder sb = new StringBuilder();
		Set<Entry<String, Object>> es = jo.entrySet();
		boolean isFirst = true;

		for (Entry<String, Object> e : es) {
			if (isFirst)
				isFirst = false;
			else
				sb.append("&");
			sb.append(e.getKey());
			sb.append("=");
			sb.append(URLEncoder.encode(e.getValue().toString(), "utf-8"));
		}
		return sb.toString();
	}

	/**
	 * 获取指定请求对象中的排序信息参数。
	 * @param request 请求对象。
	 * @return 排序信息数组，0项property，1项direction。
	 */
	public static String[] getSortInfo(HttpServletRequest request) {
		String sort = request.getParameter("sort");

		if (StringUtil.isEmpty(sort))
			return null;
		JSONObject jo = new JSONArray(sort).getJSONObject(0);
		String[] result = new String[2];
		result[0] = jo.getString("property");
		result[1] = jo.optString("direction");
		return result;
	}

	/**
	 * 把文件名称转换成特定浏览器可识别的字符串，字符串采用浏览器相关的特定编码方式。
	 * @param request 包含浏览器信息的请求对象。
	 * @param filename 文件名称。
	 * @return 转码后的文件名称。
	 * @throws IOException 转码过程发生异常。
	 */
	public static String encodeFilename(HttpServletRequest request,
			String filename) throws IOException {
		String agent = StringUtil.opt(request.getHeader("user-agent"))
				.toLowerCase();
		if (agent.indexOf("opera") != -1)
			return StringUtil.concat("filename*=\"utf-8''", encode(filename),
					"\"");
		else if (agent.indexOf("trident") != -1 || agent.indexOf("msie") != -1)
			return StringUtil.concat("filename=\"", encode(filename), "\"");
		else
			return StringUtil.concat("filename=\"", new String(filename
					.getBytes("utf-8"), "ISO-8859-1"), "\"");
	}

	/**
	 * 把指定字符串使用application/x-www-form-urlencoded方式编码。
	 * @param string 需要编码的字符串。
	 * @return 编码后的字符串。
	 * @throws IOException 编码过程发生异常。
	 */
	public static String encode(String string) throws IOException {
		return StringUtil.replaceAll(URLEncoder.encode(string, "utf-8"), "+",
				"%20");
	}

	/**
	 * 把按指定编码方式编码的字符串解码后重新使用utf-8编码。
	 * @param string 需要重新编码的字符串。
	 * @return 重新编码后的字符串。
	 * @throws IOException 重新编码过程发生异常。
	 */
	public static String decode(String string) throws IOException {
		if (Var.urlEncoding.isEmpty() || StringUtil.isEmpty(string))
			return string;
		return new String(string.getBytes(Var.urlEncoding), "utf-8");
	}

	/**
	 * 清除本次请求使用文件上传功能产生的临时文件和会话中存储的值。
	 * @param request 请求对象。
	 * @param list 上传的值列表。
	 */
	public static void clearUpload(HttpServletRequest request,
			List<FileItem> list) {
		for (FileItem item : list) {
			if (!item.isFormField())
				IOUtils.closeQuietly((InputStream) request.getAttribute(item
						.getFieldName()));
			item.delete();
		}
		String uploadId = (String) request.getAttribute("sys.uploadId");
		if (uploadId != null) {
			HttpSession session = request.getSession(true);
			session.removeAttribute("sys.upread." + uploadId);
			session.removeAttribute("sys.uplen." + uploadId);
		}
	}

	/**
	 * 给指定编号添加上用户id号前缀，格式为userId@id。
	 * @param request 包含用户id号的请求对象。
	 * @param id 需要添加前缀的id号。
	 * @return 添加前缀后的id号。
	 */
	public static String getIdWithUser(HttpServletRequest request, String id) {
		String user = fetch(request, "sys.user");
		return StringUtil.concat(id, "@", StringUtil.opt(user));
	}

	/**
	 * 设置request对象关联session的属性值。如果session不存在将抛出异常。
	 * @param request 请求对象。
	 * @param name 属性名称。
	 * @param value 属性值。
	 * @throws RuntimeException session不存在。
	 */
	public static void setSessionValue(HttpServletRequest request, String name,
			Object value) {
		HttpSession session = request.getSession(false);

		if (session == null)
			throw new RuntimeException("Session does not exist.");
		session.setAttribute(name, value);
	}

	/**
	 * 获取HttpServletRequest和其关联的HttpSession对象中指定名称的属性或参数的原始值。
	 *如果相同名称的属性或参数都存在则返回优先级最高的值。优先级从高到低依次为：
	 *HttpSession的attribute，HttpServletRequest的attribute和
	 *HttpServletRequest的parameter。如果都不存在则返回null。
	 * @param request 请求对象。
	 * @param name 属性或参数名称。
	 * @return 指定名称的属性或参数值。
	 */
	public static Object fetchObject(HttpServletRequest request, String name) {
		HttpSession session = request.getSession(false);
		Object value;

		if (session == null || (value = session.getAttribute(name)) == null) {
			value = request.getAttribute(name);
			if (value == null)
				return request.getParameter(name);
			else
				return value;
		} else
			return value;
	}

	/**
	 * 获取HttpServletRequest和其关联的HttpSession对象中指定名称的属性或参数转换的字符串值。
	 *如果相同名称的属性或参数都存在则返回优先级最高的值。优先级从高到低依次为：
	 *HttpSession的attribute，HttpServletRequest的attribute和
	 *HttpServletRequest的parameter。如果都不存在则返回null。
	 * @param request 请求对象。
	 * @param name 属性或参数名称。
	 * @return 指定名称的属性或参数值。
	 */
	public static String fetch(HttpServletRequest request, String name) {
		Object object = fetchObject(request, name);
		if (object == null)
			return null;
		else
			return object.toString();
	}

	/**
	 * 把HttpServletRequest和其关联的HttpSession对象中属性和参数取值到JSONObject中。
	 *如果相同名称的属性或参数都存在则返回优先级最高的值。优先级从高到低依次为：
	 *HttpSession的attribute，HttpServletRequest的attribute和
	 *HttpServletRequest的parameter。如果都不存在则返回null。
	 * @param request 请求对象。
	 * @return 取到所有值组成的JSONObject对象。
	 */
	public static JSONObject fetch(HttpServletRequest request) {
		JSONObject json = new JSONObject();
		Entry<?, ?> entry;
		String name;
		HttpSession session;
		Iterator<?> requestParams = request.getParameterMap().entrySet()
				.iterator();
		Enumeration<?> requestAttrs = request.getAttributeNames();

		// 获取request parameters
		while (requestParams.hasNext()) {
			entry = (Entry<?, ?>) requestParams.next();
			json.put((String) entry.getKey(), ((String[]) entry.getValue())[0]);
		}
		// 重复概率较小，因此采用覆盖策略效率较高
		// 获取request attributes，如果同名的值已经存在，则覆盖。
		while (requestAttrs.hasMoreElements()) {
			name = requestAttrs.nextElement().toString();
			if (!"sys.app".equals(name) && !"sys.varMap".equals(name))
				json.put(name, request.getAttribute(name));
		}
		// 获取session attributes，如果同名的值已经存在，则覆盖。
		session = request.getSession(false);
		if (session != null) {
			Enumeration<?> sessionAttrs = session.getAttributeNames();
			while (sessionAttrs.hasMoreElements()) {
				name = sessionAttrs.nextElement().toString();
				if (!"sys.simpleListener".equals(name)
						&& !"sys.userAgent".equals(name)
						&& !"sys.out".equals(name))
					json.put(name, session.getAttribute(name));
			}
		}
		return json;
	}

	/**
	 * 替换指定文本中使用{#name#}语法引用的值。值取自HttpServletRequest中的属性和参数、
	 * HttpSession属性、Var变量值、Str字符串值。如果名称重复，优先级从高到低依次为：
	 * Var变量，Str字符串值，session属性，request属性，request参数。
	 * @param request 请求对象。如果为null，直接返回text。
	 * @param text 需要进行替换的文本。
	 * @return 替换值后的文本。
	 */
	public static String replaceParams(HttpServletRequest request, String text) {
		if (request == null)
			return text;
		int start = 0, startPos = text.indexOf("{#", start), endPos = text
				.indexOf("#}", startPos + 2);

		if (startPos != -1 && endPos != -1) {
			String paramName, paramValue;
			StringBuilder buf = new StringBuilder(text.length());

			while (startPos != -1 && endPos != -1) {
				paramName = text.substring(startPos + 2, endPos);
				if (paramName.startsWith("Var."))
					paramValue = Var.getString(paramName.substring(4));
				else if (paramName.startsWith("Str."))
					paramValue = Str.format(request, paramName.substring(4));
				else
					paramValue = WebUtil.fetch(request, paramName);
				buf.append(text.substring(start, startPos));
				if (paramValue != null)
					buf.append(paramValue);
				start = endPos + 2;
				startPos = text.indexOf("{#", start);
				endPos = text.indexOf("#}", startPos + 2);
			}
			buf.append(text.substring(start));
			return buf.toString();
		} else
			return text;
	}

	/**
	 *发送指定对象数据至客户端，并立即提交。如果缓冲区数据已经提交则该方法没有任何效果。
	 *发送的对象根据类型分为InputStream类型数据和非InputStream类型数据。前者根据系统变量
	 *sys.sendStreamGzip决定是否使用gzip压缩输出；后者如果是非字节数组对象先转换成字符串，
	 *然后再转换成utf-8编码的字节数组，并根据系统变量sys.sendGzipMinSize决定是否使用gzip
	 *压缩输出。非InputStream类型数据如果没有使用gzip压缩，设置response的contentLength为
	 *字节长度，如果为字符串类数据设置头信息内容为"text/html;charset=utf-8"。如果启用压缩
	 *不能设置response对象的contentLength。
	 * @param response 响应对象，数据将发送到该对象输出流中。
	 * @param object 发送的对象。
	 * @throws IOException 输出过程中发生异常。
	 */
	public static void send(HttpServletResponse response, Object object)
			throws IOException {
		if (response.isCommitted())
			return;
		OutputStream outputStream = response.getOutputStream();
		if (object instanceof InputStream) {
			// 处理流数据
			InputStream inputStream = (InputStream) object;
			try {
				if (Var.sendStreamGzip) {
					response.setHeader("Content-Encoding", "gzip");
					GZIPOutputStream gos = new GZIPOutputStream(outputStream);
					try {
						IOUtils.copy(inputStream, gos);
					} finally {
						gos.close();
					}
				} else {
					IOUtils.copy(inputStream, outputStream);
				}
			} finally {
				inputStream.close();
			}
		} else {
			// 处理非流类数据
			int len;
			byte[] bytes;
			if (object instanceof byte[])
				bytes = (byte[]) object;
			else {
				String text;
				if (object == null)
					text = "";
				else
					text = object.toString();
				bytes = text.getBytes("utf-8");
				if (StringUtil.isEmpty(response.getContentType()))
					response.setContentType("text/html;charset=utf-8");
			}
			len = bytes.length;
			if (len >= Var.sendGzipMinSize && Var.sendGzipMinSize != -1) {
				response.setHeader("Content-Encoding", "gzip");
				GZIPOutputStream gos = new GZIPOutputStream(outputStream);
				try {
					gos.write(bytes);
				} finally {
					gos.close();
				}
			} else {
				response.setContentLength(len);
				outputStream.write(bytes);
			}
		}
		response.flushBuffer();
	}

	/**
	 * 发送以特定JSON格式封装的文本数据至客户端，并立即提交。如果缓冲区数据已经提交则该
	 *方法没有任何效果。该方法通常用于响应带有文件上传的请求。输出文本时将设置头信息内容
	 *长度为文本字节长度，头信息内容格式为"text/html;charset=utf-8"。
	 * @param response 响应对象，数据将发送到该对象输出流中。
	 * @param text 发送的文本。文本内容将被转换为html格式。
	 * @param successful 是否成功状态标志。
	 * @throws IOException 输出过程中发生异常。
	 */
	public static void send(HttpServletResponse response, String text,
			boolean successful) throws IOException {
		WebUtil.send(response, StringUtil.textareaQuote(StringUtil.concat(
				"{success:", Boolean.toString(successful), ",value:",
				StringUtil.quote(text), "}")));
	}

	/**
	 * 判断指定请求是否由Ajax发出。
	 * @param request 请求对象。
	 * @return true表示由Ajax发出，false其他。
	 */
	public static boolean fromAjax(HttpServletRequest request) {
		try {
			return "XMLHttpRequest".equalsIgnoreCase(request
					.getHeader("X-Requested-With"));
		} catch (Throwable e) {
			// 确保任何运行期异常也不发生错误
			return false;
		}
	}

	/**
	 * 显示异常信息。
	 * @param exception 异常信息对象。
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Throwable 如果异常未处理，则抛出异常交由服务器处理。
	 */
	public static void showException(Throwable exception,
			HttpServletRequest request, HttpServletResponse response)
			throws ServletException {
		boolean isAjax = fromAjax(request), jsonResp = jsonResponse(request);
		boolean directOutput = isAjax || jsonResp;
		String errorMessage;
		StringWriter writer = new StringWriter();
		PrintWriter pwriter = new PrintWriter(writer, true);

		exception.printStackTrace(pwriter);
		pwriter.close();
		errorMessage = writer.toString();
		Console.error(request, errorMessage);
		if (Var.printError && !directOutput) {
			Throwable rootExcept = SysUtil.getRootExcept(exception);
			if (rootExcept instanceof ServletException)
				throw (ServletException) rootExcept;
			else
				throw new ServletException(rootExcept);
		} else {
			try {
				if (Var.printError)
					System.err.println(errorMessage);
				if (!response.isCommitted()) {
					String rootError = SysUtil.getRootError(exception);
					response.reset();
					if (directOutput) {
						if (jsonResp) {
							WebUtil.send(response, rootError, false);
						} else {
							response
									.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
							WebUtil.send(response, rootError);
						}
					} else {
						response.sendError(
								HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
								rootError);
					}
				}
			} catch (Throwable e) {
				// 忽略
			}
		}
	}

	/**
	 * 检查当前请求的登录状态，如果请求需要登录则转到登录页面，否则发送401状态码。
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @return true已经成功登录，false没有登录。
	 * @throws Exception 检查登录过程发生异常。
	 */
	public static boolean checkLogin(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("sys.logined") == null) {
			boolean isAjax = fromAjax(request), jsonResp = jsonResponse(request);
			boolean directOutput = isAjax || jsonResp;

			if (directOutput) {
				if (jsonResp) {
					WebUtil.send(response, "$WBE201: Login required", false);
				} else {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					WebUtil.send(response, "Login required");
				}
			} else {
				Parser parser = new Parser(request, response);
				if (isTouchModule(request))
					parser.parse("sys/session/tlogin.xwl");
				else
					parser.parse("sys/session/login.xwl");
			}
			return false;
		} else {
			return true;
		}
	}

	/**
	 * 判断是否请求xwl移动应用模块。
	 * @param request 请求对象。
	 * @return 如果是返回true，否则返回false.
	 * @throws IOException
	 */
	private static boolean isTouchModule(HttpServletRequest request)
			throws IOException {
		String xwl = UrlBuffer.get(request.getServletPath());
		if (xwl.isEmpty()) {
			xwl = request.getParameter("xwl");
			if (StringUtil.isEmpty(xwl))
				return false;
			xwl = StringUtil.concat(xwl, ".xwl");
		}
		JSONObject module = XwlBuffer.get(xwl, true);
		if (module == null)
			return false;
		return module.has("hasTouch");
	}

	/**
	 * 判断当前请求的用户是否包含指定的角色。
	 * @param request 请求对象
	 * @param roleName 角色名称
	 * @return true包含角色，false未包含角色。
	 */
	public static boolean hasRole(HttpServletRequest request, String roleName) {
		String roles[] = (String[]) WebUtil.fetchObject(request, "sys.roles");
		return StringUtil.indexOf(roles, roleName) != -1;
	}

	/**
	 * 处理文件上传模式提交的参数，把上传的文件和参数存储到HttpServletRequest对象
	 *的attribute属性中，其中上传的文件以输入流对象的形式存储在attribute中。
	 * @param request 请求对象
	 * @return 上传的所有参数和文件列表。
	 * @throws Exception 处理过程发生异常。
	 */
	public static List<FileItem> setUploadFile(HttpServletRequest request)
			throws Exception {
		DiskFileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload(factory);
		List<FileItem> list;
		String fieldName, fileName, fileIndexText;
		long fileSize;
		final String uploadId = request.getParameter("uploadId");
		int maxKbSize = Var.getInt("sys.service.upload.maxSize");
		Integer fileIndex;
		HashMap<String, Integer> multiFilesMap = null;

		if (maxKbSize != -1)
			upload.setSizeMax(1024 * maxKbSize);
		factory.setSizeThreshold(Var.getInt("sys.service.upload.bufferSize"));
		if (uploadId != null && uploadId.indexOf('.') == -1) {
			request.setAttribute("sys.uploadId", uploadId);
			final HttpSession session = request.getSession(true);
			if (session != null) {
				upload.setProgressListener(new ProgressListener() {
					public void update(long read, long length, int id) {
						session.setAttribute("sys.upread." + uploadId, read);
						session.setAttribute("sys.uplen." + uploadId, length);
					}
				});
			}
		}
		list = upload.parseRequest(request);
		if (list == null || list.size() == 0)
			return null;
		try {
			for (FileItem item : list) {
				fieldName = item.getFieldName();
				// 外部request属性名称不允许带"."和"@"，防止注入系统变量
				if (fieldName.indexOf('.') != -1
						|| fieldName.indexOf('@') != -1)
					continue;
				if (item.isFormField()) {
					if (request.getAttribute(fieldName) != null)
						throw new RuntimeException("Duplicate parameters \""
								+ fieldName + "\" found.");
					request.setAttribute(fieldName, item.getString("utf-8"));
				} else {
					/*
					 * 没有上传文件值为""，上传文件值为InputStream类型，
					 * 可以使用WebUtil.hasFile方法判断文件域是否非空。
					 */
					fileName = FileUtil.getFilename(item.getName());
					fileSize = item.getSize();
					if (request.getAttribute(fieldName) != null) {
						// 单文件控件上传多个文件
						// 属性名称为itemId@index
						if (multiFilesMap == null)
							multiFilesMap = new HashMap<String, Integer>();
						fileIndex = multiFilesMap.get(fieldName);
						if (fileIndex == null)
							fileIndex = 0;
						fileIndex++;
						multiFilesMap.put(fieldName, fileIndex);
						fileIndexText = "@" + Integer.toString(fileIndex);
						if (StringUtil.isEmpty(fileName) && fileSize == 0)
							request.setAttribute(fieldName + fileIndexText, "");
						else
							// 已经上传文件，包括空文件
							request.setAttribute(fieldName + fileIndexText,
									item.getInputStream());
						request.setAttribute(StringUtil.concat(fieldName,
								fileIndexText, "__name"), fileName);
						request.setAttribute(StringUtil.concat(fieldName,
								fileIndexText, "__size"), fileSize);
					} else {
						if (StringUtil.isEmpty(fileName) && fileSize == 0)
							request.setAttribute(fieldName, "");
						else
							// 已经上传文件，包括空文件
							request.setAttribute(fieldName, item
									.getInputStream());
						request.setAttribute(fieldName + "__name", fileName);
						request.setAttribute(fieldName + "__size", fileSize);
					}
				}
			}
		} catch (Throwable e) {
			WebUtil.clearUploadFile(request, list);
			throw new Exception(e);
		}
		return list;
	}

	/**
	 * 判断上传的文件域是否为非空。
	 * @param request 请求对象。
	 * @param name 文件域名称。
	 * @return 如果存在上传的文件包括空文件则返回true，否则返回false。
	 */
	public static boolean hasFile(HttpServletRequest request, String name) {
		return request.getAttribute(name) instanceof InputStream;
	}

	/**
	 * 清除上传文件的资源，包括临时文件和标识上传进度的session属性。
	 * @param request 请求对象。
	 * @param list 上传的所有参数和文件列表。
	 */
	public static void clearUploadFile(HttpServletRequest request,
			List<FileItem> list) {
		Object object;
		for (FileItem item : list) {
			if (!item.isFormField() && !item.isInMemory()) {
				// 文件且非缓存项需要清除临时文件
				object = request.getAttribute(item.getFieldName());
				if (object instanceof InputStream)
					IOUtils.closeQuietly((InputStream) object);
				item.delete();
			}
		}
		String uploadId = (String) request.getAttribute("sys.uploadId");
		if (uploadId != null) {
			HttpSession session = request.getSession(true);
			session.removeAttribute("sys.upread." + uploadId);
			session.removeAttribute("sys.uplen." + uploadId);
		}
	}

	/**
	 * 获取当前客户端请求的语言种类。
	 * @param request 请求对象。
	 * @return 语言种类。
	 * @throws Exception
	 */
	public static void setLanguage(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		String sessionLan, setLan = Var.language;

		if (session != null) {
			sessionLan = (String) session.getAttribute("sys.lang");
			if (sessionLan != null)
				setLan = sessionLan;
		}
		if (setLan.equals("auto")) {
			setLan = Var.defaultLanguage;
			String acceptLang = request.getHeader("Accept-Language"), language, mappedLang;
			if (acceptLang != null) {
				int pos = acceptLang.indexOf(',');
				if (pos != -1)
					acceptLang = acceptLang.substring(0, pos);
				pos = acceptLang.indexOf(';');
				if (pos != -1)
					acceptLang = acceptLang.substring(0, pos);
				pos = acceptLang.indexOf('-');
				if (pos == -1)
					setLan = acceptLang.toLowerCase();
				else {
					language = StringUtil.concat(acceptLang.substring(0, pos)
							.toLowerCase(), "_", acceptLang.substring(pos + 1)
							.toUpperCase());
					mappedLang = Str.getMappedLang(language);
					if (mappedLang == null)
						setLan = language;
					else
						setLan = mappedLang;
				}
			}
		}
		request.setAttribute("sys.useLang", setLan);
	}

	/**
	 * 判断请求是否需要采用json格式返回响应数据。
	 * @param request 请求对象。
	 * @return true json格式，false其他。
	 */
	public static boolean jsonResponse(HttpServletRequest request) {
		return exists(request, "_jsonresp");
	}

	/**
	 * 判断请求对象中是否存在指定属性且其值为1或"1"。取值的优先顺序为session,attribute,
	 * 和parameter。如果参数名称以“sys.”为前缀，只判断request的attribute属性。
	 * @param request 请求对象。
	 * @param name 属性名称。
	 * @return true存在，false不存在。
	 */
	public static boolean exists(HttpServletRequest request, String name) {
		Object value;
		if (name.startsWith("sys."))
			value = request.getAttribute(name);// 防止读取外部参数，避免被注入内部参数
		else
			value = WebUtil.fetch(request, name);
		if (value == null)
			return false;
		else
			return "1".equals(value.toString());
	}

	/**
	 * 获取存储于特定Map中，通过setObject设置的对象。如果值不存在返回null。
	 * @param request 请求对象。
	 * @param name 存储的名称。
	 * @return request 指定名称的值或null。
	 */
	public static Object getObject(HttpServletRequest request, String name) {
		Object object = request.getAttribute("sys.varMap");
		if (object != null) {
			ConcurrentHashMap<String, Object> map = JSONObject
					.toConHashMap(object);
			return map.get(name);
		}
		return null;
	}

	/**
	 * 引用指定的请求。如果地址为xwl模块立即执行该xwl模块，其他请求地址使用
	 * include进行引用。
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @param path 请求路径，路径不能含有url参数。如果带url参数可使用Wrapper对request进行包装。
	 * 调用xwl模块可使用捷径或xwl文件路径，如"dbe"或"admin/dbe.xwl"。
	 * @throws Exception 调用过程发生异常。 
	 */
	public static void include(HttpServletRequest request,
			HttpServletResponse response, String path) throws Exception {
		doInclude(request, response, path, false);
	}

	/**
	 * 转发指定的请求。如果地址为xwl模块清空response缓冲区并立即执行该xwl模块，
	 * 其他请求地址使用include进行引用。
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @param path 请求路径，路径不能含有url参数。如果带url参数可使用Wrapper对request进行包装。
	 * 调用xwl模块可使用捷径或xwl文件路径，如"dbe"或"admin/dbe.xwl"。
	 * @throws Exception 调用过程发生异常。 
	 */
	public static void forward(HttpServletRequest request,
			HttpServletResponse response, String path) throws Exception {
		doInclude(request, response, path, true);
	}

	/**
	 * 引用或转发指定的请求。如果地址为xwl模块立即执行该xwl模块，其他请求地址使用
	 * forward或include进行引用或转发。
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @param path 请求路径，路径不能含有url参数。如果带url参数可使用Wrapper对request进行包装。
	 * @param isForward 指定是否使用include或forward。对于xwl模块两者之间的区别在于forward
	 * 前执行resetBuffer操作。
	 * @throws Exception 调用过程发生异常。 
	 */
	private static void doInclude(HttpServletRequest request,
			HttpServletResponse response, String path, boolean isForward)
			throws Exception {
		String xwl;

		if (path.endsWith(".xwl"))
			xwl = path;
		else
			xwl = UrlBuffer.get("/" + path);
		if (xwl == null) {
			// 非xwl模块
			if (isForward)
				request.getRequestDispatcher(path).forward(request, response);
			else
				request.getRequestDispatcher(path).include(request, response);
		} else {
			// xwl模块
			if (isForward)
				response.resetBuffer();
			if (xwl.isEmpty()) {
				xwl = request.getParameter("xwl");
				if (xwl == null) {
					response.sendError(HttpServletResponse.SC_BAD_REQUEST,
							"null xwl");
					return;
				}
				xwl = StringUtil.concat(xwl, ".xwl");
			}
			Parser parser = new Parser(request, response);
			parser.parse(xwl);
		}
	}

	/**
	 * 把对象存储于特定Map中，待请求周期结束，系统将自动关闭或释放该变量。该列表对象存储
	 *于request的attribute对象。如果相同的名称已经存在将抛出异常。
	 * @param request 请求对象。
	 * @param name 存储的名称。
	 * @param value 存储的值。值允许重复。
	 * @return request对象本身。
	 * @throws IllegalArgumentException 参数名称已经存在。
	 */
	public static void setObject(HttpServletRequest request, String name,
			Object value) {
		Object object = request.getAttribute("sys.varMap");
		if (object != null) {
			ConcurrentHashMap<String, Object> map = JSONObject
					.toConHashMap(object);
			if (map.containsKey(name))
				throw new IllegalArgumentException("Key \"" + name
						+ "\" already exists.");
			else
				map.put(name, value);
		}
	}
}
