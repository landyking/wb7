package com.wb.interact;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.compatible.CustomResponse;
import com.wb.tool.DataOutput;
import com.wb.util.FileUtil;
import com.wb.util.StringUtil;
import com.wb.util.WebUtil;

public class FilePush {
	/**
	 * 根据客户端推送的数据生成原始数据文件，并发送给客户端。
	 */
	public static void getFile(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String filename = WebUtil.fetch(request, "filename");
		String data = WebUtil.fetch(request, "data");
		boolean gzip = Boolean.parseBoolean(WebUtil.fetch(request, "gzip"));

		if (filename == null) {
			if (gzip)
				filename = "data.gz";
			else
				filename = "data";
		}
		response.setHeader("content-type", "application/force-download");
		response.setHeader("content-disposition", "attachment;"
				+ WebUtil.encodeFilename(request, filename));
		if (gzip) {
			GZIPOutputStream gos = new GZIPOutputStream(response
					.getOutputStream());
			try {
				gos.write(data.getBytes("utf-8"));
			} finally {
				gos.close();
			}
			response.flushBuffer();
		} else
			WebUtil.send(response, data);
	}

	/**
	 * 根据客户端推送的数据生成原始文本，并发送给客户端。
	 */
	public static void getText(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		WebUtil.send(response, request.getParameter("text"));
	}

	/**
	 * 根据客户端推送的数据在服务器端指定目录生成原始数据文件。
	 * @param request 请求对象。
	 * @param response 响应对象
	 * @throws Exception 生成文件过程发生异常。
	 */
	public static void writeFile(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String filename = WebUtil.fetch(request, "filename");
		String data = WebUtil.fetch(request, "data");
		boolean gzip = Boolean.parseBoolean(WebUtil.fetch(request, "gzip"));
		File file = new File(filename);
		if (file.exists())
			throw new Exception("文件 “" + filename + "” 已经存在。");
		if (gzip) {
			FileOutputStream fos = null;
			GZIPOutputStream gos = null;
			try {
				fos = new FileOutputStream(file);
				gos = new GZIPOutputStream(fos);
				gos.write(data.getBytes("utf-8"));
			} finally {
				IOUtils.closeQuietly(gos);
				fos.close();
			}
		} else
			FileUtil.writeString(file, data);
	}

	/**
	 * 对指定url使用include的方法发起请求，并把请求获取的数据按指定格式进行输出。
	 */
	public static void transfer(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String data, filename, fileExtName, respText;
		String type, title, dateFormat, timeFormat;
		String decimalSeparator, thousandSeparator;
		JSONObject responseObject, metaParams = new JSONObject(request
				.getParameter("__metaParams"));
		JSONArray headers, records;
		boolean isHtml = false, neptune;
		CustomResponse resp;

		type = metaParams.optString("type");
		if ("excel".equals(type)) {
			fileExtName = DataOutput.getExtName();
		} else if ("text".equals(type)) {
			fileExtName = ".txt";
		} else if ("html".equals(type)) {
			fileExtName = ".html";
			isHtml = true;
		} else
			throw new IllegalArgumentException("Invalid request file type.");
		data = metaParams.optString("data", null);// 获取客户端数据
		if (data == null) {
			// 如果客户端无数据（输出全部页时）重新获取服务端数据
			resp = new CustomResponse(response);
			request.setAttribute("sys.rowOnly", 1);// 标记仅生成rows数据
			request.setAttribute("sys.fromExport", 1);// 标记操作来自导出
			WebUtil.include(request, resp, metaParams.optString("url"));
			respText = getResponseString(resp);
			if (respText.startsWith("<textarea>")) {
				// json response格式
				data = respText.substring(10, respText.length() - 11);
				responseObject = new JSONObject(data);
				data = (String) responseObject.opt("value");
				if (!responseObject.optBoolean("success")) {
					WebUtil.send(response, data, false);
					return;
				}
			} else {
				// Ajax格式
				respText = respText.trim();
				if (!respText.startsWith("{") || !respText.endsWith("}")) {
					WebUtil.send(response, respText, false);
					return;
				}
				data = respText;
			}
			records = new JSONObject(data).getJSONArray("rows");
		} else
			records = new JSONArray(data);
		headers = metaParams.optJSONArray("headers");
		title = metaParams.optString("title", null);// 标题如果为null不输出
		dateFormat = metaParams.optString("dateFormat");// 默认日期格式
		timeFormat = metaParams.optString("timeFormat");// 默认时间格式
		neptune = metaParams.optBoolean("neptune");
		decimalSeparator = metaParams.optString("decimalSeparator");
		thousandSeparator = metaParams.optString("thousandSeparator");
		filename = metaParams.optString("filename");
		if (StringUtil.isEmpty(filename))
			filename = "data";
		filename += fileExtName;
		if (!isHtml) {
			// 非html下载文件
			response.setHeader("content-type", "application/force-download");
			response.setHeader("content-disposition", "attachment;"
					+ WebUtil.encodeFilename(request, filename));
		}
		if ("excel".equals(type)) {
			DataOutput.outputExcel(request, response.getOutputStream(),
					headers, records, title, dateFormat, timeFormat, neptune);
		} else if ("text".equals(type)) {
			DataOutput.outputText(request, response.getOutputStream(), headers,
					records, dateFormat, timeFormat, decimalSeparator,
					thousandSeparator);

		} else {// html预览
			DataOutput.outputHtml(request, response.getOutputStream(), headers,
					records, title, dateFormat, timeFormat, neptune, metaParams
							.getInt("rowNumberWidth"), metaParams
							.optString("rowNumberTitle"), decimalSeparator,
					thousandSeparator);
		}
		response.flushBuffer();
	}

	/**
	 * 获取使用include方法返回的Response对象数据。
	 * @param response 响应对象。
	 * @return 响应对象的输出数据。
	 */
	private static String getResponseString(CustomResponse response)
			throws Exception {
		byte data[] = response.getBytes();
		String result;

		if (data.length > 2 && data[0] == (byte) 0x1F && data[1] == (byte) 0x8B) {
			// 使用gzip压缩的数据
			InputStream is = new GZIPInputStream(new ByteArrayInputStream(data));
			try {
				result = StringUtil.getString(is);
			} finally {
				is.close();
			}
		} else
			result = new String(data, "utf-8");
		return result;
	}
}
