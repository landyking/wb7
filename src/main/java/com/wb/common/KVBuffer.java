package com.wb.common;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import com.wb.util.DbUtil;
import com.wb.util.StringUtil;

public class KVBuffer {
	/**
	 * 键值数据缓存。
	 */
	public static ConcurrentHashMap<String, ConcurrentHashMap<Object, String>> buffer;

	/**
	 * 获取指定键值的键值项列表数组组成的字符串。
	 * @param key 键名称。
	 * @return 键值项列表。
	 */
	public static String getList(String keyName) {
		ConcurrentHashMap<Object, String> map = buffer.get(keyName);
		if (map == null)
			return "[]";
		StringBuilder buf = new StringBuilder();
		Set<Entry<Object, String>> es = map.entrySet();
		boolean isFirst = true;
		Object K;
		String V;

		buf.append("[");
		for (Entry<Object, String> e : es) {
			if (isFirst)
				isFirst = false;
			else
				buf.append(",");
			buf.append("{\"K\":");
			K = e.getKey();
			if (K instanceof Integer)
				buf.append(Integer.toString((Integer) K));
			else
				buf.append(StringUtil.quote((String) K));
			buf.append(",\"V\":");
			V = e.getValue();
			if (V.startsWith("@"))
				V = V.substring(1);
			else
				V = StringUtil.quote(V);
			buf.append(V);
			buf.append("}");
		}
		buf.append("]");
		return buf.toString();
	}

	/**
	 * 加载和初始化。
	 */
	public static synchronized void load() {
		try {
			buffer = new ConcurrentHashMap<String, ConcurrentHashMap<Object, String>>();
			Connection conn = null;
			Statement st = null;
			ResultSet rs = null;
			String K, keyName = null, preKeyName = null;
			ConcurrentHashMap<Object, String> map = new ConcurrentHashMap<Object, String>();

			try {
				conn = DbUtil.getConnection();
				st = conn.createStatement();
				rs = st.executeQuery("select * from WB_KEY order by KEY_NAME");
				while (rs.next()) {
					keyName = rs.getString("KEY_NAME");
					if (preKeyName != null && !preKeyName.equals(keyName)) {
						buffer.put(preKeyName, map);
						map = new ConcurrentHashMap<Object, String>();
					}
					K = rs.getString("K");
					map.put(rs.getInt("TYPE") == 1 ? K : Integer.parseInt(K),
							rs.getString("V"));
					preKeyName = keyName;
				}
				if (preKeyName != null)
					buffer.put(preKeyName, map);
			} finally {
				DbUtil.close(rs);
				DbUtil.close(st);
				DbUtil.close(conn);
			}
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 获取指定键对应的值。
	 * @param map 键值对照map。
	 * @param request 请求对象，用于多国语言支持。
	 * @param key 键。
	 * @return 键对应的字符串值。
	 */
	public static String getValue(ConcurrentHashMap<?, ?> map,
			HttpServletRequest request, Object key) {
		Object value;
		String str;

		if (key instanceof Number)
			value = map.get(((Number) key).intValue());
		else
			value = map.get(key.toString());
		if (value == null)
			return key.toString();
		str = value.toString();
		if (str.startsWith("@Str."))
			return Str.format(request, str.substring(5));
		else
			return str;
	}
}