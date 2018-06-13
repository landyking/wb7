package com.wb.interact;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Base;
import com.wb.common.Resource;
import com.wb.common.Session;
import com.wb.common.Str;
import com.wb.common.Value;
import com.wb.common.XwlBuffer;
import com.wb.util.DbUtil;
import com.wb.util.FileUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;

/**
 * 门户及其相关的应用。
 */
public class Portal {
	/**
	 * 递归获取所有模块列表树的节点列表。
	 * @param request 请求对象。
	 * @param path 请求的路径。
	 * @param roles 角色列表。
	 * @param type 数据请求来源类型，1：主页，2：权限模块，3：用户模块。
	 */
	private static JSONArray getModuleList(HttpServletRequest request,
			String path, String[] roles, int type) throws Exception {
		String iconCls, fileName, pageLink, relPath, title;
		File file, base;
		int displayType = (type == 1 ? 1 : 3);

		if (path == null)
			base = Base.modulePath;
		else {
			base = new File(Base.modulePath, path);
			if (!FileUtil.isAncestor(Base.modulePath, base))
				SysUtil.accessDenied();
		}
		path = path + "/";
		ArrayList<Entry<String, Integer>> fileNames = IDE.getSortedFile(base);
		JSONObject content = null, fileObject;
		JSONArray fileArray;
		boolean isFolder;

		fileArray = new JSONArray();
		for (Entry<String, Integer> entry : fileNames) {
			fileName = entry.getKey();
			file = new File(base, fileName);
			// 文件列表取自配置文件，如果不存在返回
			if (!file.exists())
				continue;
			if (!XwlBuffer.canDisplay(file, roles, displayType))
				continue;
			isFolder = file.isDirectory();
			if (isFolder) {
				if (type != 1 && !hasLoginModule(file))
					continue;
				File configFile = new File(file, "folder.json");
				if (configFile.exists())
					content = JsonUtil.readObject(configFile);
				else
					content = new JSONObject();
			} else {
				// 如果设置模块权限且非模块文件则返回
				if (type != 1 && !file.getName().endsWith(".xwl"))
					continue;
				content = XwlBuffer.get(path + fileName, false);
				// 无需登录的模块不允许设置权限
				if (type != 1
						&& Boolean.FALSE.equals(content.opt("loginRequired")))
					continue;
			}
			fileObject = new JSONObject();
			title = content.optString("title");
			if (title.startsWith("Str."))
				title = Str.format(request, title.substring(4));
			fileObject.put("text", StringUtil.select(title, fileName));
			relPath = FileUtil.getModulePath(file);
			fileObject.put("path", relPath);
			fileObject.put("fileName", fileName);
			fileObject.put("inframe", Boolean.TRUE.equals(content
					.opt("inframe")));
			if (isFolder) {
				fileObject.put("children", getModuleList(request, relPath,
						roles, type));
			} else {
				pageLink = (String) content.opt("pageLink");
				if (!StringUtil.isEmpty(pageLink))
					fileObject.put("pageLink", pageLink);
				fileObject.put("leaf", true);
			}
			fileObject.put("cls", "wb_pointer");
			iconCls = content.optString("iconCls");
			if (!StringUtil.isEmpty(iconCls))
				fileObject.put("iconCls", iconCls);
			if (type == 2 && !isFolder)
				fileObject.put("checked", false);
			fileArray.put(fileObject);
		}
		return fileArray;
	}

	/**
	 * 判断指定目录下是否具有需要登录的模块
	 * @param path 目录路径。
	 * @return true存在需要登录的模块，false不存在需要登录的模块。
	 * @throws IOException 
	 */
	private static boolean hasLoginModule(File path) throws IOException {
		File[] files = FileUtil.listFiles(path);

		for (File file : files) {
			if (file.isDirectory()) {
				if (hasLoginModule(file))
					return true;
			} else {
				if (file.getName().endsWith(".xwl")) {
					JSONObject content = XwlBuffer.get(FileUtil
							.getModulePath(file), true);
					if (Boolean.TRUE.equals(content.opt("loginRequired")))
						return true;
				}
			}
		}
		return false;
	}

	/**
	 *  初始化主页。
	 */
	public static void initHome(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String desktopString = Resource.getString(request, "desktop", null);
		int treeWidth, viewIndex;
		boolean treeCollapsed, treeHidden;

		if (desktopString == null)
			desktopString = Resource.getString("sys.home.desktop", null);
		if (desktopString != null) {
			JSONObject desktop = new JSONObject(desktopString);
			treeWidth = desktop.optInt("treeWidth", 200);
			viewIndex = desktop.optInt("viewIndex", 0);
			treeCollapsed = desktop.optBoolean("treeCollapsed", false);
			treeHidden = desktop.optBoolean("treeHidden", false);
			JSONArray pages = desktop.optJSONArray("pages");
			if (pages != null) {
				int i, j = pages.length(), activeIndex = desktop.optInt(
						"active", 0);
				String url, title, pageLink, params, rawUrl;
				JSONObject page, module, item;
				JSONArray tabItems = new JSONArray();
				for (i = 0; i < j; i++) {
					page = pages.optJSONObject(i);
					rawUrl = page.optString("url");
					url = FileUtil.getModuleFile(rawUrl, true);
					module = XwlBuffer.get(url, true);
					if (module == null) {
						if (i <= activeIndex)
							activeIndex--;
					} else {
						item = new JSONObject();
						item.put("url", rawUrl);
						title = (String) module.opt("title");
						if (title.startsWith("Str."))
							title = Str.format(request, title.substring(4));
						item.put("title", StringUtil.select(title, FileUtil
								.getFilename(url)));
						item.put("iconCls", (String) module.opt("iconCls"));
						item.put("useIFrame", Boolean.TRUE.equals(module
								.opt("inframe")));
						params = page.optString("params");
						if (!StringUtil.isEmpty(params))
							item.put("params", new JSONObject(params));
						pageLink = (String) module.opt("pageLink");
						if (!StringUtil.isEmpty(pageLink))
							JsonUtil.apply(item, new JSONObject(pageLink));
						tabItems.put(item);
					}
				}
				request.setAttribute("activeIndex", activeIndex);
				request.setAttribute("tabItems", StringUtil.text(tabItems
						.toString()));
			}
			JSONArray portlets = desktop.optJSONArray("portlets");
			if (portlets != null) {
				int i, j = portlets.length(), k, l;
				String url, title, pageLink;
				JSONArray cols;
				JSONObject portlet, module;
				for (i = 0; i < j; i++) {
					cols = portlets.optJSONArray(i);
					l = cols.length();
					for (k = 0; k < l; k++) {
						portlet = cols.optJSONObject(k);
						url = FileUtil.getModuleFile(portlet.optString("url"),
								true);
						module = XwlBuffer.get(url, true);
						if (module == null) {
							cols.remove(k);
							k--;
							l--;
							continue;
						}
						title = (String) module.opt("title");
						if (title.startsWith("Str."))
							title = Str.format(request, title.substring(4));
						portlet.put("title", StringUtil.select(title, FileUtil
								.getFilename(url)));
						portlet.put("iconCls", (String) module.opt("iconCls"));
						portlet.put("useIFrame", Boolean.TRUE.equals(module
								.opt("inframe")));
						pageLink = (String) module.opt("pageLink");
						if (!StringUtil.isEmpty(pageLink))
							JsonUtil.apply(portlet, new JSONObject(pageLink));
					}
				}
				request.setAttribute("portlets", StringUtil.text(portlets
						.toString()));
			}
		} else {
			treeWidth = 200;
			viewIndex = 0;
			treeCollapsed = false;
			treeHidden = false;
		}
		request.setAttribute("treeWidth", treeWidth);
		request.setAttribute("viewIndex", viewIndex);
		request.setAttribute("treeCollapsed", treeCollapsed);
		request.setAttribute("treeHidden", treeHidden);
		request.setAttribute("hideSetDefaultDesktop", !XwlBuffer.canAccess(
				request, "m?xwl=sys/portal/home/save-default-desktop"));
		request.setAttribute("hideSetAllDesktop", !XwlBuffer.canAccess(request,
				"m?xwl=sys/portal/home/save-all-desktop"));
	}

	/**
	 *  保存当前用户桌面。
	 */
	public static void saveDesktop(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		doSaveDesktop(request, 1);
	}

	/**
	 *  保存当前桌面为默认桌面。
	 */
	public static void saveAsDefaultDesktop(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		doSaveDesktop(request, 2);
	}

	/**
	 *  保存当前桌面为所有用户桌面。
	 */
	public static void saveAsAllDesktop(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		doSaveDesktop(request, 3);
	}

	/**
	 * 保存桌面信息。
	 * @param request 请求对象。
	 * @param type 保存类型，1当前用户桌面，2默认桌面，3所有桌面
	 */
	private static void doSaveDesktop(HttpServletRequest request, int type)
			throws Exception {
		JSONObject desktop = new JSONObject();
		desktop.put("treeWidth", Integer.parseInt(request
				.getParameter("treeWidth")));
		desktop.put("viewIndex", Integer.parseInt(request
				.getParameter("viewIndex")));
		desktop.put("treeCollapsed", Boolean.parseBoolean(request
				.getParameter("treeCollapsed")));
		desktop.put("treeHidden", Boolean.parseBoolean(request
				.getParameter("treeHidden")));
		desktop.put("pages", new JSONArray(request.getParameter("pages")));
		desktop
				.put("portlets",
						new JSONArray(request.getParameter("portlets")));
		desktop.put("active", Integer.parseInt(request.getParameter("active")));
		if (type == 1) {
			Resource.set(request, "desktop", desktop.toString());
		} else {
			if (type == 3)
				DbUtil
						.run(request,
								"delete from WB_RESOURCE where RES_ID like 'desktop@%'");
			Resource.set("sys.home.desktop", desktop.toString());
		}
	}

	/**
	 *  获取主页应用列表树的节点列表。
	 */
	public static void getAppList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		WebUtil.send(response, new JSONObject().put("children", getModuleList(
				request, request.getParameter("path"), Session
						.getRoles(request), 1)));
	}

	/**
	 *  获取权限模块列表树的节点列表。
	 */
	public static void getPermList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		WebUtil.send(response, new JSONObject().put("children", getModuleList(
				request, request.getParameter("path"), Session
						.getRoles(request), 2)));
	}

	/**
	 *  获取用户模块权限模块列表树的节点列表。
	 */
	public static void getUserPermList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		WebUtil.send(response, new JSONObject().put("children", getModuleList(
				request, request.getParameter("path"), Session
						.getRoles(request), 3)));
	}

	/**
	 *  设置桌面应用界面方案。
	 */
	public static void setTheme(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String theme = request.getParameter("theme");

		Value.set(request, "theme", theme);
		WebUtil.setSessionValue(request, "sys.theme", theme);
	}

	/**
	 *  设置移动应用界面方案。
	 */
	public static void setTouchTheme(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String theme = request.getParameter("theme");

		Value.set(request, "touchTheme", theme);
		WebUtil.setSessionValue(request, "sys.touchTheme", theme);
	}

	/**
	 *  初始化移动应用首页模块。
	 */
	public static void initTouchHome(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		HttpSession session = request.getSession(false);
		boolean isNotLogin = session == null
				|| session.getAttribute("sys.logined") == null;
		request.setAttribute("isNotLogin", isNotLogin ? 1 : 0);
	}

	/**
	 * 搜索模块目录下指定名称的模块。
	 */
	private static void searchModule(HttpServletRequest request,
			HttpServletResponse response, boolean isPerm) throws Exception {
		JSONArray array = new JSONArray();
		String query = request.getParameter("query").toLowerCase();
		String[] roles = Session.getRoles(request);
		if (query.isEmpty())
			query = ".xwl";
		doSearchFile(request, Base.modulePath, query.toLowerCase(), "", "",
				array, isPerm, roles);
		WebUtil.send(response, new JSONObject().put("rows", array));
	}

	/**
	 * 搜索主页模块目录下指定名称的模块。
	 */
	public static void searchAppModule(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		searchModule(request, response, false);
	}

	/**
	 * 搜索权限模块目录下指定名称的模块。
	 */
	public static void searchPermModule(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		searchModule(request, response, true);
	}

	/**
	 * 
	 * 在指定目录下搜索文件。内部函数，用于递归搜索。
	 * @param request 请求对象。
	 * @param folder 目录。
	 * @param searchName 查找的文件名称关键字，任何包括该关键字的文件均将被列举。
	 * @param parentText 上级目录显示名称。
	 * @param array 用于存放搜索结果。
	 * @param isPerm 是否来自权限设置模块。
	 * @param roles 当前用户角色列表。
	 * @return 是否完成搜索，如果搜索结果大于等于100项，将返回true，否则返回false。
	 * @throws Exception 搜索过程中发生异常。
	 */
	private static boolean doSearchFile(HttpServletRequest request,
			File folder, String searchName, String parentText,
			String parentFile, JSONArray array, boolean isPerm, String[] roles)
			throws Exception {
		File files[] = FileUtil.listFiles(folder);
		String path, title;

		for (File file : files) {
			if (!XwlBuffer.canDisplay(file, roles, isPerm ? 3 : 1))
				continue;
			if (file.isDirectory()) {
				File indexFile = new File(file, "folder.json");
				String folderTitle, folderFile;
				folderFile = file.getName();
				if (indexFile.exists()) {
					JSONObject jo = JsonUtil.readObject(indexFile);
					folderTitle = jo.optString("title");
					if (folderTitle.isEmpty())
						folderTitle = folderFile;
				} else
					folderTitle = folderFile;
				if (folderTitle.startsWith("Str."))
					folderTitle = Str.format(request, folderTitle.substring(4));
				// 查找权限时只查找模块不查找目录
				if (!isPerm) {
					if (folderTitle.toLowerCase().indexOf(searchName) != -1) {
						JSONObject jo = new JSONObject();
						jo.put("path", parentText);
						jo.put("title", folderTitle);
						jo.put("file", folderFile);
						jo.put("parentFile", parentFile);
						array.put(jo);
						if (array.length() > 99)
							return true;
					}
				}
				if (doSearchFile(request, file, searchName, StringUtil.concat(
						parentText, "/", folderTitle), StringUtil.concat(
						parentFile, "/", folderFile), array, isPerm, roles))
					return true;
			} else {
				path = FileUtil.getModulePath(file);
				if (path.endsWith(".xwl")) {
					JSONObject moduleData = XwlBuffer.get(path, false);
					if (isPerm
							&& Boolean.FALSE.equals(moduleData
									.opt("loginRequired")))
						continue;
					title = moduleData.optString("title");
					if (title.isEmpty())
						title = path.substring(path.lastIndexOf('/') + 1);
					if (title.startsWith("Str."))
						title = Str.format(request, title.substring(4));
					if (title.toLowerCase().indexOf(searchName) != -1) {
						JSONObject jo = new JSONObject();
						jo.put("path", parentText);
						jo.put("title", title);
						jo.put("file", file.getName());
						jo.put("parentFile", parentFile);
						array.put(jo);
						if (array.length() > 99)
							return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * 获取移动模块列表。
	 */
	public static void getMobileAppList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		ArrayList<JSONObject> appList = new ArrayList<JSONObject>();
		JSONArray outputList = new JSONArray();
		String[] roles;

		roles = Session.getRoles(request);
		scanMobileApp(request, appList, new File(Base.modulePath, "apps"),
				roles);
		for (JSONObject jo : appList)
			outputList.put(jo);
		WebUtil.send(response, new JSONObject().put("rows", outputList));
	}

	/**
	 * 递归扫描移动模块列表。
	 * @param request 请求对象。
	 * @param appList 获取的应用放在此列表对象中。
	 * @param path 模块目录。
	 * @param roles 用户角色。
	 * @param fromOrder 请求是否来自调整索引模块。
	 */
	private static void scanMobileApp(HttpServletRequest request,
			ArrayList<JSONObject> appList, File path, String[] roles)
			throws Exception {
		JSONObject content, item, viewport;
		ArrayList<Entry<String, Integer>> fileNames = IDE.getSortedFile(path);
		String title, url, image, glyph, fileName;
		File file;

		for (Entry<String, Integer> entry : fileNames) {
			fileName = entry.getKey();
			file = new File(path, fileName);
			if (!XwlBuffer.canDisplay(file, roles, 2))
				continue;
			if (file.isDirectory()) {
				scanMobileApp(request, appList, file, roles);
			} else {
				url = FileUtil.getModulePath(file);
				content = XwlBuffer.get(url, true);
				if (content == null)
					continue;
				viewport = getViewport(content);
				// 没有Viewport的模块不能作为应用
				if (viewport == null)
					continue;
				viewport = viewport.getJSONObject("configs");
				item = new JSONObject();
				title = content.optString("title");
				if (title.startsWith("Str."))
					title = Str.format(request, title.substring(4));
				item.put("title", StringUtil.select(title, file.getName()));
				image = viewport.optString("appImage");
				if (image.isEmpty()) {
					glyph = viewport.optString("appGlyph");
					if (glyph.isEmpty())
						item.put("glyph", "&#xf10a;");
					else
						item.put("glyph", StringUtil.concat("&#x", glyph, ";"));
				} else
					item.put("image", image);
				item.put("url", url);
				appList.add(item);
			}
		}
	}

	/**
	 * 扫描模块控件下的子控件列表，获取Viewport控件，如果没有找到返回null。
	 * @param module module控件。
	 * @return Viewport控件或null。
	 */
	private static JSONObject getViewport(JSONObject rootNode) throws Exception {
		int i, j;
		JSONObject jo;
		JSONArray items;
		JSONObject module = (JSONObject) ((JSONArray) rootNode.opt("children"))
				.opt(0);

		items = (JSONArray) module.opt("children");
		if (items == null)
			return null;
		j = items.length();
		for (i = 0; i < j; i++) {
			jo = ((JSONObject) items.opt(i));
			if ("tviewport".equals(jo.opt("type")))
				return jo;
		}
		return null;
	}
}
