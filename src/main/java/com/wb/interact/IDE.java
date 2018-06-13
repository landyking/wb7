package com.wb.interact;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Base;
import com.wb.common.UrlBuffer;
import com.wb.common.Value;
import com.wb.common.Var;
import com.wb.common.XwlBuffer;
import com.wb.tool.Console;
import com.wb.tool.QueueWriter;
import com.wb.tool.ScriptCompressor;
import com.wb.util.DateUtil;
import com.wb.util.FileUtil;
import com.wb.util.JsonUtil;
import com.wb.util.SortUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;

/**
 * 集成开发环境后台部分应用。
 */
public class IDE {
	/** 常用的图片文件类型 */
	private static final String imageTypes[] = { "gif", "jpg", "png", "bmp" };

	/**
	 * 获取模块或文件的列表。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 获取过程中发生异常。
	 */
	public static void getList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		if ("root".equals(request.getParameter("node")))
			getBaseList(request, response);
		else {
			if ("module".equals(request.getParameter("type")))
				getModuleList(request, response);
			else
				getFileList(request, response);
		}
	}

	/**
	 * 获取文件树的文件节点列表。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 获取过程中发生异常。
	 */
	public static void getFileList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String path = request.getParameter("path"), type = request
				.getParameter("type"), fileDir;
		JSONArray fileArray = new JSONArray();
		JSONObject fileObject;
		File base, files[];
		// null IDE,1 touch,2 文件管理器树，3文件管理器表格
		String mode = request.getParameter("mode");
		boolean isIde = StringUtil.isEmpty(mode);
		boolean isTouch = "1".equals(mode);
		boolean isTree = "2".equals(mode);
		boolean isGrid = "3".equals(mode);
		boolean isDir;
		boolean isAdmin = WebUtil.hasRole(request, "admin");

		if (StringUtil.isEmpty(path)) {
			if (!isAdmin)
				checkFilePerm(null);
			base = null;
			files = File.listRoots();
		} else {
			base = new File(path);
			if (!isAdmin)
				checkFilePerm(base);
			files = FileUtil.listFiles(base);
		}
		if (isGrid) {
			String[] sortInfo = WebUtil.getSortInfo(request), fields = {
					"text", "size", "type", "date" };
			SortUtil.sort(files, StringUtil.indexOf(fields, sortInfo[0]),
					sortInfo[1].equalsIgnoreCase("desc"));
		} else {
			SortUtil.sort(files);
		}
		for (File file : files) {
			isDir = file.isDirectory();
			if (isTree && !isDir)
				continue;
			fileDir = FileUtil.getPath(file);
			if (isIde && "app".equals(type) && file.equals(Base.modulePath)
					|| "file".equals(type) && file.equals(Base.path))
				continue;
			fileObject = new JSONObject();
			fileObject.put("text", StringUtil.select(file.getName(), fileDir));
			if (isDir) {
				if (isTouch || isGrid)
					fileObject.put("icon", "wb/images/folder.gif");
				if (isIde && FileUtil.isEmpty(file) || isTree
						&& !FileUtil.hasFolder(file))
					fileObject.put("children", new JSONArray());
			} else {
				fileObject.put("size", file.length());
				fileObject.put("leaf", true);
				if (isGrid)
					fileObject.put("icon", WebUtil.encode(fileDir));
				else if (!isTree)
					fileObject.put("icon", "m?xwl=dev/ide/get-file-icon&file="
							+ WebUtil.encode(fileDir));
			}
			if (isGrid || isTouch) {
				fileObject.put("date", new Date(file.lastModified()));
				fileObject.put("type", isDir ? "文件夹" : FileUtil
						.getFileType(file));
			}
			fileArray.put(fileObject);
		}
		WebUtil.send(response, new JSONObject().put(isGrid ? "rows"
				: "children", fileArray));
	}

	/**
	 * 获取文件树的模块节点列表。
	 * 
	 * @throws Exception 获取过程中发生异常。
	 */
	private static void getModuleList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String path = request.getParameter("path"), icon, iconCls, fileName;
		File file, base;
		ArrayList<Entry<String, Integer>> fileNames;
		JSONObject content, fileObject;
		JSONArray fileArray;
		boolean isFolder, hidden;

		base = new File(path);
		if (!FileUtil.isAncestor(Base.path, base))
			SysUtil.accessDenied();
		fileNames = getSortedFile(base);
		fileArray = new JSONArray();
		for (Entry<String, Integer> entry : fileNames) {
			fileName = entry.getKey();
			if ("folder.json".equalsIgnoreCase(fileName))
				continue;
			file = new File(base, fileName);
			if (!file.exists())
				continue;
			isFolder = file.isDirectory();
			content = null;
			if (isFolder) {
				File configFile = new File(file, "folder.json");
				if (configFile.exists())
					content = JsonUtil.readObject(configFile);
			} else if (fileName.endsWith(".xwl"))
				content = XwlBuffer.get(FileUtil.getModulePath(file), false);
			if (content == null) {
				content = new JSONObject();
				if (!isFolder) {
					// 其他类型的文件
					content.put("icon", "m?xwl=dev/ide/get-file-icon&file="
							+ WebUtil.encode(FileUtil.getPath(file)));
				}
			}
			fileObject = new JSONObject();
			fileObject.put("text", fileName);
			fileObject.put("title", content.optString("title"));
			hidden = Boolean.TRUE.equals(content.opt("hidden"));
			fileObject.put("hidden", hidden);
			if (hidden)
				fileObject.put("cls", "x-highlight");// 隐藏的节点高亮显示
			fileObject.put("inframe", Boolean.TRUE.equals(content
					.opt("inframe")));
			fileObject.put("pageLink", content.optString("pageLink"));
			iconCls = content.optString("iconCls");
			if (!StringUtil.isEmpty(iconCls))
				fileObject.put("iconCls", iconCls);
			// icon用于非xwl文件
			icon = content.optString("icon");
			if (!StringUtil.isEmpty(icon))
				fileObject.put("icon", icon);
			if (isFolder) {
				if (!hasChildren(file))
					fileObject.put("children", new JSONArray());
			} else {
				fileObject.put("leaf", true);
			}
			fileArray.put(fileObject);
		}
		WebUtil.send(response, new JSONObject().put("children", fileArray));
	}

	/**
	 * 获取文件树的根节点列表。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 获取过程中发生异常。
	 */
	private static void getBaseList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONArray list = new JSONArray();
		JSONObject node = new JSONObject();
		String sysFolderBase = Var.getString("sys.ide.sysFolderBase");

		node.put("text", "模块");
		node.put("iconCls", "module_icon");
		node.put("expanded", true);
		node.put("base", FileUtil.getPath(Base.modulePath) + '/');
		node.put("type", "module");
		list.put(node);

		node = new JSONObject();
		node.put("text", "应用");
		node.put("iconCls", "application_icon");
		node.put("base", FileUtil.getPath(Base.path) + '/');
		node.put("type", "app");
		list.put(node);
		if (!sysFolderBase.equals("app")) {
			node = new JSONObject();
			node.put("text", "系统");
			node.put("iconCls", "system_icon");
			if (sysFolderBase.equals("server"))
				sysFolderBase = FileUtil.getPath(Base.path.getParentFile()
						.getParent()) + '/';
			else
				sysFolderBase = ""; // root
			node.put("base", sysFolderBase);
			node.put("type", "file");
			list.put(node);
		}
		WebUtil.send(response, list);
	}

	/**
	 * 设置文件或模块的属性。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 设置过程中发生异常。
	 */
	public static void setProperty(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONObject map = WebUtil.fetch(request), result;
		File indexFile, configFile = null, newFile, oldFile = new File(map
				.getString("path"));
		String url = map.optString("url"), oldName = oldFile.getName(), newName = map
				.getString("text");
		boolean nameModified = !oldName.equals(newName), indexFileExists;
		boolean isModule, isDir, needConfig, urlValid = map
				.optBoolean("urlValid");
		JSONObject indexContent = null, content;

		isDir = oldFile.isDirectory();
		if (urlValid && !UrlBuffer.exists(url, oldFile))
			throw new IllegalArgumentException("URL捷径 \"" + url + "\" 已经存在。");
		if (nameModified)
			newFile = new File(oldFile.getParent(), newName);
		else
			newFile = oldFile;
		if (nameModified)
			FileUtil.syncRename(oldFile, newFile);
		indexFile = new File(newFile.getParentFile(), "folder.json");
		indexFileExists = indexFile.exists();
		isModule = map.getBoolean("isModule");
		needConfig = isModule || indexFileExists;
		if (needConfig) {
			if (indexFileExists)
				indexContent = JsonUtil.readObject(indexFile);
			else if (isModule) {
				indexContent = new JSONObject();
				indexContent.put("index", new JSONArray());
			}
			if (isModule) {
				if (isDir) {
					configFile = new File(newFile, "folder.json");
					if (configFile.exists())
						content = JsonUtil.readObject(configFile);
					else {
						content = new JSONObject();
						content.put("index", new JSONArray());
					}
				} else
					content = JsonUtil.readObject(newFile);
				content.put("title", map.getString("title"));
				content.put("iconCls", map.getString("iconCls"));
				content.put("hidden", map.getBoolean("hidden"));
				if (isDir)
					FileUtil.syncSave(configFile, content.toString());
				else {
					content.put("inframe", map.getBoolean("inframe"));
					content.put("pageLink", map.getString("pageLink"));
					updateModule(newFile, content, null, false);
				}
			}
			if (nameModified && indexFileExists) {
				// 更新folder.json中的索引信息
				JSONArray idxArray = indexContent.getJSONArray("index");
				int index = idxArray.indexOf(oldName);
				if (index != -1) {
					idxArray.put(index, newName);
					FileUtil.syncSave(indexFile, indexContent.toString());
				}
			}
		}
		if (isModule) {
			String newRelPath = FileUtil.getModulePath(newFile);
			String oldRelPath = nameModified ? FileUtil.getModulePath(oldFile)
					: newRelPath;
			boolean changed = false;
			if (isDir) {
				if (nameModified) {
					if (UrlBuffer.change(oldRelPath, newRelPath, isDir))
						changed = true;
				}
			} else {
				if (UrlBuffer.remove(oldRelPath))
					changed = true;
				if (urlValid && !url.isEmpty()) {
					UrlBuffer.put('/' + url, newRelPath);
					changed = true;
				}
			}
			if (changed)
				UrlBuffer.save();
		}
		result = new JSONObject();
		result.put("lastModified", DateUtil
				.getTimestamp(newFile.lastModified()));
		result.put("path", FileUtil.getPath(newFile));
		if (nameModified) {
			// 更改js和xwl中的模块引用路径
			JSONObject resp = new JSONObject();
			JSONArray src = new JSONArray(), moveTo = new JSONArray();
			src.put(FileUtil.getPath(oldFile));
			moveTo.put(FileUtil.getPath(newFile));
			Object[] changeInfo = changePath(src, moveTo);
			resp.put("files", changeInfo[0]);
			resp.put("change", changeInfo[1]);
			resp.put("moveTo", moveTo);
			result.put("refactorInfo", resp);
		}
		WebUtil.send(response, result);
	}

	/**
	 * 搜索应用目录下指定名称的文件列表。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 搜索过程中发生异常。
	 */
	public static void searchFile(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONArray array = new JSONArray();
		String query = request.getParameter("query").toLowerCase();

		if (query.isEmpty())
			query = ".xwl";
		doSearchFile(Base.path, query, array);
		WebUtil.send(response, new JSONObject().put("rows", array));
	}

	/**
	 * 
	 * 在指定目录下搜索文件。内部函数，用于递归搜索。
	 * 
	 * @param folder 目录。
	 * @param searchName 查找的文件名称关键字，任何包括该关键字的文件均将被列举。
	 * @param array 用于存放搜索结果。
	 * @return 是否完成搜索，如果搜索结果大于等于100项，将返回true，否则返回false。
	 * @throws Exception 搜索过程中发生异常。
	 */
	private static boolean doSearchFile(File folder, String searchName,
			JSONArray array) throws Exception {
		File files[] = FileUtil.listFiles(folder);
		String name, path;

		for (File file : files) {
			if (file.isDirectory()) {
				if (doSearchFile(file, searchName, array))
					return true;
			} else {
				path = FileUtil.getPath(file);
				name = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
				if (!name.equals("folder.json")
						&& name.indexOf(searchName) != -1) {
					JSONObject jo = new JSONObject();
					jo.put("path", path);
					array.put(jo);
					if (array.length() > 99)
						return true;
				}
			}
		}
		return false;
	}

	/**
	 * 查询统计文件或目录的大小，数量，种类和最后修改日期。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 查询统计过程中发生异常。
	 */
	public static void total(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		File file = new File(request.getParameter("path"));
		JSONObject result = new JSONObject();
		boolean isDir = file.isDirectory();

		result.put("lastModified", DateUtil.getTimestamp(file.lastModified()));
		result.put("fileSize", file.length());
		if (isDir && FileUtil.isAncestor(Base.path, file)) {
			int[] info = new int[4];
			total(file, info);
			result.put("total", info);
		}
		if (!isDir && FileUtil.isAncestor(Base.modulePath, file)
				&& file.getName().endsWith(".xwl"))
			result.put("url", UrlBuffer.find(file));
		WebUtil.send(response, result);
	}

	/**
	 * 统计指定目录下指定文件的种类、数量和大小。
	 * 
	 * @param folder 统计的目录
	 * @param info 统计信息数组，0模块数, 1文件数, 2目录数, 3合计大小
	 */
	private static void total(File folder, int[] info) {
		File[] files = FileUtil.listFiles(folder);
		for (File file : files) {
			if (file.isDirectory()) {
				info[2]++;
				total(file, info);
			} else {
				if (file.getName().endsWith(".xwl"))
					info[0]++;
				info[1]++;
				info[3] += file.length();
			}
		}
	}

	/**
	 * 搜索目录或文件内的指定文本。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 搜索过程中发生异常。
	 */
	public static void search(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String searchType = request.getParameter("searchType");
		if ("shortcut".equals(searchType)) {
			searchShortcut(request, response);
			return;
		}
		JSONArray pathList = new JSONArray(request.getParameter("pathList"));
		String searchText = request.getParameter("search"), filePatterns[] = StringUtil
				.split(request.getParameter("filePatterns"), ',', true);
		boolean whole = Boolean.parseBoolean(request.getParameter("whole")), searched;
		boolean isReplace = Boolean.parseBoolean(request
				.getParameter("isReplace"));
		Pattern searchPattern = null;
		File file;
		int i, pathListLen = pathList.length();
		ArrayList<File> searchedFiles = new ArrayList<File>(pathListLen);
		JSONArray rows = new JSONArray();

		if (Boolean.parseBoolean(request.getParameter("regularExp")))
			searchPattern = Pattern.compile(searchText);
		else
			searchPattern = Pattern.compile(StringUtil.concat(Boolean
					.parseBoolean(request.getParameter("caseSensitive")) ? ""
					: "(?i)", whole ? "\\b" : "", "\\Q", searchText, "\\E",
					whole ? "\\b" : ""));
		for (i = 0; i < pathListLen; i++) {
			file = new File(pathList.getString(i));
			if (!FileUtil.isAncestor(Base.path, file))
				throw new IllegalArgumentException("禁止检索应用目录之外的文件。");
			searched = false;
			// 父目录搜索过后，子目录不需要再搜索
			for (File f : searchedFiles) {
				if (FileUtil.isAncestor(f, file)) {
					searched = true;
					break;
				}
			}
			if (!searched) {
				doSearch(file, rows, searchPattern, filePatterns,
						isReplace ? request.getParameter("replace") : null);
				searchedFiles.add(file);
			}
		}
		WebUtil.send(response, new JSONObject().put("rows", rows));
	}

	/**
	 * 对指定目录或文件搜索关键字，如果指定替换关键字则执行替换，并把搜索或替换的结果放入JSONArray对象。
	 * 
	 * @param file 搜索的文件或目录。
	 * @param rows 把搜索结果放入该对象。
	 * @param pattern 搜索关键词正则表达式
	 * @param filePatterns 搜索的文件类型
	 * @param replaceText 如果指定此关键字则替换搜索结果。
	 * @return 是否继续搜索，true继续搜索，false停止搜索。停止搜索条件为搜索结果超过1000项。
	 * @throws Exception 搜索文件过程中发生异常。
	 */
	private static boolean doSearch(File file, JSONArray rows, Pattern pattern,
			String[] filePatterns, String replaceText) throws Exception {
		if (file.isDirectory()) {
			File files[] = FileUtil.listFiles(file);
			boolean match;

			for (File f : files) {
				if (filePatterns.length == 0 || f.isDirectory())
					match = true;
				else {
					match = false;
					for (String filePattern : filePatterns) {
						if (FilenameUtils.wildcardMatch(f.getName(),
								filePattern, IOCase.SYSTEM)) {
							match = true;
							break;
						}
					}
				}
				if (match
						&& !doSearch(f, rows, pattern, filePatterns,
								replaceText))
					return false;
			}
		} else {
			if (debugVersionExists(file))
				return true;
			String text = FileUtil.readString(file), path = FileUtil
					.getPath(file);
			// 查找模式
			if (replaceText == null) {
				if (path.endsWith(".xwl")) {
					if (!searchXwl(text, path, pattern, rows))
						return false;
				} else {
					if (!searchText(text, path, pattern, rows, null, null))
						return false;
				}
			} else {
				// 替换模式
				String replacedText = pattern.matcher(text).replaceAll(
						replaceText);
				JSONObject row;
				if (!replacedText.equals(text)) {
					FileUtil.syncSave(file, replacedText);
					row = new JSONObject();
					row.put("content", "替换：" + replaceText);
					row.put("path", path);
					// 用于更新客户端文件日期
					row.put("lastModified", DateUtil.getTimestamp(file
							.lastModified()));
					rows.put(row);
				}
			}
		}
		return true;
	}

	/**
	 * 搜索指定关键字在文本内的行列和路径位置，并把搜索内容加粗显示。 搜索完成把结果放入指定对象。
	 * 
	 * @param text 搜索的文本。
	 * @param path 文本所在文件的完全路径。
	 * @param pattern 搜索关键字正则表达式。
	 * @param rows 搜索结果放放该对象。
	 * @param nodePath 如果搜索的是XWL模块，则指定节点路径。
	 * @param itemName 如果搜索的是XWL模块，则指定节点属性，属性以[Configs/Events=name]表示。
	 *如搜索事件initialize，则表示为[Events=initialize]。
	 * @return 是否继续搜索，true继续搜索，false停止搜索。超过1000项搜索结果将返回false。
	 */
	private static boolean searchText(String text, String path,
			Pattern pattern, JSONArray rows, String nodePath, String itemName) {
		int pos, lastPos = 0, line = 1, afterTextPos, lineInfo[];
		int matchTextLength, textLength = text.length();
		JSONObject row;
		Matcher matcher = pattern.matcher(text);

		while (matcher.find()) {
			row = new JSONObject();
			if (rows.length() == 999) {
				row.put("content", "超过1000项被搜索到，停止搜索。");
				rows.put(row);
				return false;
			} else {
				pos = matcher.start();
				matchTextLength = matcher.end() - pos;
				afterTextPos = pos + matchTextLength;
				row.put("content", StringUtil.concat(StringUtil.toHTML(text
						.substring(Math.max(0, pos - 30), pos), false, false),
						"<strong>", StringUtil.toHTML(text.substring(pos,
								afterTextPos), false, false), "</strong>",
						afterTextPos >= textLength ? "" : StringUtil.toHTML(
								text.substring(afterTextPos, Math.min(
										textLength, pos + 70)), false, false)));
				lineInfo = StringUtil.stringOccur(text, '\n', lastPos, pos);
				line += lineInfo[0];
				lastPos = pos;
				row.put("path", path);
				row.put("line", line);
				row.put("ch", pos - lineInfo[1]);
				row.put("nodePath", nodePath);
				row.put("itemName", itemName);
				rows.put(row);
			}
			pos += matchTextLength;
		}
		return true;
	}

	/**
	 * 搜索url捷径。
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 搜索过程发生异常。
	 */
	public static void searchShortcut(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String shortcut = request.getParameter("shortcut");
		JSONObject jo;
		String key;
		Set<Entry<String, String>> es = UrlBuffer.buffer.entrySet();
		JSONArray ja = new JSONArray();
		for (Entry<String, String> e : es) {
			key = e.getKey().substring(1);
			if (FilenameUtils.wildcardMatch(key, shortcut, IOCase.INSENSITIVE)) {
				jo = new JSONObject();
				if (ja.length() == 999) {
					jo.put("content", "超过1000项被搜索到，停止搜索。");
					ja.put(jo);
					break;
				}
				jo.put("content", key);
				jo.put("path", FileUtil.getPath(new File(Base.modulePath, e
						.getValue())));
				jo.put("line", 1);
				jo.put("ch", 1);
				ja.put(jo);
			}
		}
		WebUtil.send(response, new JSONObject().put("rows", ja));
	}

	/**
	 * 判断指定文件是否存在调试版本。如果存在以“_debug”结尾的文件则返回true，否则返回false。
	 * 此功能用于避免搜索重复的结果，因为发布版本可以通过调试版本得到。该功能可通过变量
	 * sys.ide.searchIgnoreRelease来控制是否启用该功能。
	 * 
	 * @param file 判断的文件。
	 * @return 是否存在调试版本文件。
	 */
	private static boolean debugVersionExists(File file) {
		if (Var.getBool("sys.ide.searchIgnoreRelease")) {
			String filename = file.getName();
			File debugFile = new File(file.getParentFile(), FileUtil
					.removeExtension(filename)
					+ "-debug." + FileUtil.getFileExt(filename));
			return debugFile.exists();
		}
		return false;
	}

	/**
	 * 搜索模块文件。
	 * 
	 * @param text 模块文件内容。
	 * @param path 模块文件路径。
	 * @param pattern 搜索关键词正则表达式。
	 * @param rows 搜索结果存放在该对象。
	 * @return 是否继续搜索，true继续搜索，false停止搜索。停止搜索条件为搜索结果超过1000项。
	 */
	private static boolean searchXwl(String text, String path, Pattern pattern,
			JSONArray rows) {
		JSONObject xwl = new JSONObject(text);

		return scanXwl(xwl, path, pattern, rows, "");
	}

	/**
	 * 对模块文件各个节点，搜索指定的关键字。
	 * 
	 * @param xwl 模块文件的指定节点内容。
	 * @param path 模块文件路径。
	 * @param pattern 搜索的正则表达式。
	 * @param rows 搜索结果存放在该对象。
	 * @param nodePath 节点路径。
	 * @return 是否继续搜索，true继续搜索，false停止搜索。停止搜索条件为搜索结果超过1000项。
	 */
	private static boolean scanXwl(JSONObject xwl, String path,
			Pattern pattern, JSONArray rows, String nodePath) {
		JSONArray children = xwl.optJSONArray("children");
		JSONObject jo, configs;
		String currentPath;

		if (children != null) {
			int i, j = children.length();
			for (i = 0; i < j; i++) {
				jo = children.getJSONObject(i);
				configs = jo.getJSONObject("configs");
				currentPath = nodePath + "/" + configs.getString("itemId");
				if (!scanItems(configs, path, pattern, rows, currentPath,
						"Configs"))
					return false;
				if (jo.has("events")
						&& !scanItems(jo.getJSONObject("events"), path,
								pattern, rows, currentPath, "Events"))
					return false;
				if (!scanXwl(jo, path, pattern, rows, currentPath))
					return false;
			}
		}
		return true;
	}

	/**
	 * 对模块文件各个节点的配置项或事件，搜索指定的关键字。
	 * 
	 * @param jo 配置项或事件数据。
	 * @param path 模块文件路径。
	 * @param pattern 搜索的正则表达式。
	 * @param rows 搜索结果存放在该对象。
	 * @param nodePath 节点路径。
	 * @param type 配置项或事件。
	 * @return 是否继续搜索，true继续搜索，false停止搜索。停止搜索条件为搜索结果超过1000项。
	 */
	private static boolean scanItems(JSONObject jo, String path,
			Pattern pattern, JSONArray rows, String nodePath, String type) {
		Set<Entry<String, Object>> items = jo.entrySet();
		String key;
		for (Entry<String, Object> item : items) {
			key = item.getKey();
			if (!searchText(key, path, pattern, rows, nodePath, type + "="
					+ key))
				return false;
			if (!searchText(item.getValue().toString(), path, pattern, rows,
					nodePath, type + "=" + key))
				return false;

		}
		return true;
	}

	/**
	 * 判断目录是否含可显示的子节点。
	 * 
	 * @param dir 目录
	 * @return true有子节点，false无子节点。
	 */
	private static boolean hasChildren(File dir) {
		File files[];
		files = FileUtil.listFiles(dir);
		if (files == null)
			return false;
		for (File file : files) {
			if (file.isDirectory()) {
				return true;
			} else if (!file.getName().equals("folder.json"))
				return true;
		}
		return false;
	}

	/**
	 * 在当前的文件树中添加模块或目录。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 添加过程中发生异常。
	 */
	public static void addModule(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String name = request.getParameter("name");
		String title = request.getParameter("title");
		String iconCls = request.getParameter("iconCls");
		String url = null;
		Object value;
		boolean hidden = Boolean.parseBoolean(request.getParameter("hidden"));
		boolean isDir = Boolean.parseBoolean(request.getParameter("isDir"));
		JSONObject moduleConfigs, moduleMeta, content = new JSONObject();
		Set<Entry<String, Object>> moduleConfigEntries;
		File base;

		moduleMeta = moduleConfigs = Controls.get("module").optJSONObject(
				"configs");
		content.put("title", title);
		content.put("iconCls", iconCls);
		content.put("hidden", hidden);
		if (!isDir) {
			// 添加默认角色，任何用户都可以访问
			content.put("roles", new JSONObject().put("default", 1));
			content.put("inframe", Boolean.parseBoolean(request
					.getParameter("inframe")));
			content.put("pageLink", request.getParameter("pageLink"));
			url = request.getParameter("url");
			if (!UrlBuffer.exists(url, null))
				throw new IllegalArgumentException("URL捷径 \"" + url
						+ "\" 已经存在。");
			if (!name.endsWith(".xwl")) {
				if (name.toLowerCase().endsWith(".xwl"))
					name = name.substring(0, name.length() - 3) + "xwl";
				else
					name = name + ".xwl";
			}
			JSONObject module = new JSONObject();
			moduleConfigs = new JSONObject();
			moduleConfigs.put("itemId", "module");
			moduleConfigEntries = moduleMeta.entrySet();
			for (Entry<String, Object> entry : moduleConfigEntries) {
				value = ((JSONObject) entry.getValue()).opt("value");
				if (value != null)
					moduleConfigs.put(entry.getKey(), value.toString());
			}
			module.put("children", new JSONArray());
			module.put("configs", moduleConfigs);
			module.put("type", "module");
			content.put("children", new JSONArray().put(module));
		}
		base = new File(request.getParameter("path"));
		File file = addModule(base, name, isDir, content);
		setFileIndex(base, request.getParameter("indexName"), new JSONArray()
				.put(name), request.getParameter("type"));
		if (isDir) {
			JSONObject fileInfo = new JSONObject();
			fileInfo.put("file", name);
			fileInfo.put("title", title);
			fileInfo.put("iconCls", iconCls);
			fileInfo.put("hidden", hidden);
			WebUtil.send(response, fileInfo);
		} else {
			if (!url.isEmpty()) {
				UrlBuffer.put('/' + url, FileUtil.getModulePath(file));
				UrlBuffer.save();
			}
			doOpen(new JSONArray().put(FileUtil.getPath(file)), null, null,
					request, response);
		}
	}

	/**
	 * 设置文件在列表中显示位置的索引信息。
	 * 
	 * @param folder 文件所在目录。
	 * @param indexFileName 插入的文件名称列表基于此文件名称位置为参考。
	 * @param insertFileNames 添加的文件名称列表。
	 * @param type 添加类别，before在目标位置之前，after在目标位置之前，append添加在最后
	 * @throws Exception 设置过程中发生异常。
	 */
	private static void setFileIndex(File folder, String indexFileName,
			JSONArray insertFileNames, String type) throws Exception {
		JSONObject content;
		JSONArray indexArray;
		File file = new File(folder, "folder.json"), checkFile;
		int index, i, j = insertFileNames.length(), k;

		if (file.exists()) {
			content = JsonUtil.readObject(file);
			indexArray = content.getJSONArray("index");
			for (i = 0; i < j; i++) {
				index = indexArray.indexOf(insertFileNames.getString(i));
				if (index != -1)
					indexArray.remove(index);
			}
			k = indexArray.length();
			for (i = k - 1; i >= 0; i--) {
				// 移除已经不存在的索引
				checkFile = new File(folder, indexArray.getString(i));
				if (!checkFile.exists())
					indexArray.remove(i);
			}
			if (StringUtil.isEmpty(indexFileName) || "append".equals(type))
				index = -1;
			else {
				index = indexArray.indexOf(indexFileName);
				if (index != -1 && "after".equals(type))
					index++;
			}
		} else {
			content = new JSONObject();
			indexArray = new JSONArray();
			content.put("index", indexArray);
			index = -1;
		}
		if (index == -1)
			for (i = j - 1; i >= 0; i--)
				indexArray.put(insertFileNames.getString(i));
		else
			for (i = 0; i < j; i++)
				indexArray.add(index, insertFileNames.getString(i));
		FileUtil.syncSave(file, content.toString());
	}

	/**
	 * 创建模块文件或目录。
	 * 
	 * @param base 上级目录。
	 * @param name 文件或目录名称。
	 * @param isDir 是否是目录。
	 * @param content 文件内容，如果创建的是目录，则写入到目录索引文件。
	 * @return 创建的文件或目录。
	 * @throws Exception 创建过程发生异常。
	 */
	private static File addModule(File base, String name, boolean isDir,
			JSONObject content) throws Exception {
		File file = new File(base, name);

		if (isDir) {
			FileUtil.syncCreate(file, true);
			File configFile = new File(file, "folder.json");
			content.put("index", new JSONArray());
			FileUtil.syncSave(configFile, content.toString());
		} else {
			FileUtil.syncCreate(file, false);
			FileUtil.syncSave(file, content.toString());
		}
		return file;
	}

	/**
	 * 返回使用html显示的线程信息，蓝色表示daemon线程。
	 */
	public static void getThreadList(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
		Thread[] threadArray = threadSet.toArray(new Thread[threadSet.size()]);
		StringBuilder buf = new StringBuilder();
		final Collator collator = Collator.getInstance();
		int daemonThreads = 0;

		Arrays.sort(threadArray, new Comparator<Thread>() {
			public int compare(Thread t1, Thread t2) {
				CollationKey k1 = collator.getCollationKey(StringUtil.opt(
						t1.getName()).toLowerCase());
				CollationKey k2 = collator.getCollationKey(StringUtil.opt(
						t2.getName()).toLowerCase());
				return k1.compareTo(k2);
			}
		});
		for (Thread thread : threadArray) {
			buf.append("<p");
			if (thread.isDaemon()) {
				daemonThreads++;
				buf.append(" style=\"color:blue\">");
			} else
				buf.append(">");
			buf.append(thread.getName());
			buf.append(" (");
			buf.append(thread.getPriority());
			buf.append(")</p>");
		}
		buf.append("<p>合计线程：");
		buf.append(threadArray.length);
		buf.append("，守护线程：");
		buf.append(daemonThreads);
		buf.append("，普通线程：");
		buf.append(threadArray.length - daemonThreads);
		buf.append("</p>");
		WebUtil.send(response, buf);
	}

	/**
	 * 在当前的文件树中添加文件或目录。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 添加过程中发生异常。
	 */
	public static void addFile(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String name = request.getParameter("name");
		String path = request.getParameter("path");

		if (path.isEmpty())
			throw new IllegalArgumentException("父目录为空，无法创建文件。");
		boolean isDir = Boolean.parseBoolean(request.getParameter("isDir"));
		JSONObject fileInfo = new JSONObject();
		File file = addFile(new File(path), name, isDir);

		if (isDir) {
			fileInfo.put("children", new JSONArray());
		} else {
			fileInfo.put("leaf", true);
			fileInfo.put("icon", "m?xwl=dev/ide/get-file-icon&file="
					+ WebUtil.encode(FileUtil.getPath(file)));
		}
		fileInfo.put("text", name);
		WebUtil.send(response, fileInfo);
	}

	/**
	 * 重新热加载系统。
	 */
	public static void reload(HttpServletRequest request,
			HttpServletResponse response) {
		SysUtil.reload(1);
	}

	/**
	 * 在指定目录下添加文件。
	 * 
	 * @param base 上级目录。
	 * @param name 文件或目录名称。
	 * @param isDir 是否创建目录。
	 * @return 创建的文件或目录。
	 * @throws Exception 创建过程中发生异常。
	 */
	private static File addFile(File base, String name, boolean isDir)
			throws Exception {
		File file = new File(base, name);

		if (isDir)
			FileUtil.syncCreate(file, true);
		else
			FileUtil.syncCreate(file, false);
		return file;
	}

	/**
	 * 获得指定目录下按设置的排序返回的文件列表。
	 * 
	 * @param dir 对该目录下的模块进行排序。
	 * @return 排序后的模块列表。
	 * @throws Exception 排序过程发生异常。
	 */
	public static ArrayList<Entry<String, Integer>> getSortedFile(File dir)
			throws Exception {
		String fileNames[];
		File configFile;
		JSONArray indexArray;
		HashMap<String, Integer> jsonMap = new HashMap<String, Integer>();
		int i, j;

		fileNames = dir.list();
		SortUtil.sort(fileNames);
		j = fileNames.length;
		for (i = 0; i < j; i++)
			jsonMap.put(fileNames[j - i - 1], Integer.MAX_VALUE - i);

		configFile = new File(dir, "folder.json");
		if (configFile.exists()) {
			indexArray = JsonUtil.readObject(configFile).getJSONArray("index");
			j = indexArray.length();
			for (i = 0; i < j; i++)
				jsonMap.put(indexArray.getString(i), i);
		}
		return SortUtil.sortValue(jsonMap, true);
	}

	/**
	 * 保存一个或多个文件。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 保存过程发生异常。
	 */
	public static void saveFile(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		boolean confirm = !Boolean.parseBoolean(request
				.getParameter("noConfirm"));
		JSONArray files = new JSONArray(StringUtil.getString(request
				.getInputStream())), lastModifiedData = new JSONArray();
		JSONObject content;
		File file;
		String filename = null, fileExt, saveContent;
		int i, j = files.length(), modifiedCount = 0;

		for (i = 0; i < j; i++) {
			content = files.getJSONObject(i);
			file = new File(content.getString("file"));
			if (confirm
					&& file.lastModified() != content.getTimestamp(
							"lastModified").getTime()) {
				if (filename == null)
					filename = content.getString("file");
				modifiedCount++;
			}
		}
		if (modifiedCount > 0)
			SysUtil
					.error(
							"文件 \""
									+ FileUtil.getFilename(filename)
									+ "\""
									+ (modifiedCount > 1 ? (" 等 "
											+ modifiedCount + " 项") : " ")
									+ "已经被修改，确定要保存吗？", "101");
		for (i = 0; i < j; i++) {
			content = files.getJSONObject(i);
			filename = content.getString("file");
			saveContent = content.getString("content");
			file = new File(filename);
			fileExt = FileUtil.getFileExt(filename).toLowerCase();
			if (StringUtil.indexOf(imageTypes, fileExt) == -1) {
				if ("xwl".equals(fileExt))
					updateModule(file, new JSONObject(saveContent), null, false);
				else
					FileUtil.syncSave(file, saveContent, content
							.optString("charset"));
			} else
				FileUtil.syncSave(file, StringUtil.decodeBase64(saveContent));
			lastModifiedData.put(DateUtil.getTimestamp(file.lastModified()));
		}
		WebUtil.send(response, lastModifiedData);
	}

	/**
	 * 使用同步方法更新文件内的模块和权限数据，使模块文件在修改和设置权限时使文件更新同步。
	 * @param file 模块文件对象。
	 * @param moduleData 保存的文件模块数据，如果为null保持文件内的模块数据。
	 * @param roles 保存的权限角色列表，如果为null保持文件内的角色数据。
	 * @param addRoles true添加角色，false删除角色。
	 */
	public static synchronized void updateModule(File file,
			JSONObject moduleData, String roles[], boolean addRoles)
			throws Exception {
		boolean keepLastModified = false;
		if (moduleData == null) {
			if (roles == null)
				return; // 没有更新的内容
			keepLastModified = true; // 仅更新权限时lastModified不更新
		}
		JSONObject data = JsonUtil.readObject(file);
		JSONObject oldRoles = (JSONObject) data.opt("roles");

		if (moduleData != null) {
			data = moduleData;
			data.put("roles", oldRoles);
		}
		if (roles != null) {
			for (String role : roles) {
				if (addRoles)
					oldRoles.put(role, 1);
				else
					oldRoles.remove(role);
			}
		}
		FileUtil.syncSave(file, data.toString(4), "utf-8", keepLastModified);
	}

	/**
	 * 打开一个或多个文件。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 打开文件发生异常。
	 */
	public static void openFile(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		doOpen(new JSONArray(request.getParameter("fileNames")), request
				.getParameter("charset"), request.getParameter("type"),
				request, response);
	}

	/**
	 * 打开指定文件名称列表的文件，并把文件数据发送到客户端。
	 * 
	 * @param fileNames 需要打开的文件列表。
	 * @param charset 字符编码。
	 * @param type 请求来源类型。
	 * @param response 响应对象。
	 * @throws Exception 打开过程发生异常。
	 */
	private static void doOpen(JSONArray fileNames, String charset,
			String type, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONArray result = new JSONArray();
		JSONObject content;
		String filename, shortFilename, fileText, fileExt;
		boolean isAdmin = WebUtil.hasRole(request, "admin");
		File file;
		int i, j;
		boolean fromEditor = "1".equals(type);

		j = fileNames.length();
		for (i = 0; i < j; i++) {
			filename = fileNames.getString(i);
			shortFilename = FileUtil.getFilename(filename);
			fileExt = FileUtil.getFileExt(filename).toLowerCase();
			file = new File(filename);
			if (!isAdmin)
				checkFilePerm(file);
			if (!fromEditor && fileExt.equals("xwl")) {
				content = new JSONObject(FileUtil.readString(file));
				fillProperties(content);
				content.put("file", shortFilename);
			} else {
				content = new JSONObject();
				content.put("file", shortFilename);
				content.put("icon", "m?xwl=dev/ide/get-file-icon&file="
						+ WebUtil.encode(filename));
				if (StringUtil.indexOf(imageTypes, fileExt) == -1) {
					if (StringUtil.isEmpty(charset)) {
						if (FileUtil.isAncestor(Base.path, file))
							charset = "utf-8";
						else
							charset = Var.getString("sys.locale.charset");
					} else if ("[系统默认]".equals(charset))
						charset = null;
					if (StringUtil.isEmpty(charset)) {
						fileText = FileUtils.readFileToString(file);
						content.put("charset", "[系统默认]");
					} else {
						fileText = FileUtils.readFileToString(file, charset);
						content.put("charset", charset);
					}
				} else {
					fileText = StringUtil
							.encodeBase64(new FileInputStream(file));
					// 无需关闭文件流，encodeBase64方法读完后自动关闭文件流
				}
				content.put("content", fileText);
			}
			content.put("lastModified", DateUtil.getTimestamp(file
					.lastModified()));
			content.put("path", filename);
			result.put(content);
		}
		WebUtil.send(response, result);
	}

	/**
	 * 获取控制台缓存区中的字符串信息。
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 获取过程发生异常。
	 */
	public static void getOutputs(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		QueueWriter out = getSysOut(request);
		String string = out.toString();
		out.clear();
		WebUtil.send(response, string);
	}

	/**
	 * 把模块中各个节点的配置项的itemId, iconCls填充到节点属性中。
	 * 
	 * @param json 模块节点对象。
	 * @throws IOException 如果控件不存在将抛异常。
	 */
	private static void fillProperties(JSONObject json) throws IOException {
		JSONArray controls = json.getJSONArray("children");
		JSONObject control, fillControl, fillGeneral, configs;
		String type;
		int i, j = controls.length();

		for (i = 0; i < j; i++) {
			control = controls.getJSONObject(i);
			type = control.getString("type");
			fillControl = Controls.get(type);
			if (fillControl == null)
				throw new NullPointerException("控件 \"" + type + "\" 没有找到。");
			configs = control.optJSONObject("configs");
			if (configs != null)
				control.put("text", configs.optString("itemId"));
			fillGeneral = fillControl.getJSONObject("general");
			control.put("iconCls", fillGeneral.getString("iconCls"));
			if (control.has("children") && control.length() > 0)
				fillProperties(control);
		}
	}

	/**
	 * 获取指定类型文件的图标，并发送到客户端。
	 * 
	 * @param request 请求对象。
	 * @param response 发生对象。
	 * @throws Exception 获取图标过程发生异常。
	 */
	public static void getFileIcon(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String fileName = WebUtil.decode(request.getParameter("file"));
		String fileExt = FileUtil.getFileExt(fileName).toLowerCase();
		String zipTypes[] = { "zip", "rar", "gzip", "gz", "tar", "cab" };
		File file = null;
		InputStream is;
		boolean isAdmin = WebUtil.hasRole(request, "admin");

		if (StringUtil.indexOf(imageTypes, fileExt) != -1) {
			file = new File(fileName);
			if (!isAdmin)
				checkFilePerm(file);
			long fileLen = file.length();
			if (fileLen > 10240 || fileLen == 0)
				file = null;
		}
		if (file == null) {
			if (fileExt.equals("doc") || fileExt.equals("docx"))
				fileName = "file_doc";
			else if (fileExt.equals("xls") || fileExt.equals("xlsx"))
				fileName = "file_xls";
			else if (fileExt.equals("ppt") || fileExt.equals("pptx"))
				fileName = "file_ppt";
			else if (fileExt.equals("htm") || fileExt.equals("html"))
				fileName = "web";
			else if (fileExt.equals("jar") || fileExt.equals("war"))
				fileName = "file_jar";
			else if (StringUtil.indexOf(zipTypes, fileExt) != -1)
				fileName = "file_zip";
			else if (fileExt.equals("txt"))
				fileName = "file_txt";
			else if (fileExt.equals("js"))
				fileName = "file_js";
			else if (fileExt.equals("css"))
				fileName = "file_css";
			else if (fileExt.equals("java"))
				fileName = "file_java";
			else if (fileExt.equals("jsp"))
				fileName = "file_jsp";
			else if (fileExt.equals("xml"))
				fileName = "file_xml";
			else if (fileExt.equals("xwl"))
				fileName = "file_xwl";
			else
				fileName = "file_default";
			file = new File(Base.path, StringUtil.concat("wb/images/", fileName
					+ ".gif"));
			response.setContentType("image/gif");
		} else
			response.setContentType("image/" + fileExt);
		is = new FileInputStream(file);
		try {
			IOUtils.copy(is, response.getOutputStream());
		} finally {
			is.close();
		}
		response.flushBuffer();
	}

	/**
	 * 删除指定的文件或目录。
	 * 
	 * @param request 请求对象。
	 * @param response 发送对象。
	 * @throws Exception 删除过程发生异常。
	 */
	public static void deleteFiles(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		JSONArray files = new JSONArray(request.getParameter("files"));
		int i, j = files.length();
		String filename;
		File file;

		for (i = 0; i < j; i++) {
			filename = files.getString(i);
			file = new File(filename);
			FileUtil.syncDelete(file, true);
		}
	}

	/**
	 * 设置当前用户指定类型的数据。
	 */
	public static void setData(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String name = request.getParameter("name");
		String value = request.getParameter("value");
		String sessionName = request.getParameter("sessionName");

		Value.set(request, name, value);
		if (sessionName != null)
			WebUtil.setSessionValue(request, sessionName, value);
	}

	/**
	 * 移动或复制文件/目录。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 移动或复制文件/目录过程发生异常。
	 */
	public static void moveFiles(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String isCopyStr = request.getParameter("isCopy");
		boolean sameFolder, fromPaste = !StringUtil.isEmpty(isCopyStr);
		boolean isCopy = "true".equals(isCopyStr);
		boolean confirm = !Boolean.parseBoolean(request
				.getParameter("noConfirm"));
		JSONArray src = new JSONArray(request.getParameter("src")), srcNames, moveTo = null;
		File srcFile, newDstFile, dstFile = new File(request
				.getParameter("dst")), folder;
		String filename, dropPosition = request.getParameter("dropPosition"), overwriteFilename = null;
		int i, j = src.length(), overwriteCount = 0;
		Object[] moveInfo;

		if (dropPosition.equals("append"))
			folder = dstFile;
		else
			folder = dstFile.getParentFile();
		if (folder == null)
			throw new Exception("无法复制到此目录。");
		for (i = 0; i < j; i++) {
			if (src.getString(i).isEmpty())
				throw new IllegalArgumentException("复制源含无效目录。");
			srcFile = new File(src.getString(i));
			filename = srcFile.getName();
			newDstFile = new File(folder, filename);
			if (FileUtil.isAncestor(srcFile, newDstFile, false))
				throw new IllegalArgumentException("上级目录 \"" + filename
						+ "\" 不能复制到下级目录。");
			if (newDstFile.exists()) {
				sameFolder = folder.equals(srcFile.getParentFile());
				if (fromPaste) {
					if (!isCopy && sameFolder)
						throw new IllegalArgumentException("同一目录内剪切 \""
								+ filename + "\" 无效。");
					// 非同一目录内的复制提示是否覆盖
					if (confirm && !sameFolder) {
						if (overwriteFilename == null)
							overwriteFilename = filename;
						overwriteCount++;
					}
				} else if (!sameFolder)
					throw new IllegalArgumentException("\"" + filename
							+ "\" 已经存在。");
			}
		}
		if (overwriteCount > 0)
			SysUtil.error("\""
					+ overwriteFilename
					+ "\""
					+ (overwriteCount > 1 ? (" 等 " + overwriteCount + " 项")
							: " ") + "已经存在，确定要覆盖吗？", "101");
		srcNames = new JSONArray();
		if (fromPaste) {
			moveTo = copyFiles(src, folder, !isCopy);
			j = moveTo.length();
			for (i = j - 1; i >= 0; i--) {
				moveInfo = (Object[]) moveTo.get(i);
				if (!(Boolean) moveInfo[1])
					srcNames.put(FileUtil.getFilename((String) moveInfo[0]));
			}
		} else {
			for (i = j - 1; i >= 0; i--) {
				srcNames.put(FileUtil.getFilename(src.getString(i)));
				// 移除无需移动的文件
				if (folder.equals(new File(src.getString(i)).getParentFile()))
					src.remove(i);
			}
			moveTo = doMoveFiles(src, folder);
		}
		// 设置文件在目录中的显示索引位置
		if ("module".equals(request.getParameter("type")))
			setFileIndex(folder, dstFile.getName(), srcNames, dropPosition);
		// 更改js和xwl中的模块引用路径
		JSONObject resp = new JSONObject();
		if (!isCopy) {
			Object[] result = changePath(src, moveTo);
			resp.put("files", result[0]);
			resp.put("change", result[1]);
		}
		resp.put("moveTo", moveTo);
		WebUtil.send(response, resp);
	}

	/**
	 * 移动模块文件时同步更新被引用的这些模块的URL路径。
	 * 
	 * @param source 源目录列表。
	 * @param dest 目标目录列表。
	 * @return 更改结果对象。
	 * @throws Exception
	 */
	private static Object[] changePath(JSONArray source, JSONArray dest)
			throws Exception {
		JSONArray rows = new JSONArray();
		ArrayList<Object[]> changes = new ArrayList<Object[]>();
		Object[] change, result;
		File srcFile, dstFile;
		String srcPath, dstPath;
		boolean isDir, changed = false;
		Object value;
		int i, j = source.length();

		for (i = 0; i < j; i++) {
			srcFile = new File(source.getString(i));
			value = dest.opt(i);
			if (value instanceof String)
				dstFile = new File((String) value);
			else
				dstFile = new File((String) ((Object[]) value)[0]);
			isDir = dstFile.isDirectory();
			if (FileUtil.isAncestor(Base.modulePath, srcFile)
					&& FileUtil.isAncestor(Base.modulePath, dstFile)
					&& (isDir || srcFile.getName().endsWith(".xwl")
							&& dstFile.getName().endsWith(".xwl"))) {
				srcPath = FileUtil.getModulePath(srcFile);
				dstPath = FileUtil.getModulePath(dstFile);
				if (UrlBuffer.change(srcPath, dstPath, isDir))
					changed = true;
				if (!isDir) {
					srcPath = srcPath.substring(0, srcPath.length() - 4);
					dstPath = dstPath.substring(0, dstPath.length() - 4);
				}
				change = new Object[2];
				if (isDir) {
					change[0] = Pattern.compile("\\bxwl=" + srcPath + "\\b/");
					change[1] = "xwl=" + dstPath + "/";
				} else {
					change[0] = Pattern.compile("(\\bxwl=" + srcPath
							+ "\\b)(?![/\\-\\.])");
					change[1] = "xwl=" + dstPath;
				}
				changes.add(change);
			}
		}
		if (changed)
			UrlBuffer.save();
		doChangePath(Base.modulePath, changes, rows);
		doChangePath(new File(Base.path, "wb/script"), changes, rows);
		result = new Object[2];
		result[0] = rows;
		result[1] = changes;
		return result;
	}

	/**
	 * 更改指定目录下所有模块和js文件对特定模块的引用路径。
	 * 
	 * @param folder 更改的目录。
	 * @param changes 需要更改的路径列表。
	 * @param rows 更改的结果存储在该对象。
	 * @throws Exception 更改过程发生异常。
	 */
	private static void doChangePath(File folder, ArrayList<Object[]> changes,
			JSONArray rows) throws Exception {
		File files[] = FileUtil.listFiles(folder);
		String fileExt;

		for (File file : files) {
			if (file.isDirectory())
				doChangePath(file, changes, rows);
			else {
				fileExt = FileUtil.getFileExt(file.getName()).toLowerCase();
				if (fileExt.equals("xwl") || fileExt.equals("js")) {
					String text = FileUtil.readString(file), replacedText = text;
					for (Object[] change : changes) {
						replacedText = ((Pattern) change[0]).matcher(
								replacedText).replaceAll((String) change[1]);
					}
					if (!replacedText.equals(text)) {
						FileUtil.syncSave(file, replacedText);
						JSONObject row = new JSONObject();
						row.put("path", FileUtil.getPath(file));
						row.put("lastModified", DateUtil.getTimestamp(file
								.lastModified()));
						rows.put(row);
					}
				}
			}
		}
	}

	/**
	 * 复制文件或目录至目标目录。
	 * 
	 * @param src 需要复制的源文件或目录。
	 * @param dstFolder 复制的目标目录。
	 * @param deleteOld 复制完成后是否删除源文件/目录。
	 * @return 被复制的文件信息，0项复制后的文件或目录，1项目标是否存在。
	 * @throws Exception 复制过程中发生异常。
	 */
	private static JSONArray copyFiles(JSONArray src, File dstFolder,
			boolean deleteOld) throws Exception {
		int i, j = src.length();
		JSONArray newNames = new JSONArray();
		File file;
		Object[] object;

		for (i = 0; i < j; i++) {
			file = new File(src.getString(i));
			object = FileUtil.syncCopy(file, dstFolder);
			if (deleteOld)
				FileUtil.syncDelete(file, false);
			newNames.put(object);
		}
		return newNames;
	}

	/**
	 * 移动指定列表的文件至目标目录。
	 * 
	 * @param src 需要移动的文件列表。
	 * @param dstFile 移动的目标目录。
	 * @return 移动后的文件列表。
	 * @throws Exception 移动文件过程发生异常。
	 */
	private static JSONArray doMoveFiles(JSONArray src, File dstFile)
			throws Exception {
		int i, j = src.length();
		File file;
		Object[] object;
		JSONArray result = new JSONArray();

		for (i = 0; i < j; i++) {
			file = new File(src.getString(i));
			FileUtil.syncMove(file, dstFile);
			object = new Object[2];
			object[0] = FileUtil.getPath(new File(dstFile, file.getName()));
			object[1] = false;
			result.put(object);
		}
		return result;
	}

	/**
	 * 初始化IDE。
	 * 
	 * @param request 请求对象。
	 * @param response 响应对象。
	 */
	public static void initIDE(HttpServletRequest request,
			HttpServletResponse response) {
		getSysOut(request);
	}

	/**
	 * 设置并获取当前用户的输出缓冲区。
	 */
	private static QueueWriter getSysOut(HttpServletRequest request) {
		HttpSession session = request.getSession(true);
		QueueWriter out = (QueueWriter) session.getAttribute("sys.out");

		if (out == null) {
			out = new QueueWriter(Var.getInt("sys.ide.consoleBufferSize"));
			session.setAttribute("sys.out", out);
		}
		return out;
	}

	/**
	 * 获取图标样式数据列表。
	 * 
	 * @return 图表样式列表。
	 * @throws Exception
	 */
	public static ArrayList<String> getIconList() {
		File iconPath = new File(Base.path, "wb/images");
		File[] files = FileUtil.listFiles(iconPath);
		ArrayList<String> list = new ArrayList<String>();

		SortUtil.sort(files);
		for (File file : files) {
			if (file.isDirectory())
				continue;
			list.add(FileUtil.removeExtension(file.getName()) + "_icon");
		}
		return list;
	}

	/**
	 * 压缩应用目录下的所有调试版本JS和CSS文件为发布版本。 
	 * 调试版本文件名以“-debug”结尾，发布版本文件名将去掉“-debug”后缀。
	 * 
	 * @throws IOException 压缩文件时发生异常。
	 */
	public static void compressScriptFile(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		boolean compressAll = Boolean.parseBoolean(request
				.getParameter("compressAll"));
		makeFile(request, Base.path, compressAll);
	}

	/**
	 * 检查指定路径是否超出权限非法访问。如果超出权限将抛出异常。
	 * @param path 检查的路径。
	 * @throws IOException 检查路径发生异常。
	 */
	public static void checkFilePerm(File path) throws IOException {
		String sysFolderBase = Var.getString("sys.ide.sysFolderBase");
		File base;

		if ("server".equals(sysFolderBase))
			base = Base.path.getParentFile().getParentFile();
		else if ("app".equals(sysFolderBase)) {
			if (FileUtil.isAncestor(new File(Base.path, "wb/system"), path)
					|| FileUtil.isAncestor(new File(Base.path, "META-INF"),
							path)
					|| FileUtil
							.isAncestor(new File(Base.path, "WEB-INF"), path))
				SysUtil.accessDenied();
			base = Base.path;
		} else
			base = null;
		if (base != null && (path == null || !FileUtil.isAncestor(base, path)))
			SysUtil.accessDenied();
	}

	/**
	 * 压缩指定目录下的所有调试版本JS和CSS文件为发布版本。 
	 * 调试版本文件名以“-debug”结尾，发布版本文件名将去掉“-debug”后缀。
	 * @param request 请求对象。
	 * @param folder 需要压缩文件的目标目录。
	 * @param compressAll 是否重新压缩全部。true重新压缩全部，false只压缩修改过的文件。
	 * @throws IOException 压缩文件时发生异常。
	 */
	public static void makeFile(HttpServletRequest request, File folder,
			boolean compressAll) throws IOException {
		File newFile, syncFile, files[] = FileUtil.listFiles(folder);
		String ext, name;

		for (File file : files) {
			if (file.isDirectory())
				makeFile(request, file, compressAll);
			else {
				name = file.getName();
				ext = FileUtil.getFileExt(name).toLowerCase();
				if (ext.equals("js") || ext.equals("css")) {
					name = FileUtil.removeExtension(name);
					if (name.endsWith("-debug")) {
						newFile = new File(file.getParent(), name.substring(0,
								name.length() - 6)
								+ "." + ext);
						if (compressAll
								|| file.lastModified() != newFile
										.lastModified()) {
							Console.log(request, "Compressing "
									+ file.toString());
							if (ext.equals("js"))
								ScriptCompressor.compressJs(file, newFile,
										false);
							else
								ScriptCompressor.compressCss(file, newFile);
							newFile.setLastModified(file.lastModified());
							syncFile = FileUtil.getSyncPath(newFile);
							if (syncFile != null)
								FileUtils.copyFile(newFile, syncFile);
						}
					}
				}
			}
		}
	}
}