package com.wb.common;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;

import com.wb.util.FileUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;

/**
 * URL地址映射器，映射指定URL地址至对应的模块。
 */
public class UrlBuffer {
	/** URL映射地址缓存HashMap。 */
	public static ConcurrentHashMap<String, String> buffer;
	/**
	 * URL映射文件。
	 */
	private static final File file = new File(Base.path, "wb/system/url.json");

	/**
	 * 获取指定URL的模块相对路径。
	 * @param url URL地址。
	 * @return 模块相对路径。
	 */
	public static String get(String url) {
		return buffer.get(url);
	}

	/**
	 * 设置模块的URL地址。
	 * @param url URL地址。
	 * @param path 模块相对路径。
	 */
	public static void put(String url, String path) {
		buffer.put(url, path);
	}

	/**
	 * 检查指定文件设置的url是否存在。
	 * @param url 去掉前置"/"的URL地址。
	 * @param file 文件对象。
	 * @throws IOException 访问文件错误。
	 */
	public static boolean exists(String url, File file) throws IOException {
		if (!StringUtil.isEmpty(url)) {
			String urlPath = get('/' + url);
			String relPath;
			if (file == null)
				relPath = null;
			else
				relPath = FileUtil.getModulePath(file);
			if (urlPath != null
					&& (relPath == null || !StringUtil.isSame(urlPath, relPath)))
				return false;
		}
		return true;
	}

	/**
	 * 删除指定文件或目录的URL缓存。
	 * @param path 文件或目录的相对路径。
	 * @return 如果有记录被删除则返回true，否则返回false。
	 */
	public static boolean remove(String path) {
		Set<Entry<String, String>> es = buffer.entrySet();
		String key, modulePath, delPath;
		boolean result = false;

		delPath = StringUtil.concat(path, "/");
		for (Entry<String, String> e : es) {
			key = e.getKey();
			modulePath = StringUtil.concat(e.getValue(), "/");
			if (modulePath.startsWith(delPath) && key.length() > 1) {
				buffer.remove(key);
				if (!result)
					result = true;
			}
		}
		return result;
	}

	/**
	 * 加载和初始化。
	 */
	public static synchronized void load() {
		try {
			buffer = new ConcurrentHashMap<String, String>();
			JSONObject object = JsonUtil.readObject(file);
			Set<Entry<String, Object>> es = object.entrySet();
			for (Entry<String, Object> e : es)
				put(e.getKey(), (String) e.getValue());
			String portal = Var.getString("sys.portal");
			// 根地址设置
			if (portal.endsWith(".xwl"))
				put("/", portal);
			else
				put("/", buffer.get("/" + portal));
			put("/m", ""); // 模块入口地址
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 保存缓存中的数据至文件。
	 * @throws IOException 
	 */
	public static void save() throws Exception {
		// 该方法无需同步
		Set<Entry<String, String>> es = buffer.entrySet();
		JSONObject object = new JSONObject();
		String key;

		for (Entry<String, String> e : es) {
			key = e.getKey();
			if (!key.equals("/") && !key.equals("/m"))
				object.put(key, e.getValue());
		}
		FileUtil.syncSave(file, object.toString());
	}

	/**
	 * 查找指定模块文件对应的URL。
	 * @param file 模块文件。
	 * @return 如果找到则返回URL，否则返回null。
	 */
	public static String find(File file) {
		String path = FileUtil.getModulePath(file);
		if (path == null)
			return null;
		Set<Entry<String, String>> es = buffer.entrySet();
		String key;

		for (Entry<String, String> e : es) {
			key = e.getKey();
			if (path.equals(e.getValue()) && !key.equals("/"))
				return key.substring(1);
		}
		return null;
	}

	/**
	 * 更改URL对应的模块至新的目录。
	 * @param src 源文件/目录。
	 * @param dest 目标文件/目录。
	 * @param isDir 更改的是文件还是目录。
	 * @return 如果被更改返回true，否则返回false。
	 */
	public static boolean change(String src, String dest, boolean isDir) {
		Set<Entry<String, String>> es = buffer.entrySet();
		String path, key, value;
		int srcLen = src.length() + 1;
		boolean result = false;

		src = src + '/';
		if (isDir)
			dest = dest + '/';
		for (Entry<String, String> e : es) {
			key = e.getKey();
			value = e.getValue();
			path = StringUtil.concat(value, "/");
			if (path.startsWith(src)) {
				if (isDir)
					buffer.put(key, dest + value.substring(srcLen));
				else
					buffer.put(key, dest);
				if (!result)
					result = true;
			}
		}
		return result;
	}
}
