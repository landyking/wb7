package com.wb.interact;

import java.io.File;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Base;
import com.wb.common.Session;
import com.wb.common.XwlBuffer;
import com.wb.util.DbUtil;
import com.wb.util.FileUtil;
import com.wb.util.JsonUtil;
import com.wb.util.SortUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;

public class Tools {
	/**
	 * 获取数据字典数据库表树数据。
	 */
	public static void getDictTree(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String jndi = request.getParameter("jndi");
		if (StringUtil.isEmpty(jndi)) {
			// 显示数据库
			WebUtil.send(response, DBE.getDbList());
		} else {
			// 显示表
			String tableName, dbName = request.getParameter("dbName");
			int dbNameLen;
			boolean hasDbName = !"default".equals(dbName), hasSchem = Boolean
					.parseBoolean(request.getParameter("hasSchem"));
			HashSet<String> tables = new HashSet<String>();
			ResultSet rs = (ResultSet) DbUtil.run(request,
					"select distinct TABLE_NAME from WB_DICT");
			dbName = dbName.toUpperCase() + '.';
			dbNameLen = dbName.length();
			while (rs.next()) {
				// 表名转换为大写，因为MySQL在某些OS上是小写，Oracle则是大写，
				// 因此DICT表名不区分大小写以统一不同数据库差异
				tableName = rs.getString(1).toUpperCase();
				// 表名带"."为其他数据库字典定义
				if (hasDbName) {
					if (tableName.startsWith(dbName))
						tables.add(tableName.substring(dbNameLen));
				} else if (tableName.indexOf('.') == -1)
					tables.add(tableName);
			}
			WebUtil.send(response, DBE.getTableList(jndi, hasSchem, tables));
		}
	}

	/**
	 * 获取所有键名组成的数组，并设置到request attribute名为keyNameList为属性中。
	 */
	public static void loadKeyNames(HttpServletRequest request,
			HttpServletResponse response) {
		boolean isFirst = true;
		StringBuilder buf = new StringBuilder();
		ArrayList<Entry<String, ConcurrentHashMap<Object, String>>> keys = SortUtil
				.sortKey(com.wb.common.KVBuffer.buffer);

		buf.append("[");
		for (Entry<String, ConcurrentHashMap<Object, String>> key : keys) {
			if (isFirst)
				isFirst = false;
			else
				buf.append(",");
			buf.append(StringUtil.quote(key.getKey()));
		}
		buf.append("]");
		request.setAttribute("keyNameList", StringUtil.quote(buf.toString(),
				false));
	}

	/**
	 * 获取所有模块的角色信息。
	 */
	public static void getModulesPerm(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject perms = new JSONObject();
		scanModulePerm(Base.modulePath, perms);
		WebUtil.send(response, perms);
	}

	/**
	 * 对指定目录下的模块进行扫描，并获取这些模块的权限信息至JSONObject。
	 * @param path 路径。
	 * @param perms 获取的模块角色列表。
	 */
	private static void scanModulePerm(File path, JSONObject perms)
			throws Exception {
		File files[] = FileUtil.listFiles(path);
		JSONObject jo;
		JSONObject roles;
		String filename;

		for (File file : files) {
			if (file.isDirectory()) {
				scanModulePerm(file, perms);
			} else {
				filename = file.getName();
				if (!filename.endsWith(".xwl"))
					continue;
				jo = JsonUtil.readObject(file);
				roles = (JSONObject) jo.opt("roles");
				perms.put(FileUtil.getModulePath(file), roles);
			}
		}
	}

	/**
	 * 删除所有模块中的指定角色信息。
	 */
	public static void delModulesPerm(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONArray destroy = new JSONArray(request.getParameter("destroy"));
		int i, j = destroy.length();
		if (j == 0)
			return;
		String[] roles = new String[j];

		for (i = 0; i < j; i++)
			roles[i] = destroy.getJSONObject(i).getString("ROLE_ID");
		doDelPerm(Base.modulePath, roles);
	}

	/**
	 * 对指定目录下的模块进行扫描，并删除模块文件内指定的角色信息。
	 * @param path 路径。
	 * @param roles 删除的模块角色列表。
	 */
	private static void doDelPerm(File path, String[] delRoles)
			throws Exception {
		File files[] = FileUtil.listFiles(path);
		String filename;

		for (File file : files) {
			if (file.isDirectory()) {
				doDelPerm(file, delRoles);
			} else {
				filename = file.getName();
				if (filename.endsWith(".xwl"))
					IDE.updateModule(file, null, delRoles, false);
			}
		}
	}

	/**
	* 设置指定模块列表的角色信息。
	*/
	public static void setModulesPerm(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String[] roles = new String[1], userRoles = Session.getRoles(request);
		boolean checked = Boolean.parseBoolean(request.getParameter("checked"));
		JSONArray pathList = new JSONArray(request.getParameter("path"));
		int i, j = pathList.length();
		File file;

		roles[0] = request.getParameter("role");
		for (i = 0; i < j; i++) {
			file = new File(Base.modulePath, pathList.getString(i));
			// 禁止无权限的用户设置该权限
			if (!file.isDirectory()) {
				if (!XwlBuffer.canAccess(XwlBuffer.get(FileUtil
						.getModulePath(file), false), userRoles))
					SysUtil.accessDenied();
				IDE.updateModule(file, null, roles, checked);
			}
		}
	}
}
