package com.wb.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

import com.wb.common.Dictionary;
import com.wb.common.FileBuffer;
import com.wb.common.KVBuffer;
import com.wb.common.ScriptBuffer;
import com.wb.common.Str;
import com.wb.common.UrlBuffer;
import com.wb.common.Var;
import com.wb.common.XwlBuffer;
import com.wb.interact.Controls;

/**
 * 系统工具方法类。
 */
public class SysUtil {
	/**
	 * 服务器端维护的维一ID号。
	 */
	private static long currentId = 0;
	/**
	 * 服务器ID号的首位字符。
	 */
	private static byte serverId0;
	/**
	 * 服务器ID号的次位字符。
	 */
	private static byte serverId1;
	/**
	 * 同步锁。
	 */
	private static Object lock = new Object();
	/**
	 * 36进制数字表。
	 */
	public static final byte[] digits = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
			'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W',
			'X', 'Y', 'Z' };

	/**
	 * 默认缓冲区大小。
	 */
	public static final int bufferSize = 4096;

	/**
	 * 把反射的方法存储到HashMap中，以提高对方法的访问性能。
	 */
	private static final ConcurrentHashMap<String, Method> methodBuffer = new ConcurrentHashMap<String, Method>();

	/**
	 * 获取服务器端唯一且正增长的13位长度编号。该编号由2位服务器编号字符串和11位基于时序动态
	 *生成的字符串组成。编号由26个大写字母和10个数字组成。该方法可产生唯一且正增长的id号，并
	 *可保证在公元2386年前的编码长度不超过11位（另2位为服务器编号）。该方法仅保证指定服务器
	 *内（前2位服务器编号）的编号唯一。如果要使用全球唯一的编号，请使用java.util.UUID。
	 * @return 指定服务器内唯一的id号。
	 * @see java.util.UUID#randomUUID()
	 */
	public static String getId() {
		long id;
		synchronized (lock) {
			if (currentId == 0) {
				currentId = (new Date()).getTime() * 10000;
				String serverId = Var.getString("sys.serverId");
				serverId0 = (byte) serverId.charAt(0);
				serverId1 = (byte) serverId.charAt(1);
			}
			id = currentId++;
		}
		return numToString(id);
	}

	/**
	 * 把长整数转换为36进制的字符串并在该字符串前加上服务器编号，作为整个字符串返回。
	 * @param num 需要转换的数值。
	 * @return 转换后的字符串。
	 */
	private static String numToString(long num) {
		byte buf[] = new byte[13], charPos = 12;
		long val;

		buf[0] = serverId0;
		buf[1] = serverId1;
		while ((val = num / 36) > 0) {
			buf[charPos--] = digits[(byte) (num % 36)];
			num = val;
		}
		buf[charPos] = digits[(byte) num];
		return new String(buf);
	}

	/**
	 * 获取指定异常对象的根异常信息。
	 * @param e 异常对象。
	 * @return 获取的根异常信息。
	 */
	public static String getRootError(Throwable e) {
		Throwable cause, c = e;
		String message;

		do {
			cause = c;
			c = c.getCause();
		} while (c != null);
		message = cause.getMessage();
		if (StringUtil.isEmpty(message))
			message = cause.toString();
		return StringUtil.toLine(message.trim());
	}

	/**
	 * 获取指定异常对象的根异常信息。
	 * @param e 异常对象。
	 * @return 获取的根异常信息。
	 */
	public static Throwable getRootExcept(Throwable e) {
		Throwable cause, c = e;

		do {
			cause = c;
			c = c.getCause();
		} while (c != null);
		return cause;
	}

	/**
	 * 执行含HttpServletRequest和HttpServletResponse参数的静态方法。
	 * @param classMethodName 包名、类名和方法名构成的全名。
	 * @param request 请求对象。
	 * @param response 响应对象。
	 * @throws Exception 执行方法过程发生异常。
	 */
	public static void executeMethod(String classMethodName,
			HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		Method method = methodBuffer.get(classMethodName);
		if (method == null) {
			// 无需使用同步。
			int pos = classMethodName.lastIndexOf('.');
			String className, methodName;
			if (pos == -1) {
				className = "";
				methodName = classMethodName;
			} else {
				className = classMethodName.substring(0, pos);
				methodName = classMethodName.substring(pos + 1);
			}
			Class<?> cls = Class.forName(className);
			method = cls.getMethod(methodName, HttpServletRequest.class,
					HttpServletResponse.class);
			methodBuffer.put(classMethodName, method);
		}
		method.invoke(null, request, response);
	}

	/**
	 * 把输入流对象转换成基于字节的输入流对象，并关闭原输入流对象。
	 * @param is 输入流。
	 * @return 字节输入流。
	 * @throws IOException 读取流过程发生异常。
	 */
	public static ByteArrayInputStream toByteArrayInputStream(InputStream is)
			throws IOException {
		if (is instanceof ByteArrayInputStream)
			return (ByteArrayInputStream) is;
		byte[] bytes;
		try {
			bytes = IOUtils.toByteArray(is);
		} finally {
			is.close();
		}
		return new ByteArrayInputStream(bytes);
	}

	/**
	 * 从Reader对象读取字符串。读取完成后关闭reader。
	 * @param reader Reader对象。
	 * @return 读取的字符串。
	 * @throws IOException 读取过程发生异常。
	 */
	public static String readString(Reader reader) throws IOException {
		try {
			char buf[] = new char[bufferSize];
			StringBuilder sb = new StringBuilder();
			int len;

			while ((len = reader.read(buf)) > 0) {
				sb.append(buf, 0, len);
			}
			return sb.toString();
		} finally {
			reader.close();
		}
	}

	/**
	 * 抛出异常信息。
	 * @param msg 异常信息。
	 * @throws RuntimeException 抛出异常。
	 */
	public static void error(String msg) {
		throw new RuntimeException(msg);
	}

	/**
	 * 抛出包含指定错误代码的异常信息。错误代码包含在错误信息中，并使用特定关键字引用。
	 * @param msg 异常信息。
	 * @param errorNo 错误代码。
	 * @throws RuntimeException 抛出异常。
	 */
	public static void error(String msg, String errorNo)
			throws RuntimeException {
		throw new RuntimeException(StringUtil.concat("#WBE", errorNo, ":", msg));
	}

	/**
	 * 判断指定对象是否是数组。
	 * @return true对象是数组，false对象不是数组。
	 */
	public static boolean isArray(Object object) {
		return object.getClass().isArray();
	}

	/**
	 * 把JS数组转换为Java数组。
	 * @return 转换后的Java数组。
	 */
	public static Object[] javaArray(Object[] value) {
		return value;
	}

	/**
	 * 把JS数组转换为Java整数数组。
	 * @return 转换后的Java整数数组。
	 */
	public static Integer[] javaIntArray(Integer[] value) {
		return value;
	}

	/**
	 * 把对象类型值转换为字符串类型值。
	 * @return 转换后的字符串值。
	 */
	public static String javaString(Object value) {
		return value.toString();
	}

	/**
	 * 把对象类型值转换为整数类型值。
	 * @return 转换后的值。
	 */
	public static Integer javaInt(Object value) {
		if (value == null)
			return null;
		else if (value instanceof Number)
			return ((Number) value).intValue();
		else
			return Integer.parseInt(value.toString());
	}

	/**
	 * 把对象类型值转换为长整数类型值。
	 * @return 转换后的值。
	 */
	public static Long javaLong(Object value) {
		if (value == null)
			return null;
		else if (value instanceof Number)
			return ((Number) value).longValue();
		else
			return Long.parseLong(value.toString());
	}

	/**
	 * 把对象类型值转换为浮点数类型值。
	 * @return 转换后的值。
	 */
	public static Float javaFloat(Object value) {
		if (value == null)
			return null;
		else if (value instanceof Number)
			return ((Number) value).floatValue();
		else
			return Float.parseFloat(value.toString());
	}

	/**
	 * 把对象类型值转换为双精度类型值。
	 * @return 转换后的值。
	 */
	public static Double javaDouble(Object value) {
		if (value == null)
			return null;
		else if (value instanceof Number)
			return ((Number) value).doubleValue();
		else
			return Double.parseDouble(value.toString());
	}

	/**
	 * 把JS类型值转换为Java布尔类型值。
	 * @return 转换后的Java值。
	 */
	public static Boolean javaBool(Boolean value) {
		return value;
	}

	/**
	 * 判断指定对象是否是Map。
	 * @return true对象是Map，false对象不是Map。
	 */
	public static boolean isMap(Object object) {
		return object instanceof Map<?, ?>;
	}

	/**
	 * 判断指定对象是否是可遍历的Iterable对象。
	 * @return true是，false不是。
	 */
	public static boolean isIterable(Object object) {
		return object instanceof Iterable<?>;
	}

	/**
	 * 重新热加载系统。
	 * @param type 加载类型：1全部，2非数据库相关类，3数据库相关类。
	 */
	public static void reload(int type) {
		if (type == 1 || type == 2) {
			Var.load();
			Controls.load();
			FileBuffer.load();
			ScriptBuffer.load();
			Str.load();
			UrlBuffer.load();
			XwlBuffer.load();
		}
		if (type == 1 || type == 3) {
			KVBuffer.load();
			Dictionary.load();
		}
	}

	/**
	 * 获取二维数组内指定的值。索引0存放键，索引1存放值，根据键获取值。
	 * @param data 二维数组。
	 * @param key 键名
	 * @return 键名对应的值。
	 */
	public static Object getValue(Object[][] data, String key) {
		for (Object[] item : data) {
			if (key.equals(item[0]))
				return item[1];
		}
		return null;
	}

	/**
	 * 抛出包含Access denied信息的异常。
	 */
	public static void accessDenied() {
		throw new RuntimeException("Access denied.");
	}

	/**
	 * 获取指定对象的类fields和methods成员名称组成的列表。
	 * @return 对象的类成员名称列表。
	 */
	public static ArrayList<String> getMembers(Object object) {
		ArrayList<String> list = new ArrayList<String>();
		Class<?> cls = object.getClass();
		Field[] fields = cls.getFields();
		Method[] methods = cls.getMethods();
		String name;

		for (Field field : fields) {
			name = field.getName();
			if (list.indexOf(name) == -1)
				list.add(name);
		}
		for (Method method : methods) {
			name = method.getName();
			if (list.indexOf(name) == -1)
				list.add(name);
		}
		return list;
	}

	/**
	 * 获取本机的mac地址，如果获取过程发生异常返回"invalid"字符串。
	 * @return 本机mac地址或invalid字符串。
	 */
	public static String getMacAddress() {
		try {
			NetworkInterface network = NetworkInterface
					.getByInetAddress(InetAddress.getLocalHost());
			byte[] mac = network.getHardwareAddress();
			StringBuilder sb = new StringBuilder();

			for (int i = 0; i < mac.length; i++) {
				sb.append(String.format("%02X%s", mac[i],
						(i < mac.length - 1) ? "-" : ""));
			}
			return sb.toString();
		} catch (Throwable e) {
			return "invalid";
		}
	}

	/**
	 * 获取用户设置的输出至用户文件时换行符号。
	 * @return 换行符号。
	 */
	public static String getLineSeparator() {
		String sp = Var.getString("sys.locale.lineSeparator");
		if ("\\r".equals(sp))
			return "\r";
		else if ("\\n".endsWith(sp))
			return "\n";
		else
			return "\r\n";
	}

	/**
	 * 把数组列表项内的所有值添加到HashSet中。
	 * @param <T> 指定类型。
	 * @param items 列表项数组。
	 * @return 所有列表项添加到HashSet值。
	 */
	public static <T> HashSet<T> toHashSet(T[] items) {
		HashSet<T> hashSet = new HashSet<T>();
		for (T item : items) {
			hashSet.add(item);
		}
		return hashSet;
	}
}
