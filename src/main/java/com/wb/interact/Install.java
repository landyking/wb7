package com.wb.interact;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Base;
import com.wb.common.ScriptBuffer;
import com.wb.common.UrlBuffer;
import com.wb.common.Var;
import com.wb.tool.TaskManager;
import com.wb.util.DateUtil;
import com.wb.util.DbUtil;
import com.wb.util.FileUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;

public class Install {
	/**
	 * 检查当前系统是否允许安装，如果不允许安装则抛出异常。
	 */
	public static void checkInstall(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		if (!Var.getBool("sys.service.allowInstall"))
			throw new RuntimeException(
					"系统已经安装完成，如需重新安装请设置变量sys.service.allowInstall为true。");
	}

	/**
	 * 立即生成JNDI数据源配置文件。
	 */
	public static void generateJndi(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		checkInstall(request, response);
		String overwrite = request.getParameter("overwrite");
		if (overwrite == null)
			throw new NullPointerException("overwrite 参数为null");
		char dbType = request.getParameter("dbType").charAt(0);
		String contextFilename = null, libFilename = null;
		switch (dbType) {
		case 's':
			contextFilename = "s-context.xml";
			libFilename = "sqljdbc4.jar";
			break;
		case 'm':
			contextFilename = "m-context.xml";
			libFilename = "mysql51.jar";
			break;
		case 'o':
			contextFilename = "o-context.xml";
			libFilename = "ojdbc6.jar";
			break;
		}
		File metaFolder = new File(Base.path, "META-INF"), contextFile = new File(
				metaFolder, "context.xml"), rootFolder, syncFolder, libFolder;
		if (!metaFolder.exists() && !metaFolder.mkdir())
			throw new IOException("创建META-INF目录失败。");
		if (!Boolean.parseBoolean(overwrite) && contextFile.exists())
			SysUtil.error("文件 \"META-INF/context.xml\" 已经存在，确定要覆盖吗？", "106");
		rootFolder = Base.path.getParentFile().getParentFile();
		libFolder = new File(rootFolder, "lib");
		if (!new File(libFolder, libFilename).exists()) {
			FileUtils.copyFileToDirectory(new File(Base.path,
					"wb/system/database/" + libFilename), libFolder);
		}
		String xml = FileUtil.readString(new File(Base.path,
				"wb/system/database/" + contextFilename));
		xml = WebUtil.replaceParams(request, xml);
		FileUtil.writeString(contextFile, xml);
		syncFolder = new File(rootFolder, "conf/Catalina/localhost");
		if (!syncFolder.exists())
			syncFolder.mkdirs();
		FileUtil.writeString(
				new File(syncFolder, Base.path.getName() + ".xml"), xml);
	}

	/**
	 * 自动生成指定版本的软件包。
	 */
	public static synchronized void getPack(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		getReleasePath();// 测试发布目录是否存在。
		IDE.makeFile(request, Base.path, false);
		exportWBTables("r".equals(request.getParameter("type")));
		createPack(request, response);
	}

	/**
	 * 导出所有sqls.sql文件中定义的需要创建的数据库表数据至指定目录。
	 * @param userRelease 是否为用户发布版本。
	 */
	private static void exportWBTables(boolean userRelease) throws Exception {
		Connection conn = null;
		Statement st = null;
		ResultSet rs = null;
		BufferedWriter writer;
		int index;
		String withoutTables[] = { "WB_LOG", "WB_ROUTE", "WB_SN", "WB_SYS1",
				"WB_SYS2", "WB_VALUE" };
		String tableName, upperSQL, sqls[] = FileUtil.readString(
				new File(Base.path, "wb/system/database/sqls.sql")).split(";");
		File destTable, tablesPath = new File(Base.path,
				"wb/system/database/tables"), syncPath = FileUtil
				.getSyncPath(tablesPath);
		try {
			FileUtils.cleanDirectory(tablesPath);
			if (syncPath != null)
				FileUtils.cleanDirectory(syncPath);
			conn = DbUtil.getConnection();
			st = conn.createStatement();
			st.executeUpdate("update WB_USER set LOGIN_TIMES=0");
			for (String sql : sqls) {
				upperSQL = sql.toUpperCase();
				index = upperSQL.indexOf("CREATE TABLE");
				if (index != -1) {
					tableName = sql.substring(index + 13, sql.indexOf('('))
							.trim();
					if (!userRelease
							&& StringUtil.indexOf(withoutTables, tableName) != -1)
						continue;
					writer = null;
					rs = null;
					try {
						destTable = new File(tablesPath, tableName + ".dat");
						if (userRelease)
							rs = st.executeQuery("select * from " + tableName);
						else {
							if (tableName.equals("WB_USER"))
								rs = st
										.executeQuery("select * from WB_USER where USER_ID in ('admin','test')");
							else if (tableName.equals("WB_RESOURCE"))
								rs = st
										.executeQuery("select * from WB_RESOURCE where RES_ID in ('desktop@admin','desktop@test')");
							else
								rs = st.executeQuery("select * from "
										+ tableName);
						}
						writer = new BufferedWriter(new OutputStreamWriter(
								new FileOutputStream(destTable), "utf-8"));
						DbUtil.exportData(rs, writer);
						writer.close();
						writer = null;
						if (syncPath != null)
							FileUtils.copyFileToDirectory(destTable, syncPath);
					} finally {
						if (writer != null)
							writer.close();
						if (rs != null)
							rs.close();// 无需使用DbUtil.close
					}
				}
			}
		} finally {
			DbUtil.close(rs);
			DbUtil.close(st);
			DbUtil.close(conn);
		}
	}

	/**
	 * 测试JNDI是否有效。如果JNDI无效则抛出异常。
	 */
	public static void testJNDI(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		checkInstall(request, response);
		String jndi = request.getParameter("jndi");
		Var.set("sys.jndi.default", jndi);
		Var.jndi = jndi;
		DbUtil.getConnection().close();
	}

	/**
	 * 把相关的数据库表复制到目标数据库，并对相关参数进行设置和备份。
	 */
	public static void setup(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		checkInstall(request, response);
		String jndi = request.getParameter("jndiText");
		String type = request.getParameter("typeText");
		Var.set("sys.jndi.default", jndi);
		Var.jndi = jndi;
		if ("postgresql".equals(type)
				&& !"e".equals(Var.getString("sys.app.versionType")))
			throw new RuntimeException("postgresql数据库类型只有企业版本支持。");// 企业版本提供支持使用小写的postgresql数据库
		Connection conn = null;
		Statement st = null;
		int index;
		String tableName, sqls[];
		File mapJson = new File(Base.path, "wb/system/map.json");
		File tablePath = new File(Base.path, "wb/system/database/tables");
		JSONArray mapArray;
		JSONObject jo;
		File file;

		try {
			mapArray = JsonUtil.readArray(mapJson);
			jo = new JSONObject();
			jo.put("var.json", FileUtil.readString(Var.file));
			FileUtil.syncSave(mapJson, mapArray.put(jo).toString());
			sqls = replaceSQLMacro(
					FileUtil.readString(new File(Base.path,
							"wb/system/database/sqls.sql")), type).split(";");
			conn = DbUtil.getConnection();
			st = conn.createStatement();
			for (String sql : sqls) {
				index = sql.indexOf("CREATE TABLE");
				if (index == -1)
					st.executeUpdate(sql.trim());
				else {
					tableName = sql.substring(index + 13, sql.indexOf('('))
							.trim();
					try {
						st.executeUpdate("DROP TABLE " + tableName);
					} catch (Throwable e) {
						// 忽略，因为有可能指定表不存在。
					}
					st.executeUpdate(sql.trim());
					file = new File(tablePath, StringUtil.concat(tableName,
							".dat"));
					if (file.exists()) {
						DbUtil.importData(conn, tableName, new BufferedReader(
								new InputStreamReader(
										new FileInputStream(file), "utf-8")));
					}
				}
			}
		} finally {
			DbUtil.close(st);
			DbUtil.close(conn);
		}
		Var.set("sys.db.defaultType", type);
		if ("d".equals(Var.getString("sys.app.versionType"))) {
			Var.set("sys.portal", "demo-index");
			UrlBuffer.buffer.put("/", "sys/portal/demo-index.xwl");
		} else {
			Var.set("sys.portal", "index");
			UrlBuffer.put("/", "sys/portal/index.xwl");
		}
		// 禁用安装服务，如需再次使用安装需设置该变量为true。
		Var.set("sys.service.allowInstall", false);
		// 安装后加载数据库相关类
		SysUtil.reload(3);
		// 安装后立即启动计划任务
		TaskManager.start();
	}

	/**
	 * 获取发布目录路径。
	 * @return 发布目录路径。
	 * @throws RuntimeException 发布目录未指定或目录不存在。
	 */
	private static File getReleasePath() {
		String releasePathVar = Var.getString("sys.ide.releasePath");
		if (releasePathVar.isEmpty())
			throw new RuntimeException("发布目录变量 \"sys.ide.releasePath\" 未指定。");
		File releasePath = new File(releasePathVar);
		if (!releasePath.exists())
			throw new RuntimeException("发布目录 \"" + releasePath + "\" 不存在。");
		return releasePath;
	}

	/**
	 * 创建软件包。
	 */
	private static void createPack(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		File releasePath = getReleasePath();
		String type = request.getParameter("type");
		boolean userReleaseVersion = "r".equals(type);// 用户发布版本
		File destPath = FileUtil
				.getUniqueFile(new File(releasePath, (userReleaseVersion ? Var
						.getString("sys.app.title") : ("wb7" + type))
						+ "(" + DateUtil.format(new Date(), "yyyy-MM-dd") + ")"));
		if (userReleaseVersion) {
			File files[] = FileUtil.listFiles(Base.path);
			for (File file : files) {
				FileUtils.copyDirectoryToDirectory(file, destPath);
			}
			File varFile = new File(destPath, "wb/system/var.json");
			JSONObject varObject = JsonUtil.readObject(varFile);
			((JSONArray) JsonUtil
					.getValue(varObject, "sys.db.defaultType", '.')).put(0, "");
			((JSONArray) JsonUtil.getValue(varObject, "sys.jndi.default", '.'))
					.put(0, "");
			((JSONArray) JsonUtil.getValue(varObject, "sys.ide.releasePath",
					'.')).put(0, "");
			((JSONArray) JsonUtil.getValue(varObject, "sys.ide.syncPath", '.'))
					.put(0, "");
			((JSONArray) JsonUtil.getValue(varObject, "sys.portal", '.')).put(
					0, "setup");
			((JSONArray) JsonUtil.getValue(varObject,
					"sys.service.allowInstall", '.')).put(0, true);
			FileUtil.writeString(varFile, varObject.toString(2));
		} else {
			File destWebInf = new File(destPath, "wb/WEB-INF");
			FileUtils.copyDirectoryToDirectory(new File(Base.path,
					"WEB-INF/lib"), destWebInf);
			FileUtils.copyFileToDirectory(
					new File(Base.path, "WEB-INF/web.xml"), destWebInf);
			FileUtils.copyDirectoryToDirectory(new File(Base.path, "wb"),
					new File(destPath, "wb"));
			FileUtils.copyDirectory(new File(Var
					.getString("sys.ide.sourcePath")), new File(destPath,
					"source"));
			FileUtils.copyFileToDirectory(new File(releasePath, "webbuilder-"
					+ Var.getString("sys.app.version") + ".jar"), new File(
					destPath, "wb/WEB-INF/lib"));
			FileUtils.copyDirectoryToDirectory(new File(releasePath, "misc"),
					destPath);
			FileUtils.copyDirectoryToDirectory(new File(releasePath,
					"docs/docs"), destPath);
			FileUtils.copyFileToDirectory(new File(releasePath, "api.html"),
					destPath);
			FileUtils.copyFileToDirectory(new File(releasePath, "manual.docx"),
					destPath);
			FileUtils.copyFileToDirectory(
					new File(releasePath, "license.html"), destPath);
			FileUtils.copyFileToDirectory(new File(releasePath, "readme.html"),
					destPath);
			File ssproto = new File(destPath,
					"wb/wb/system/database/ssproto.sql");
			if (!ssproto.delete())
				throw new RuntimeException("无法删除 \"" + ssproto + "\"。");
			String packJSPath = "wb/wb/system/pack.js";
			File packJS = new File(destPath, packJSPath);
			if (!packJS.delete())
				throw new RuntimeException("无法删除 \"" + packJS + "\"。");
			packJSPath = "wb/system/pack.js";
			packJS = new File(Base.path, packJSPath);
			request.setAttribute("destPath", destPath);
			ScriptBuffer.run(packJSPath, FileUtil.readString(packJS), request,
					response, packJSPath);
		}
		WebUtil.send(response, destPath);
	}

	/**
	 * 根据指定数据库类型替换SQL中的宏，把宏转换为指定数据库可识别的关键字。
	 * @param sql SQL语句。
	 * @param dbType 数据库类型。
	 * @return 转换后的SQL。
	 * @throws Exception 
	 */
	private static String replaceSQLMacro(String sql, String dbType)
			throws Exception {
		JSONObject object = JsonUtil.readObject(new File(Base.path,
				"wb/system/database/types.json"));
		JSONObject items = object.getJSONObject(dbType);
		Set<Entry<String, Object>> es = items.entrySet();

		for (Entry<String, Object> entry : es) {
			sql = StringUtil.replaceAll(sql, "{#" + entry.getKey() + "#}",
					entry.getValue().toString());
		}
		return sql;
	}

	/**
	 * 把产品注册信息写入到系统变量。
	 */
	public static void register(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		Var.set("sys.app.licensee", request.getParameter("licText"));
		Var.set("sys.app.serialNumber", request.getParameter("snText"));
	}

	/**
	 * 在线验证指定序列号是否有效。
	 */
	public static void verify(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String sn, msg;
		try {
			sn = request.getParameter("snText");
			msg = WebUtil.submit("http://www.putdb.com/sn-verify",
					new JSONObject().put("sn", sn));
		} catch (Throwable e) {
			throw new RuntimeException("验证失败，网络无效或服务暂时不可访问。");
		}
		if (!"ok".equals(msg))
			throw new RuntimeException("验证失败，序列号 “" + sn + "” 无效。");
	}
}