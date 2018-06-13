package com.wb.common;

import java.io.File;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.util.FileUtil;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;

/**
 * 存储和维护较小的字符串、数值、日期等值至服务器文件并缓存在内存中。
 * 如果数据较大（长度大于255字节）可使用{@link Resource}类。
 * 如果数据较小且存储的数据的数量很多可使用{@link Value}类。
 * 
 * @see Resource
 * @see Value
 */
public class Var {
	/** 是否停止检查文件是否更新，以提高系统效率。 */
	public static boolean uncheckModified;
	/** 通过WebUtil.send发送流时是否采用gzip压缩。 */
	public static boolean sendStreamGzip;
	/** 通过WebUtil.send发送的内容超过该字节大小时采用gzip压缩。 */
	public static int sendGzipMinSize;
	/** 是否打印错误信息。 */
	public static boolean printError;
	/** 是否记录日志。 */
	public static boolean log;
	/** 是否记录计划任务日志。 */
	public static boolean taskLog;
	/** 限定每次最多返回记录数。 */
	public static int limitRecords;
	/** 限定每次最多导出记录数。 */
	public static int limitExportRecords;
	/** 指定文件超过该字节大小时采用gzip压缩并缓存在内存中。 */
	public static int gzipMinSize;
	/** 是否缓存文件，以提高并发时的快速响应。 */
	public static boolean cacheEnabled;
	/** 缓存时长，-1默认，0不缓存，>0缓存以秒为单位的时长。 */
	public static int cacheMaxAge;
	/** 是否调试模式。 */
	public static boolean debug;
	/**  在主页home模块是否显示移动App。 */
	public static boolean homeShowApp;
	/** 是否允许控制台信息打印方法。 */
	public static boolean consolePrint;
	/** 是否允许批量执行数据库更新操作。 */
	public static boolean batchUpdate;
	/** 服务器会话超时时间，单位秒。 */
	public static int sessionTimeout;
	/** 每个帐户是否只允许维持单个会话。 */
	public static boolean uniqueLogin;
	/** 对于浮点值类型的字段设置变量参数值时是否强制使用double类型。 */
	public static boolean useDouble;
	/** Ajax操作超时时间，单位毫秒。 */
	public static int ajaxTimeout;
	/** 多长时间操作未返回显示mask，单位毫秒。 */
	public static int maskTimeout;
	/** 客户端是否根据时差转换服务器端时间为本地时间。 */
	public static boolean useLocalTime;
	/** 是否记录Session对象。 */
	public static boolean recordSession;
	/** 当字符串类型字段长度大于该值时作为文本字段类型处理。 */
	public static int stringAsText;
	/** 同步目录路径。 */
	public static String syncPath;
	/** 默认jndi。 */
	public static String jndi;
	/** 指定语言。 */
	public static String language;
	/** 如果指定语言找不到时使用的默认语言。 */
	public static String defaultLanguage;
	/** 应用服务器对URL使用的编码。 */
	public static String urlEncoding;
	/** 以逗号分隔的存储到session属性值名称列表。 */
	public static String sessionVars;
	/** 是否强制转换字段名称为大写。 */
	public static boolean forceUpperCase;
	/** 把指定字符串映射为空字符串。 */
	public static String emptyString;
	/** 单次从数据库提取的建议性记录数。 */
	public static int fetchSize;
	/** 变量存放的文件。 */
	public static final File file = new File(Base.path, "wb/system/var.json");
	/**
	 * 把变量缓存到HashMap中，以提高访问性能。
	 */
	public static ConcurrentHashMap<String, Object> buffer;

	/**
	 * 获取指定名称的变量值。
	 * 
	 * @param name 变量全名。
	 * @return 变量值
	 */
	public static Object get(String name) {
		if (StringUtil.isEmpty(name))
			throw new NullPointerException("Var name \"" + name
					+ "\" can not be blank");
		Object val = buffer.get(name);
		if (val == null)
			throw new NullPointerException("Var \"" + name
					+ "\" does not exist");
		return val;
	}

	/**
	 * 获取指定名称的变量字符串值。
	 * 
	 * @param name 变量全名。
	 * @return 变量值
	 */
	public static String getString(String name) {
		return get(name).toString();
	}

	/**
	 * 获取指定名称的变量整数值。
	 * 
	 * @param name 变量全名。
	 * @return 变量值
	 */
	public static int getInt(String name) {
		Object val = get(name);
		if (val instanceof Integer)
			return (Integer) val;
		throw new RuntimeException("Var \"" + name
				+ "\" is not an integer value");
	}

	/**
	 * 获取指定名称的双精度值。
	 * 
	 * @param name 变量全名。
	 * @return 变量值
	 */
	public static double getDouble(String name) {
		Object val = get(name);
		if (val instanceof Double)
			return (Double) val;
		throw new RuntimeException("Var \"" + name + "\" is not a double value");
	}

	/**
	 * 获取指定名称的变量布尔值。
	 * 
	 * @param name 变量全名。
	 * @return 变量值
	 */
	public static boolean getBool(String name) {
		Object val = get(name);
		if (val instanceof Boolean)
			return (Boolean) val;
		throw new RuntimeException("Var \"" + name
				+ "\" is not a boolean value");
	}

	/**
	 * 更新缓存中的变量值并把变量值写进文件。
	 * 
	 * @param name 变量全名。
	 * @param value 变量值。
	 */
	public static synchronized void set(String name, Object value) {
		if (name == null)
			throw new NullPointerException("Null variable name");
		if (value == null)
			throw new NullPointerException("Null variable value");
		try {
			JSONObject object = JsonUtil.readObject(file);
			Object valObject = JsonUtil.getValue(object, name, '.');
			if (!(valObject instanceof JSONArray))
				throw new RuntimeException("\"" + name
						+ "\" is not a variable.");
			JSONArray valArray = (JSONArray) valObject;
			valArray.put(0, value);
			FileUtil.syncSave(file, object.toString(2));
			buffer.put(name, value);
			loadBasicVars();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 加载和初始化。
	 */
	public static synchronized void load() {
		try {
			buffer = new ConcurrentHashMap<String, Object>();
			JSONObject object = JsonUtil.readObject(file);
			getValues(object, "");
			loadBasicVars();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 遍历所有变量，并把其完整路径作为名称，放到缓存HashMap中。
	 * 
	 * @param object 变量JSONObject对象
	 * @param parentName 上级变量路径名称
	 */
	private static void getValues(JSONObject object, String parentName) {
		Set<Entry<String, Object>> items = object.entrySet();
		Object value;
		JSONArray jsonArray;
		String name;

		for (Entry<String, Object> item : items) {
			value = item.getValue();
			name = parentName + item.getKey();
			if (value instanceof JSONObject)
				getValues((JSONObject) value, name + '.');
			else {
				jsonArray = (JSONArray) value;
				if ("double".equals(jsonArray.getJSONObject(2).opt("type")))
					buffer.put(name, ((Number) jsonArray.opt(0)).doubleValue());
				else
					buffer.put(name, jsonArray.opt(0));
			}
		}
	}

	/**
	 * 加载经常使用的基本参数。
	 */
	public static void loadBasicVars() {
		uncheckModified = !Var.getBool("sys.cache.checkModified");
		sendStreamGzip = Var.getBool("sys.sendStreamGzip");
		sendGzipMinSize = Var.getInt("sys.sendGzipMinSize");
		printError = Var.getBool("sys.printError");
		log = Var.getBool("sys.log");
		taskLog = Var.getBool("sys.task.log");
		limitRecords = Var.getInt("sys.controls.limitRecords");
		limitExportRecords = Var.getInt("sys.controls.limitExportRecords");
		gzipMinSize = Var.getInt("sys.cache.gzipMinSize");
		cacheEnabled = Var.getBool("sys.cache.enabled");
		cacheMaxAge = Var.getInt("sys.cache.maxAge");
		debug = Var.getBool("sys.debug");
		homeShowApp = Var.getBool("sys.app.homeShowApp");
		consolePrint = Var.getBool("sys.ide.consolePrint");
		batchUpdate = Var.getBool("sys.db.batchUpdate");
		sessionTimeout = Var.getInt("sys.session.sessionTimeout");
		uniqueLogin = Var.getBool("sys.session.uniqueLogin");
		useDouble = Var.getBool("sys.db.useDouble");
		ajaxTimeout = Var.getInt("sys.session.ajaxTimeout");
		maskTimeout = Var.getInt("sys.session.maskTimeout");
		useLocalTime = Var.getBool("sys.locale.useLocalTime");
		recordSession = Var.getBool("sys.session.recordSession");
		stringAsText = Var.getInt("sys.db.stringAsText");
		syncPath = Var.getString("sys.ide.syncPath");
		jndi = Var.getString("sys.jndi.default");
		language = Var.getString("sys.locale.language");
		defaultLanguage = Var.getString("sys.locale.defaultLanguage");
		urlEncoding = Var.getString("sys.locale.urlEncoding");
		sessionVars = Var.getString("sys.session.sessionVars");
		forceUpperCase = Var.getBool("sys.db.forceUpperCase");
		emptyString = Var.getString("sys.db.emptyString");
		fetchSize = Var.getInt("sys.db.fetchSize");
	}
}