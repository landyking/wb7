package com.wb.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import com.wb.util.DateUtil;
import com.wb.util.DbUtil;
import com.wb.util.WebUtil;

/**
 * 存储和维护较小的字符串、数值、日期等至数据库。
 * 如果数据较大（长度大于255字节）可使用{@link Resource}类。
 * 如果数据较小且对性能有高要求可使用{@link Var}类。
 * 
 * @see Resource
 * @see Var
 */
public class Value {
	/**
	 * 获取指定编号的整数值。如果值不存在返回defaultValue。
	 * 
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值。
	 */
	public static int getInt(String id, int defaultValue) {
		String val = getString(id, null);
		if (val == null)
			return defaultValue;
		else
			return Integer.parseInt(val);
	}

	/**
	 * 获取当前用户指定编号的整数值。如果值不存在返回defaultValue。
	 * 
	 * @param request 当前用户请求的request对象。
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值
	 */
	public static int getInt(HttpServletRequest request, String id,
			int defaultValue) {
		return getInt(WebUtil.getIdWithUser(request, id), defaultValue);
	}

	/**
	 * 获取指定编号的长整数值。如果值不存在返回defaultValue。
	 * 
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值。
	 */
	public static long getLong(String id, long defaultValue) {
		String val = getString(id, null);
		if (val == null)
			return defaultValue;
		else
			return Long.parseLong(val);
	}

	/**
	 * 获取当前用户指定编号的长整数值。如果值不存在返回defaultValue。
	 * 
	 * @param request 当前用户请求的request对象
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值。
	 */
	public static long getLong(HttpServletRequest request, String id,
			long defaultValue) {
		return getLong(WebUtil.getIdWithUser(request, id), defaultValue);
	}

	/**
	 * 获取指定编号的双精度值。如果值不存在返回defaultValue。
	 * 
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值。
	 */
	public static double getDouble(String id, double defaultValue) {
		String val = getString(id, null);
		if (val == null)
			return defaultValue;
		else
			return Double.parseDouble(val);
	}

	/**
	 * 获取当前用户指定编号的双精度值。如果值不存在返回defaultValue。
	 * 
	 * @param request 当前用户请求的request对象。
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值
	 */
	public static double getDouble(HttpServletRequest request, String id,
			double defaultValue) {
		return getDouble(WebUtil.getIdWithUser(request, id), defaultValue);
	}

	/**
	 * 获取指定编号的布尔值。如果值不存在返回defaultValue。
	 * 
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值。
	 */
	public static boolean getBool(String id, boolean defaultValue) {
		String val = getString(id, null);
		if (val == null)
			return defaultValue;
		else
			return Boolean.parseBoolean(val);
	}

	/**
	 * 获取当前用户指定编号的布尔值。如果值不存在返回defaultValue。
	 * 
	 * @param request 当前用户请求的request对象。
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值。
	 */
	public static boolean getBool(HttpServletRequest request, String id,
			boolean defaultValue) {
		return getBool(WebUtil.getIdWithUser(request, id), defaultValue);
	}

	/**
	 * 获取指定编号的日期值。如果值不存在返回defaultValue。
	 * 
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值。
	 */
	public static Timestamp getDate(String id, Timestamp defaultValue) {
		String val = getString(id, null);
		if (val == null)
			return defaultValue;
		else
			return Timestamp.valueOf(val);
	}

	/**
	 * 获取当前用户指定编号的日期值。如果值不存在返回defaultValue。
	 * 
	 * @param request 当前用户请求的request对象
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值。
	 */
	public static Timestamp getDate(HttpServletRequest request, String id,
			Timestamp defaultValue) {
		return getDate(WebUtil.getIdWithUser(request, id), defaultValue);
	}

	/**
	 * 获取当前用户指定编号的字符串值。如果值不存在返回defaultValue。
	 * 
	 * @param request 当前用户请求的request对象。
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值。
	 */
	public static String getString(HttpServletRequest request, String id,
			String defaultValue) {
		return getString(WebUtil.getIdWithUser(request, id), defaultValue);
	}

	/**
	 * 获取指定编号的字符串值。如果值不存在返回defaultValue。
	 * 
	 * @param id 值id编号。
	 * @param defaultValue 如果值不存在，返回此默认值。
	 * @return 获取的值。
	 */
	public static String getString(String id, String defaultValue) {
		Connection conn = null;
		PreparedStatement st = null;
		ResultSet rs = null;
		try {
			conn = DbUtil.getConnection();
			st = conn
					.prepareStatement("select VAL_CONTENT from WB_VALUE where VAL_ID=?");
			st.setString(1, id);
			rs = st.executeQuery();
			if (rs.next())
				return rs.getString(1);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		} finally {
			DbUtil.close(rs);
			DbUtil.close(st);
			DbUtil.close(conn);
		}
		return defaultValue;
	}

	/**
	 * 
	 * 把整数值存储到特定的数据库表中，并指定id编号标识。
	 * 
	 * @param id 值id编号
	 * @param value 存储的整数值
	 */
	public static void set(String id, int value) {
		set(id, Integer.toString(value));
	}

	/**
	 * 
	 * 把整数值存储到特定的数据库表中，并在当前用户下指定id编号标识。
	 * 
	 * @param request 当前用户请求的request对象
	 * @param id 值id编号
	 * @param value 存储的整数值
	 */
	public static void set(HttpServletRequest request, String id, int value) {
		set(WebUtil.getIdWithUser(request, id), value);
	}

	/**
	 * 
	 * 把浮点数值存储到特定的数据库表中，并指定id编号标识。
	 * 
	 * @param id 值id编号
	 * @param value 存储的浮点数值
	 */
	public static void set(String id, float value) {
		set(id, Float.toString(value));
	}

	/**
	 * 
	 * 把浮点数值存储到特定的数据库表中，并在当前用户下指定id编号标识。
	 * 
	 * @param request 当前用户请求的request对象
	 * @param id 值id编号
	 * @param value 存储的浮点数值
	 */
	public static void set(HttpServletRequest request, String id, float value) {
		set(WebUtil.getIdWithUser(request, id), value);
	}

	/**
	 * 
	 * 把长整数值存储到特定的数据库表中，并指定id编号标识。
	 * 
	 * @param id 值id编号
	 * @param value 存储的长整数值
	 */
	public static void set(String id, long value) {
		set(id, Long.toString(value));
	}

	/**
	 * 
	 * 把长整数值存储到特定的数据库表中，并在当前用户下指定id编号标识。
	 * 
	 * @param request 当前用户请求的request对象
	 * @param id 值id编号
	 * @param value 存储的长整数值
	 */
	public static void set(HttpServletRequest request, String id, long value) {
		set(WebUtil.getIdWithUser(request, id), value);
	}

	/**
	 * 
	 * 把双精度值存储到特定的数据库表中，并指定id编号标识。
	 * 
	 * @param id 值id编号
	 * @param value 存储的双精度值
	 */
	public static void set(String id, double value) {
		set(id, Double.toString(value));
	}

	/**
	 * 
	 * 把双精度值存储到特定的数据库表中，并在当前用户下指定id编号标识。
	 * 
	 * @param request 当前用户请求的request对象
	 * @param id 值id编号
	 * @param value 存储的双精度值
	 */
	public static void set(HttpServletRequest request, String id, double value) {
		set(WebUtil.getIdWithUser(request, id), value);
	}

	/**
	 * 
	 * 把布尔值存储到特定的数据库表中，并指定id编号标识。
	 * 
	 * @param id 值id编号
	 * @param value 存储的布尔值
	 */
	public static void set(String id, boolean value) {
		set(id, Boolean.toString(value));
	}

	/**
	 * 
	 * 把布尔值存储到特定的数据库表中，并在当前用户下指定id编号标识。
	 * 
	 * @param request 当前用户请求的request对象
	 * @param id 值id编号
	 * @param value 存储的布尔值
	 */
	public static void set(HttpServletRequest request, String id, boolean value) {
		set(WebUtil.getIdWithUser(request, id), value);
	}

	/**
	 * 
	 * 把日期值存储到特定的数据库表中，并指定id编号标识。
	 * 如果值为null将删除id指定的值。
	 * @param id 值id编号
	 * @param value 存储的日期
	 */
	public static void set(String id, Date value) {
		String v;
		if (value == null)
			v = null;
		else
			v = DateUtil.dateToStr(value);
		set(id, v);
	}

	/**
	 * 
	 * 把日期值存储到特定的数据库表中，并在当前用户下指定id编号标识。
	 * 如果值为null将删除id指定的值。
	 * @param request 当前用户请求的request对象
	 * @param id 值id编号
	 * @param value 存储的日期
	 */
	public static void set(HttpServletRequest request, String id, Date value) {
		set(WebUtil.getIdWithUser(request, id), value);
	}

	/**
	 * 删除当前用户指定编号的值。
	 * 
	 * @param id 值id编号
	 */
	public static void remove(HttpServletRequest request, String id) {
		set(request, id, (String) null);
	}

	/**
	 * 删除指定编号的值。
	 * 
	 * @param id 值id编号
	 */
	public static void remove(String id) {
		set(id, (String) null);
	}

	/**
	 * 把字符串值存储到特定的数据库表中，并在当前用户下指定id编号标识。
	 * 如果值为null将删除名称指定的值。
	 * 
	 * @param request 当前用户请求的request对象
	 * @param id 值id编号
	 * @param value 存储的字符串
	 */
	public static void set(HttpServletRequest request, String id, String value) {
		set(WebUtil.getIdWithUser(request, id), value);
	}

	/**
	 * 
	 * 把字符串值存储到特定的数据库表中，并指定id编号标识。
	 * 如果值为null将删除id指定的值。
	 * 
	 * @param id 值id编号
	 * @param value 存储的字符串
	 */
	public static void set(String id, String value) {
		Connection conn = null;
		PreparedStatement st = null;
		boolean hasValue = value != null;

		try {
			conn = DbUtil.getConnection();
			if (hasValue)
				conn.setAutoCommit(false);
			st = conn.prepareStatement("delete from WB_VALUE where VAL_ID=?");
			st.setString(1, id);
			st.executeUpdate();
			DbUtil.close(st);
			st = null;
			if (hasValue) {
				st = conn.prepareStatement("insert into WB_VALUE values(?,?)");
				st.setString(1, id);
				st.setString(2, value);
				st.executeUpdate();
				conn.commit();
			}
		} catch (Throwable e) {
			throw new RuntimeException(e);
		} finally {
			DbUtil.close(st);
			DbUtil.close(conn);
		}
	}
}
