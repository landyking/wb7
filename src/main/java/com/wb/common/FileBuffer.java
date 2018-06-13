package com.wb.common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

/**
 * WebBuilder缓存器，任何对wb目录下请求的资源都会被缓存，并视资源大小启用gzip压缩。
 */

public class FileBuffer {
	/** 把文件缓存到HashMap中，以提高文件访问性能。 */
	private static ConcurrentHashMap<String, Object[]> buffer;

	/**
	 * 为客户端提供高效的文件访问服务。
	 * 
	 * @param path
	 *            请求的文件路径。
	 * @param request
	 *            HttpServletRequest 请求对象。
	 * @param response
	 *            HttpServletResponse 响应对象。
	 * @throws IOException
	 *             如果执行过程中发生异常将抛出。
	 */
	public static void service(String path, HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		File file;
		Object[] obj;
		String pathKey;
		byte[] bt = null;
		boolean isGzip = false;
		long lastModified;

		pathKey = path.toLowerCase();
		if (Var.uncheckModified) {
			file = null;
			lastModified = -1;
		} else {
			file = new File(Base.path, path);
			lastModified = file.lastModified();
		}
		obj = buffer.get(pathKey);
		if (obj != null) {
			if (Var.uncheckModified || lastModified == (Long) obj[2]) {
				isGzip = (Boolean) obj[0];
				bt = (byte[]) obj[1];
				if (Var.uncheckModified)
					lastModified = (Long) obj[2];
			}
		}
		if (bt == null) {
			if (Var.uncheckModified) {
				file = new File(Base.path, path);
				lastModified = file.lastModified();
			}
			// 判断文件是否存在
			if (lastModified == 0) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND, path);
				return;
			}
			isGzip = file.length() >= Var.gzipMinSize;
			bt = getBytes(file, isGzip);
			obj = new Object[3];
			obj[0] = isGzip;
			obj[1] = bt;
			obj[2] = lastModified;
			buffer.put(pathKey, obj);
		}
		if (Var.cacheMaxAge != -1) {
			if (Var.cacheMaxAge == 0)
				response.setHeader("Cache-Control",
						"no-cache, no-store, max-age=0, must-revalidate");
			else {
				response.setDateHeader("Last-Modified", new Date().getTime());
				response.setHeader("Cache-Control", "max-age="
						+ Var.cacheMaxAge);
			}
		}
		String fileEtag = Long.toString(lastModified), reqEtag = request
				.getHeader("If-None-Match");
		if (fileEtag.equals(reqEtag)) {
			response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return;
		}
		response.setHeader("Etag", fileEtag);
		if (isGzip)
			response.setHeader("Content-Encoding", "gzip");
		response.setCharacterEncoding("utf-8");
		String contentType = Base.servletContext.getMimeType(path);
		if (contentType == null)
			contentType = "application/octet-stream";// 未知的下载类型
		response.setContentType(contentType);
		response.setContentLength(bt.length);
		response.getOutputStream().write(bt);
		response.flushBuffer();
	}

	/**
	 * 加载和初始化。
	 */
	public static synchronized void load() {
		buffer = new ConcurrentHashMap<String, Object[]>();
	}

	/**
	 * 获取文件的字节数组。
	 * 
	 * @param file
	 *            文件对象。
	 * @param isGzip
	 *            指定对文件是否使用gzip压缩。
	 * @return 以字节数组表达的文件数据。
	 * @throws IOException
	 *             如果读取文件时发生错误将抛出异常。
	 */
	private static byte[] getBytes(File file, boolean isGzip)
			throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		InputStream is = new FileInputStream(file);
		byte[] bt;

		try {
			if (isGzip) {
				GZIPOutputStream gos = new GZIPOutputStream(bos);
				try {
					IOUtils.copy(is, gos);
				} finally {
					gos.close();
				}
			} else
				IOUtils.copy(is, bos);
			bt = bos.toByteArray();
		} finally {
			is.close();
		}
		return bt;
	}
}
