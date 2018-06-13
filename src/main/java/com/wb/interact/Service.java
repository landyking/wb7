package com.wb.interact;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;

import com.wb.common.Base;
import com.wb.common.Resource;
import com.wb.common.Value;
import com.wb.tool.DataOutput;
import com.wb.util.DbUtil;
import com.wb.util.FileUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;
import com.wb.util.ZipUtil;

/**
 * 提供可交互的系统Web服务。
 */
public class Service {
	/**
	 * 获取当前用户指定名称的值。name参数为值名称。值将转换为字符串类型返回。
	 * @see Resource
	 */
	public static void getValue(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		getData(request, response, false);
	}

	/**
	 * 获取当前用户指定名称的资源。name参数为资源名称。资源将转换为字符串类型返回。
	 * @see Resource
	 */
	public static void getResource(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		getData(request, response, true);
	}

	/**
	 * 获取当前用户指定类型数据。
	 * @param request 请求对象，name参数为需要获取数据的名称。
	 * @param response 响应对象，获取数据后直接输出。
	 * @param isResource 值是否为资源，true资源，false值。
	 * @see Value
	 * @see Resource
	 */
	private static void getData(HttpServletRequest request,
			HttpServletResponse response, boolean isResource) throws Exception {
		String name = request.getParameter("name"), value;
		if (isResource)
			value = Resource.getString(request, name, null);
		else
			value = Value.getString(request, name, null);
		WebUtil.send(response, value);
	}

	/**
	 * 下载指定路径的文件或目录。接受参数files：文件或目录路径列表；zip：是否启用zip压缩，
	 *如果下载的是目录或多个文件将自动设置为true；downloadName：下载时使用的文件名称。
	 * @param request 请求对象
	 * @param response 响应对象
	 * @param webFilesOnly 是否仅允许下载webapps目录下的非系统文件。
	 * @throws Exception 下载受限制的文件或读取文件发生异常。
	 */
	private static void download(HttpServletRequest request,
			HttpServletResponse response, boolean webFilesOnly)
			throws Exception {
		JSONArray ja = new JSONArray(WebUtil.fetch(request, "files"));
		int i, j = ja.length();
		File base = null, sysFolder = null, files[] = new File[j];
		boolean useZip;
		String filename, downloadName;

		if (webFilesOnly) {
			base = new File(Base.path, "wb");
			sysFolder = new File(Base.path, "wb/system");
		}
		for (i = 0; i < j; i++) {
			if (webFilesOnly) {
				files[i] = new File(Base.path, ja.optString(i));
				if (!FileUtil.isAncestor(base, files[i])
						|| FileUtil.isAncestor(sysFolder, files[i])
						|| FileUtil.isAncestor(Base.modulePath, files[i]))
					SysUtil.accessDenied();
			} else
				files[i] = new File(ja.optString(i));
		}
		useZip = StringUtil.getBool(WebUtil.fetch(request, "zip")) || j > 1
				|| files[0].isDirectory();
		downloadName = WebUtil.fetch(request, "downloadName");
		if (StringUtil.isEmpty(downloadName)) {
			filename = files[0].getName();
			if (j == 1) {
				if (useZip)
					filename = FileUtil.removeExtension(filename) + ".zip";
			} else {
				File parentFile = files[0].getParentFile();
				if (parentFile == null)
					filename = "file.zip";
				else
					filename = parentFile.getName() + ".zip";
			}
			if (filename.equals(".zip") || filename.equals("/.zip"))
				filename = "file.zip";
		} else
			filename = downloadName;
		response.setHeader("content-type", "application/force-download");
		response.setHeader("content-disposition", "attachment;"
				+ WebUtil.encodeFilename(request, filename));
		if (useZip) {
			ZipUtil.zip(files, response.getOutputStream());
			response.flushBuffer();
		} else
			WebUtil.send(response, new FileInputStream(files[0]));
	}

	/**
	 * 下载任意目录内指定路径的文件或目录。详细信息见download方法。
	 * @param request 请求对象
	 * @param response 响应对象
	 * @throws Exception 读取文件发生异常。
	 * @see Service#download
	 */
	public static void downloadAtAll(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		download(request, response, false);
	}

	/**
	 * 下载应用目录内指定路径的文件或目录。详细信息见download方法。
	 * @param request 请求对象
	 * @param response 响应对象
	 * @throws Exception 下载受限制的文件或读取文件发生异常。
	 * @see Service#download
	 */
	public static void downloadAtApp(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		download(request, response, true);
	}

	/**
	 * 上传文件至指定目录。参数：path上传路径，file文件流，[filename]文件名称，
	 * [unzip]是否解压缩zip文件。
	 * @param request 请求对象
	 * @param response 响应对象
	 * @throws Exception 上传过程发生异常。
	 */
	public static void upload(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		boolean unzip = StringUtil.getBool((String) request
				.getAttribute("unzip"));
		boolean sync = StringUtil
				.getBool((String) request.getAttribute("sync"));
		String path = request.getAttribute("path").toString();
		InputStream stream = (InputStream) request.getAttribute("file");
		String filename = (String) request.getAttribute("filename");
		File destPath, syncPath;

		if (filename == null)
			filename = (String) request.getAttribute("file__name");
		if (unzip) {
			if (filename.toLowerCase().endsWith(".zip")) {
				destPath = new File(path);
				ZipUtil.unzip(stream, destPath);
				if (sync) {
					syncPath = FileUtil.getSyncPath(destPath);
					if (syncPath != null)
						FileUtils.copyDirectory(destPath, syncPath);
				}
			} else
				throw new Exception("Invalid zip file.");
		} else {
			destPath = new File(path, filename);
			FileUtil.saveStream(stream, destPath);
			if (sync) {
				syncPath = FileUtil.getSyncPath(destPath);
				if (syncPath != null)
					FileUtils.copyFile(destPath, syncPath);
			}
		}
	}

	/**
	 * 获取指定id号进度的当前位置信息。
	 * @param request 请求对象
	 * @param response 响应对象
	 * @throws Exception 读取进度发生异常。
	 */
	public static void getProgress(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String id = request.getParameter("progressId");
		HttpSession session = request.getSession(true);
		Long pos = (Long) session.getAttribute("sys.upread." + id);
		Long len = (Long) session.getAttribute("sys.uplen." + id);
		double result;

		if (pos == null || len == null || len == 0)
			result = 0;
		else
			result = (double) pos / (double) len;
		WebUtil.send(response, result);
	}

	/**
	 * 导入使用gzip压缩的JSON格式文件数据至指定数据库表。
	 * @param request 请求对象
	 * @param response 响应对象
	 * @throws Exception 导入发生异常。
	 */
	public static void importData(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		boolean fromServer = StringUtil.getBool(WebUtil.fetch(request,
				"fromServer"));
		String filename;
		InputStream stream;
		if (fromServer) {
			filename = WebUtil.fetch(request, "filename");
			stream = new FileInputStream(new File(filename));
			filename = filename.toLowerCase();
		} else {
			filename = ((String) request.getAttribute("file__name"))
					.toLowerCase();
			stream = (InputStream) request.getAttribute("file");
		}
		try {
			String tableName = WebUtil.fetch(request, "table");
			BufferedReader reader;
			boolean trans = StringUtil.getBool(WebUtil.fetch(request, "trans"));
			Connection connection = DbUtil.getConnection(WebUtil.fetch(request,
					"jndi"));
			try {
				if (trans)
					connection.setAutoCommit(false);
				if (filename.endsWith(".gz")) {
					// JSON格式
					reader = new BufferedReader(new InputStreamReader(
							new GZIPInputStream(stream), "utf-8"));
					DbUtil.importData(connection, tableName, reader);
				} else {
					if (filename.endsWith(".xls") || filename.endsWith(".xlsx")) {
						// Excel格式
						reader = new BufferedReader(new StringReader(
								DataOutput.excelToJson(stream, filename
										.endsWith(".xlsx"))));
						DbUtil.importData(connection, tableName, reader);
					} else {
						// 文本格式
						reader = new BufferedReader(new InputStreamReader(
								stream, "utf-8"));
						String fields = reader.readLine(), fieldList[];
						char separator;
						if (fields.indexOf('\t') == -1)
							separator = ',';
						else
							separator = '\t';
						fieldList = StringUtil.split(fields, separator);
						DbUtil.importData(connection, tableName, reader,
								fieldList, separator);
					}
				}
				if (trans)
					connection.commit();
			} finally {
				DbUtil.close(connection);
			}
		} finally {
			stream.close();
		}
	}

	/**
	 * 导出指定数据库结果集至客户端使用gzip压缩的JSON格式文件。
	 * @param request 请求对象
	 * @param response 响应对象
	 * @throws Exception 导出发生异常。
	 */
	public static void exportJson(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		ResultSet rs = (ResultSet) DbUtil.run(request, WebUtil.fetch(request,
				"sql"), WebUtil.fetch(request, "jndi"));
		String filename = WebUtil.fetch(request, "filename");
		if (filename == null)
			filename = "data.gz";
		response.setHeader("content-type", "application/force-download");
		response.setHeader("content-disposition", "attachment;"
				+ WebUtil.encodeFilename(request, filename));
		Writer writer = new BufferedWriter(new OutputStreamWriter(
				new GZIPOutputStream(response.getOutputStream()), "utf-8"));
		try {
			DbUtil.exportData(rs, writer);
		} finally {
			writer.close();
		}
		response.flushBuffer();
	}

	/**
	 * 导出指定数据库结果集至服务器端使用gzip压缩的JSON格式文件。
	 * @param request 请求对象
	 * @param response 响应对象
	 * @throws Exception 导出发生异常。
	 */
	public static void exportJsonToServer(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		ResultSet rs = (ResultSet) DbUtil.run(request, WebUtil.fetch(request,
				"sql"), WebUtil.fetch(request, "jndi"));
		String filename = WebUtil.fetch(request, "filename");
		File file = new File(filename);
		if (file.exists())
			throw new Exception("文件 “" + filename + "” 已经存在。");
		FileOutputStream fos = null;
		Writer writer = null;
		try {
			fos = new FileOutputStream(file);
			writer = new BufferedWriter(new OutputStreamWriter(
					new GZIPOutputStream(fos), "utf-8"));
			DbUtil.exportData(rs, writer);
		} finally {
			IOUtils.closeQuietly(writer);
			fos.close();// 确保writer创建异常时fos被关闭
		}
	}
}
