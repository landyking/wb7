package com.wb.common;

import java.io.File;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;

import com.wb.util.FileUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;

/**
 * 支持多国语言的文字、数字和日期等。
 */
public class Str {
	/**
	 * Wb的多语言HashMap。HashMap中名称为语言名称，值为文字列表组成的map。
	 */
	private static ConcurrentHashMap<String, ConcurrentHashMap<String, String>> wbLang;
	/**
	 * Ext的多语言HashMap，用于获取所支持的语言种类。
	 */
	private static ConcurrentHashMap<String, String> extLang;
	/**
	 * Touch的多语言HashMap，用于获取所支持的语言种类。
	 */
	private static ConcurrentHashMap<String, String> touchLangList;
	/**
	 * 根据配置文件把同一语言不同名称转换为指定名称的映射HashMap。
	 */
	private static ConcurrentHashMap<String, String> langMap;

	/**
	 * 根据默认语言和输入参数，格式化指定的关键字。
	 * 
	 * @param key
	 *            预定义的字符串关键字。
	 * @param args
	 *            填充到关键字中的参数列表。
	 * @return 格式化后的字符串。
	 * @see Str#langFormat
	 */
	public static String format(String key, Object... args) {
		return langFormat(Var.defaultLanguage, key, args);
	}

	/**
	 * 根据当前客户端语言和输入参数，格式化指定的关键字。
	 * 
	 * @param request
	 *            包含当前语言的request对象。
	 * @param key
	 *            预定义的字符串关键字。
	 * @param args
	 *            填充到关键字中的参数列表。
	 * @return 格式化后的字符串。
	 * @see Str#langFormat
	 */
	public static String format(HttpServletRequest request, String key,
			Object... args) {
		return langFormat((String) request.getAttribute("sys.useLang"), key,
				args);
	}

	/**
	 * 把指定语言转换为系统所支持的最接近的语言，如果无法匹配将返回系统的默认语言。
	 * 比如系统支持en_AU, en_GB, en 3种英文，如果请求语言为en_GB，则返回en_GB。
	 * 如果请求的是en_CA或en_US则返回最接近的en。如果未找到任何匹配则返回默认语言。
	 * 
	 * @param map
	 *            存放语言的HashMap
	 * @param lang
	 *            需要转换的语言
	 * @return 转换后的语言
	 */
	private static String optLang(ConcurrentHashMap<String, ?> map, String lang) {
		if (!StringUtil.isEmpty(lang)) {
			if (map.containsKey(lang))
				return lang;
			int pos = lang.indexOf('_');
			if (pos != -1) {
				lang = lang.substring(0, pos);
				if (map.containsKey(lang))
					return lang;
			}
		}
		return Var.defaultLanguage;
	}

	/**
	 * 把指定语言转换为wb所支持的语言，如果未找到匹配则返回默认语言。
	 * 
	 * @param lang
	 *            需要转换的语言
	 * @return 转换后的语言
	 * @see Str#optLang
	 */
	public static String optLanguage(String lang) {
		return optLang(wbLang, lang);
	}

	/**
	 * 把指定语言转换为ext所支持的语言，如果未找到匹配则返回默认语言。
	 * 
	 * @param lang 需要转换的语言
	 * @return 转换后的语言
	 * @see Str#optLang
	 */
	public static String optExtLanguage(String lang) {
		return optLang(extLang, lang);
	}

	public static String optTouchLanguage(String lang) {
		return optLang(touchLangList, lang);
	}

	/**
	 * 根据配置文件映射不同名称的语言为指定的名称。比如根据配置zh_HANS-CN或zh_HANS可被映射为zh_CN。
	 * 
	 * @param lang
	 *            需要映射的语言名称
	 * @return 映射后的语言名称
	 */
	public static String getMappedLang(String lang) {
		return langMap.get(lang);
	}

	/**
	 * 根据当前的客户端语言，格式化关键字并填充参数，把指定关键字转换为格式化后的文本。
	 * 
	 * @param lang 客户端语言名称。
	 * @param key 需要转换的带有参数的关键字。
	 * @param args 参数列表
	 * @return 格式化后的文本。
	 */
	public static String langFormat(String lang, String key, Object... args) {
		ConcurrentHashMap<String, String> buffer = wbLang
				.get(optLanguage(lang));
		if (buffer == null)
			return key;
		String str = buffer.get(key);
		if (str == null)
			return key;
		int i = 0;
		for (Object object : args)
			str = StringUtil.replaceAll(str, "{" + (i++) + "}",
					object == null ? "null" : object.toString());
		return str;
	}

	/**
	 * 加载和初始化。
	 */
	public static synchronized void load() {
		try {
			langMap = new ConcurrentHashMap<String, String>();
			wbLang = new ConcurrentHashMap<String, ConcurrentHashMap<String, String>>();
			ConcurrentHashMap<String, String> buffer;
			File[] fs = FileUtil.listFiles(new File(Base.path,
					"wb/script/locale"));
			JSONObject jo;
			String name, langList[];
			Set<Entry<String, Object>> es;

			// 建立同一语言不同名称的映射关系
			jo = JsonUtil.readObject(new File(Base.path,
					"wb/system/language.json"));
			es = jo.entrySet();
			for (Entry<String, Object> e : es) {
				langList = StringUtil.split((String) e.getValue(), ',', true);
				for (String ln : langList)
					langMap.put(ln, e.getKey());
			}

			// 把wb多语言文件所支持的语言种类和文件内容放到map中
			for (File file : fs) {
				name = file.getName();
				// 服务器端加载调试版本js，在后台使用无区别
				if (name.endsWith("-debug.js")) {
					buffer = new ConcurrentHashMap<String, String>();
					jo = JsonUtil.readObject(file);
					es = jo.entrySet();
					for (Entry<String, Object> e : es)
						buffer.put(e.getKey(), (String) e.getValue());
					wbLang.put(name.substring(8, name.length() - 9), buffer);
				}
			}

			// 把ext多语言文件所支持的语言种类放到map中
			extLang = new ConcurrentHashMap<String, String>();
			fs = FileUtil.listFiles(new File(Base.path, "wb/libs/ext/locale"));
			for (File file : fs) {
				name = file.getName();
				if (!name.endsWith("-debug.js")) {
					name = name.substring(9, name.length() - 3);
					extLang.put(name, name);
				}
			}

			// 把touch多语言文件所支持的语言种类放到map中
			touchLangList = new ConcurrentHashMap<String, String>();
			fs = FileUtil
					.listFiles(new File(Base.path, "wb/libs/touch/locale"));
			for (File f : fs) {
				name = f.getName();
				if (!name.endsWith("-debug.js")) {
					name = name.substring(7, name.length() - 3);
					touchLangList.put(name, name);
				}
			}

		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
}
