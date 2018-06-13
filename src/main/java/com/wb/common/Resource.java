package com.wb.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;

import com.wb.util.DbUtil;
import com.wb.util.WebUtil;

/**
 * 存储和维护较大的文本或二进制数据至数据库。
 * 如果数据较小（长度小于255字节）可使用{@link Value}类。
 * 如果数据较小且对性能有高要求可使用{@link Var}类。
 * 
 * @see Value
 * @see Var
 */
public class Resource {
	/**
	 * 获取当前用户指定编号的文本资源。如果资源不存在将返回defaultValue。
	 * 
	 * @param request 当前用户请求的request对象。
	 * @param id 资源id编号。
	 * @param defaultValue 默认值。
	 * @return 获取的文本。
	 */
	public static String getString(HttpServletRequest request, String id,
			String defaultValue) {
		return getString(WebUtil.getIdWithUser(request, id), defaultValue);
	}

	/**
	 * 获取指定编号的文本资源。如果资源不存在将返回defaultValue。
	 * 
	 * @param id 资源id编号。
	 * @param defaultValue 默认值。
	 * @return 获取的文本。
	 */
	public static String getString(String id, String defaultValue) {
		try {
			byte[] bytes = getBytes(id, null);
			if (bytes == null)
				return defaultValue;
			else
				return new String(bytes, "utf-8");
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 获取指定编号的文本资源。如果资源不存在将返回null。
	 * 
	 * @param id 资源id编号。
	 * @return 获取的文本。
	 */
	public static String getString(String id) {
		return getString(id, null);
	}

	/**
	 * 获取当前用户指定编号的字节数组资源。如果资源不存在将返回defaultValue。
	 * 
	 * @param request 当前用户请求的request对象。
	 * @param id 资源id编号。
	 * @param defaultValue 默认值。
	 * @return 获取的字节数组。
	 */
	public static byte[] getBytes(HttpServletRequest request, String id,
			byte[] defaultValue) {
		return getBytes(WebUtil.getIdWithUser(request, id), defaultValue);
	}

	/**
	 * 获取指定编号的字节数组资源。如果资源不存在将返回defaultValue。
	 * 
	 * @param id 资源id编号。
	 * @param defaultValue 默认值。
	 * @return 获取的字节数组。
	 */
	public static byte[] getBytes(String id, byte[] defaultValue) {
		Connection conn = null;
		PreparedStatement st = null;
		ResultSet rs = null;

		try {
			conn = DbUtil.getConnection();
			st = conn
					.prepareStatement("select RES_CONTENT from WB_RESOURCE where RES_ID=?");
			st.setString(1, id);
			rs = st.executeQuery();
			if (rs.next()) {
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				InputStream is = rs.getBinaryStream(1);
				if (is != null) {
					try {
						IOUtils.copy(is, os);
					} finally {
						is.close();
					}
					return os.toByteArray();
				}
			}
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
	 * 把字符串作为资源存储到特定的数据库表中，并在当前用户下指定id编号标识。
	 * 如果值为null将删除id指定的值。
	 * @param request 当前用户请求的request对象。
	 * @param id 资源id编号。
	 * @param data 存储的字符串。
	 */
	public static void set(HttpServletRequest request, String id, String data) {
		set(WebUtil.getIdWithUser(request, id), data);
	}

	/**
	 * 把字符串作为资源存储到特定的数据库表中，并以指定id编号标识。
	 * 如果值为null将删除id指定的值。
	 *
	 * @param id 资源id编号。
	 * @param data 存储的字符串。
	 */
	public static void set(String id, String data) {
		try {
			byte[] bytes;

			if (data == null)
				bytes = null;
			else
				bytes = data.getBytes("utf-8");
			set(id, bytes);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 删除指定编号的资源。
	 * 
	 * @param id 资源id编号。
	 */
	public static void remove(String id) {
		set(id, (byte[]) null);
	}

	/**
	 * 删除当前用户指定编号的资源。
	 * 
	 * @param request 请求对象。
	 * @param id 资源id编号。
	 */
	public static void remove(HttpServletRequest request, String id) {
		set(request, id, (byte[]) null);
	}

	/**
	 * 把字节数组作为资源存储到特定的数据库表中，并以指定id编号标识。
	 * 如果值为null将删除id指定的值。
	 * @param id 资源id编号。
	 * @param data 字节数组值。
	 */
	public static void set(String id, byte[] data) {
		Connection conn = null;
		PreparedStatement st = null;
		boolean hasData = data != null;

		try {
			conn = DbUtil.getConnection();
			if (hasData)
				conn.setAutoCommit(false);
			st = conn
					.prepareStatement("delete from WB_RESOURCE where RES_ID=?");
			st.setString(1, id);
			st.executeUpdate();
			DbUtil.close(st);
			st = null;
			if (hasData) {
				st = conn
						.prepareStatement("insert into WB_RESOURCE values(?,?)");
				st.setString(1, id);
				st.setBinaryStream(2, new ByteArrayInputStream(data),
						data.length);
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

	/**
	 * 
	 * 把字节数组作为资源存储到特定的数据库表中，并在当前用户下指定id编号标识。
	 * 如果值为null将删除id指定的值。
	 * @param request 当前用户请求的request对象。
	 * @param id 资源id编号。
	 * @param data 字节数组值。
	 */
	public static void set(HttpServletRequest request, String id, byte[] data) {
		set(WebUtil.getIdWithUser(request, id), data);
	}
}
