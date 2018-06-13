package com.wb.util;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * JSON工具方法类。
 */
public class JsonUtil {
	/**
	 * 获取JSONObject对象指定路径的值。如果值不存在将返回null。
	 * @param object JSONObject对象。
	 * @param path 路径。
	 * @param separator 路径分隔符。
	 * @return 获取的值。
	 */
	public static Object getValue(JSONObject object, String path, char separator) {
		if (path == null)
			throw new RuntimeException("null path value");
		if (path.isEmpty())
			return object;
		String item, items[] = StringUtil.split(path, separator);
		JSONObject obj = object;
		int i, j = items.length - 1;

		for (i = 0; i < j; i++) {
			item = items[i];
			obj = obj.optJSONObject(item);
			if (obj == null)
				return null;

		}
		return obj.opt(items[j]);
	}

	/**
	 * 设置JSONObject对象指定路径属性的值，如果属性不存在将创建该属性。如果值为null，将删除该属性。
	 * @param object 需要设置的JSONObject对象。
	 * @param path 属性路径。
	 * @param separator 路径分隔符。
	 * @param value 设置的值。
	 * @return object本身。
	 */
	public static JSONObject setValue(JSONObject object, String path,
			char separator, Object value) {
		if (StringUtil.isEmpty(path))
			throw new RuntimeException("Path is null or empty");
		String item, items[] = StringUtil.split(path, separator);
		JSONObject obj = object;
		int i, j = items.length - 1;

		for (i = 0; i < j; i++) {
			item = items[i];
			obj = obj.optJSONObject(item);
			if (obj == null) {
				throw new RuntimeException("Path \"" + path
						+ "\" does not exist.");
			}
		}
		obj.put(items[j], value);
		return object;
	}

	/**
	 * 读取文件中以utf-8格式存储的JSONObject字符串，并生成JSONObject对象。
	 * @param file 文件对象。
	 * @return 生成的JSONObject对象。
	 * @throws IOException 读取文件过程发生异常。
	 * @throws JSONException 文件中的数据不是一个有效的JSONObject字符串。
	 */
	public static JSONObject readObject(File file) throws IOException {
		String text = FileUtil.readString(file);
		if (text.isEmpty())
			return new JSONObject();
		else {
			try {
				int beginIndex = text.indexOf('{');
				String substring = text.substring(beginIndex);
				return new JSONObject(substring);
			} catch (Throwable e) {
				throw new JSONException("Invalid JSONObject: "
						+ StringUtil.ellipsis(text, 50));
			}
		}
	}

	/**
	 * 读取文件中以utf-8格式存储的JSONArray字符串，并生成JSONArray对象。
	 * @param file 文件对象。
	 * @return 生成的JSONArray对象。
	 * @throws IOException 读取文件过程发生异常。
	 * @throws JSONException 文件中的数据不是一个有效的JSONArray字符串。
	 */
	public static JSONArray readArray(File file) throws IOException {
		String text = FileUtil.readString(file);
		if (text.isEmpty())
			return new JSONArray();
		else
			try {
				return new JSONArray(text.substring(text.indexOf('[')));
			} catch (Throwable e) {
				throw new JSONException("Invalid JSONArray: "
						+ StringUtil.ellipsis(text, 30));
			}
	}

	/**
	 * 查找特定的包含某个键值的子JSONObject对象。
	 * @param jo 需要查找的JSONObject对象。
	 * @param itemsKey 子列表项的属性名称。
	 * @param key 查找的属性名称。
	 * @param value 查找的属性值。
	 * @return JSONObject对象。
	 */
	public static JSONObject findObject(JSONObject jo, String itemsKey,
			String key, String value) {
		if (jo.optString(key).equals(value))
			return jo;
		JSONArray ja = jo.optJSONArray(itemsKey);
		if (ja != null) {
			int i, j = ja.length();
			JSONObject item, result;

			for (i = 0; i < j; i++) {
				item = ja.optJSONObject(i);
				if (item != null) {
					result = findObject(item, itemsKey, key, value);
					if (result != null)
						return result;
				}
			}
		}
		return null;
	}

	/**
	 * 在由JSONObject组成的JSONArray中查找特定的JSONObject项。
	 * @param ja 查询的JSONArray
	 * @param key JSONobject包含的键名
	 * @param text 键名对应的值
	 * @return 查找到的JSONObject，如果没有找到返回null
	 */
	public static JSONObject findObject(JSONArray ja, String key, String text)
			throws Exception {
		int i, j = ja.length();
		JSONObject jo;

		for (i = 0; i < j; i++) {
			jo = ja.getJSONObject(i);
			if (jo.optString(key).equals(text))
				return jo;
		}
		return null;
	}

	/**
	 * 查找特定的包含某个键值的子JSONObject对象的上级JSONArray对象。
	 * @param jo 需要查找的JSONObject对象。
	 * @param itemsKey 子列表项的属性名称。
	 * @param key 查找的属性名称。
	 * @param value 查找的属性值。
	 * @return JSONArray对象。
	 */
	public static JSONArray findArray(JSONObject jo, String itemsKey,
			String key, String value) {
		JSONArray ja = jo.optJSONArray(itemsKey);
		if (ja != null) {
			int i, j = ja.length();
			JSONObject item;
			JSONArray result;

			for (i = 0; i < j; i++) {
				item = ja.optJSONObject(i);
				if (item != null) {
					if (item.optString(key).equals(value))
						return ja;
					result = findArray(item, itemsKey, key, value);
					if (result != null)
						return result;
				}
			}
		}
		return null;
	}

	/**
	 * 在JSONArray对象中查找指定值的索引号。
	 * <p>
	 * 示例: <blockquote>
	 * 
	 * <pre>
	 * int index = JsonUtil.indexOf(jsonArray, &quot;value1&quot;);
	 * </pre>
	 * 
	 * </blockquote>
	 * 
	 * @param ja 需要查找的JSONArray对象
	 * @param value 需要查找的值
	 * @return 值所在的索引号
	 */
	public static int indexOf(JSONArray ja, String value) {
		int i, j = ja.length();

		for (i = 0; i < j; i++) {
			if (value == null && ja.isNull(i) || value != null
					&& value.equals(ja.opt(i)))
				return i;
		}
		return -1;
	}

	/**
	 * 删除特定的包含某个键值的子JSONObject对象。
	 * @param jo 需要删除某个子项的JSONObject对象。
	 * @param itemsKey 子列表项的属性名称。
	 * @param key 查找的属性名称。
	 * @param value 查找的属性值。
	 * @return 以字符串形式表示的被删除的JSONObject对象。
	 */
	public static String remove(JSONObject jo, String itemsKey, String key,
			String value) {
		JSONArray ja = jo.optJSONArray(itemsKey);

		if (ja != null) {
			int i, j = ja.length();
			JSONObject item;
			String result;

			for (i = j - 1; i >= 0; i--) {
				item = ja.optJSONObject(i);
				if (item != null) {
					if (StringUtil.isEqual(item.optString(key, null), value)) {
						result = item.toString();
						ja.remove(i);
						return result;
					} else {
						result = remove(item, itemsKey, key, value);
						if (result != null)
							return result;
					}
				}
			}
		}
		return null;
	}

	/**
	 * 替换首个包含某个键值的子JSONObject对象为另一Object对象。
	 * @param jo 需要替换某个子项的JSONObject对象。
	 * @param itemsKey 子列表项的属性名称。
	 * @param key 查找的属性名称。
	 * @param value 查找的属性值。
	 * @param data 替换的对象。
	 * @return 找到对象并被替换返回true，否则返回false。
	 */
	public static boolean replace(JSONObject jo, String itemsKey, String key,
			String value, Object data) {
		JSONArray ja = jo.optJSONArray(itemsKey);
		if (ja != null) {
			int i, j = ja.length();
			JSONObject item;

			for (i = 0; i < j; i++) {
				item = ja.optJSONObject(i);
				if (item != null) {
					if (StringUtil.isEqual(item.optString(key, null), value)) {
						ja.put(i, data);
						return true;
					} else if (replace(item, itemsKey, key, value, data))
						return true;
				}
			}
		}
		return false;
	}

	/**
	 * 合并JSON数组中的所有项为字符串，每项之间以指定分隔符分隔。
	 * @param array 需要合并的JSON数组对象。
	 * @param separator 分隔符。
	 * @return 合并后组成的字符串。
	 */
	public static String join(JSONArray array, String separator) {
		int i, j = array.length();
		if (j == 0)
			return "";
		StringBuilder buf = new StringBuilder();
		for (i = 0; i < j; i++) {
			if (i > 0)
				buf.append(separator);
			buf.append(array.getString(i));
		}
		return buf.toString();
	}

	/**
	 * 把使用逗号分隔的字符串转换成JSONObject，key为分隔的文本内容，value为true。
	 * @param text 需要转换的文本。
	 * @return 转换后生成的JSONObject。如果text为空返回空JSONObject。
	 */
	public static JSONObject fromCSV(String text) {
		JSONObject jo = new JSONObject();
		if (StringUtil.isEmpty(text))
			return jo;
		String items[] = StringUtil.split(text, ',', true);
		for (String item : items) {
			jo.put(item, true);
		}
		return jo;
	}

	/**
	 * 获取JSONObject对象中指定名称的对象值，如果值为JSON.null或null则返回null。
	 * @param jo 需要获取值的JSONObject对象。
	 * @param key 名称。
	 * @return 指定名称的对象值。
	 */
	public static Object opt(JSONObject jo, String key) {
		if (jo.isNull(key))
			return null;
		else
			return jo.opt(key);
	}

	/**
	 * 获取JSONArray对象中指定名称的对象值，如果值为JSON.null或null则返回null。
	 * @param ja 需要获取值的JSONArray对象。
	 * @param index 索引值。
	 * @return 指定名称的对象值。
	 */
	public static Object opt(JSONArray ja, int index) {
		if (ja.isNull(index))
			return null;
		else
			return ja.opt(index);
	}

	/**
	 * 把dest中的项合并到source中，如果source已经存在相同名称的项将被覆盖。
	 * @param source 源对象。
	 * @param dest 目录对象。
	 * @return source源对象本身。
	 */
	public static void apply(JSONObject source, JSONObject dest) {
		Set<Entry<String, Object>> es = dest.entrySet();
		for (Entry<String, Object> e : es)
			source.put(e.getKey(), e.getValue());
	}
}