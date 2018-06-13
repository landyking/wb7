package com.wb.common;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.interact.Controls;
import com.wb.util.FileUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;

/**
 * 模块缓存器。
 */

public class XwlBuffer {
	/** 模块文件转换成JSONObject后缓存HashMap。 */
	private static ConcurrentHashMap<String, Object[]> buffer;

	/**
	 * 获取指定路径的模块文件内容的JSON对象。
	 * 
	 * @param path 请求的文件相对路径。
	 * @param silent 如果值为true且文件不存在返回null，否则将抛出异常。
	 * @return 模块内容JSON对象或null。
	 * @throws IOException 如果读取文件发生错误将抛出异常。
	 */
	public static JSONObject get(String path, boolean silent)
			throws IOException {
		if (path == null) {
			if (silent)
				return null;
			throw new NullPointerException("Module path is not specified.");
		}
		File file;
		Object[] obj;
		String pathKey;
		long lastModified;

		pathKey = path.toLowerCase();
		if (Var.uncheckModified) {
			file = null;
			lastModified = -1;
		} else {
			file = new File(Base.modulePath, path);
			lastModified = file.lastModified();
		}
		obj = buffer.get(pathKey);
		// 如果文件已经在缓存中中直接返回
		if (obj != null) {
			if (Var.uncheckModified || lastModified == (Long) obj[1])
				return (JSONObject) obj[0];
		}
		if (Var.uncheckModified) {
			file = new File(Base.modulePath, path);
			lastModified = file.lastModified();
		}
		// 判断文件是否存在
		if (lastModified == 0) {
			if (silent)
				return null;
			throw new IllegalArgumentException("Module \"" + path
					+ "\" is not found.");
		}
		JSONObject root = JsonUtil.readObject(file);
		JSONObject moduleNode = root.getJSONArray("children").getJSONObject(0)
				.getJSONObject("configs");
		// 把管理员权限设置到模块
		root.getJSONObject("roles").put("admin", 1);
		// 把module的loginRequired属性转换为布尔值并设置到根节点，以方便访问
		root.put("loginRequired", !"false".equals(moduleNode
				.opt("loginRequired")));
		boolean libTypes[] = optimize(root, true, SysUtil.getId() + ".");
		autoSetConfig(moduleNode, libTypes);
		if (moduleNode.optString("loadJS").indexOf("touch") != -1)
			root.put("hasTouch", true);
		obj = new Object[2];
		obj[0] = root;
		obj[1] = lastModified;
		buffer.put(pathKey, obj);
		return root;
	}

	/**
	 * 根据使用的控件自动设置模块模块项。
	 * @param root 模块根节点。
	 * @param moduleNode 模块配置项节点。
	 * @param libTypes 加载的控件库列表。
	 */
	private static void autoSetConfig(JSONObject moduleNode, boolean[] libTypes) {
		String loadJS = (String) moduleNode.opt("loadJS");

		if (loadJS == null) {
			ArrayList<String> libs = new ArrayList<String>();
			// 0项为未知控件
			if (libTypes[1])
				libs.add("ext");
			if (libTypes[2])
				libs.add("touch");
			if (libTypes[3])
				libs.add("bootstrap");
			moduleNode.put("loadJS", StringUtil.join(libs, '+'));
		}
	}

	/**
	 * 删除指定文件或目录的模块缓存。
	 * @param path 文件或目录的相对路径。
	 */
	public static void clear(String path) {
		Set<Entry<String, Object[]>> es = buffer.entrySet();
		String key, modulePath, delPath;
		Object[] value;

		delPath = StringUtil.concat(path, "/").toLowerCase();
		for (Entry<String, Object[]> e : es) {
			key = e.getKey();
			modulePath = StringUtil.concat(key, "/");
			if (modulePath.startsWith(delPath)) {
				value = e.getValue();
				// 根据模块的id号清除ServerScript缓存
				ScriptBuffer.remove(((JSONObject) value[0]).getJSONArray(
						"children").getJSONObject(0).getJSONObject("configs")
						.getString("id"));
				buffer.remove(key);
			}
		}
	}

	/**
	 * 加载和初始化。
	 */
	public static synchronized void load() {
		buffer = new ConcurrentHashMap<String, Object[]>();
	}

	/**
	 * 优化及设置模块。
	 * 
	 * @param data 模块数据对象。
	 * @param parentRoot 父节点是否是根控件。
	 * @param moduleId 用于标识模块节点的id。
	 * @return 加载的库列表。0 未知， 1 Ext, 2 Touch, 3 BS。
	 * @throws IOException 访问文件发生异常。
	 */
	private static boolean[] optimize(JSONObject data, boolean parentRoot,
			String moduleId) throws IOException {
		JSONArray children, ja = data.getJSONArray("children");
		JSONObject jo, configs, configItems, tag, meta, general, autoNames;
		String type, parentType;
		Object isConfig;
		int i, j = ja.length(), k, l;
		Integer lib;
		boolean asConfig, subLibTypes[], libTypes[] = new boolean[4];

		l = libTypes.length;
		if (j == 0)
			data.remove("children");
		else {
			parentType = (String) data.opt("type");
			for (i = 0; i < j; i++) {
				jo = ja.getJSONObject(i);
				type = (String) jo.opt("type");
				configs = (JSONObject) jo.opt("configs");
				meta = Controls.get(type);
				general = (JSONObject) meta.opt("general");
				tag = (JSONObject) general.opt("tag");
				if (tag != null) {
					lib = (Integer) tag.opt("lib");
					if (lib == null)
						lib = 0;
					else
						lib = (Integer) lib;
					libTypes[lib] = true;// 标识使用指定库的控件，便于系统自动选择加载
				}
				autoNames = (JSONObject) general.opt("autoNames");
				isConfig = configs.opt("isConfig");
				if (!parentRoot && autoNames != null && isConfig == null
						&& (autoNames.has(parentType) || autoNames.has("any")))
					asConfig = true;
				else
					asConfig = "true".equals(isConfig);
				// 如果指定控件为配置项，移动控件到配置项列表属性__configs中
				if (asConfig) {
					configItems = (JSONObject) data.opt("__configs");
					if (configItems == null) {
						configItems = new JSONObject();
						children = new JSONArray();
						configItems.put("children", children);
						data.put("__configs", configItems);
					} else
						children = (JSONArray) configItems.opt("children");
					children.put(jo);
					ja.remove(i);
					i--;
					j--;
				}
				if ("module".equals(type)) {
					configs.put("id", moduleId);
				} else if ("serverscript".equals(type)) {
					// id用于ServerScript缓存，id前缀必须保留moduleId，用于清除
					configs.put("id", StringUtil.concat(moduleId, SysUtil
							.getId()));
				}
				jo.remove("expanded");
				subLibTypes = optimize(jo, Boolean.TRUE.equals(general
						.opt("root")), moduleId);
				for (k = 0; k < l; k++) {
					if (subLibTypes[k])
						libTypes[k] = true;
				}
			}
			// 如果移动配置项节点后为空，删除children
			if (j == 0)
				data.remove("children");
		}
		return libTypes;
	}

	/**
	 * 判断指定模块或目录是否可显示。如果模块未隐藏且可访问则模块可显示，
	 * 如果目录下存在1个或以上可显示模块则目录可显示。
	 * @param file 需要判断的文件或目录。
	 * @param roles 当前用户的角色列表。
	 * @param type 1桌面应用，2移动应用，3权限设置。
	 * @return true可显示，false不可显示。
	 * @throws IOException 判断过程发生异常。
	 */
	public static boolean canDisplay(File file, String[] roles, int type)
			throws IOException {
		if (file.isDirectory()) {
			File configFile = new File(file, "folder.json");
			if (configFile.exists()) {
				JSONObject content = JsonUtil.readObject(configFile);
				if (type != 3 && Boolean.TRUE.equals(content.opt("hidden")))
					return false;
			}
			File[] files = FileUtil.listFiles(file);
			for (File subFile : files) {
				if (canDisplay(subFile, roles, type))
					return true;
			}
		} else if (file.getName().endsWith(".xwl")) {
			JSONObject content = get(FileUtil.getModulePath(file), false);
			if (type != 3) {
				if (content.has("hasTouch")) {
					if (type == 1 && !Var.homeShowApp)
						return false;
				} else if (type == 2)
					return false;
			}
			if ((type == 3 || Boolean.FALSE.equals(content.opt("hidden")))
					&& canAccess(content, roles))
				return true;
		}
		return false;
	}

	/**
	 * 判断指定角色列表对模块是否可访问。如果模块无需登录或包含权限中的任意一个角色返回true，
	 * 否则返回false。
	 * @param module 模块内容。
	 * @param roles 角色列表。
	 * @return true可访问，false不可访问。
	 */
	public static boolean canAccess(JSONObject module, String[] roles) {
		boolean noLoginRequired = Boolean.FALSE.equals(module
				.opt("loginRequired"));

		if (noLoginRequired)
			return true;
		if (roles == null)
			return false;
		JSONObject setRoles = (JSONObject) module.opt("roles");
		for (String role : roles) {
			if (setRoles.has(role))
				return true;
		}
		return false;
	}

	/**
	 * 判断当前请求对指定模块是否可访问。如果模块无需登录或包含权限中的任意一个角色返回true，
	 * 否则返回false。
	 * @param request 请求对象。
	 * @param path 模块路径。
	 * @return true可访问，false不可访问。
	 */
	public static boolean canAccess(HttpServletRequest request, String path)
			throws Exception {
		JSONObject module = XwlBuffer.get(FileUtil.getModuleFile(path), false);
		String roles[] = Session.getRoles(request);
		return canAccess(module, roles);
	}
}
