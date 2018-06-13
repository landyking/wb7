package com.wb.tool;

import com.wb.util.StringUtil;

public class QueueWriter implements java.io.Serializable {
	private static final long serialVersionUID = 2100540580239114561L;
	/** 打印缓冲区，支持多线程访问*/
	private StringBuffer buf;
	/** 缓冲区大小 */
	private int bufferSize;

	/** 构造函数。
	 * @param bufferSize 缓冲区大小。
	 */
	public QueueWriter(int bufferSize) {
		buf = new StringBuffer();
		this.bufferSize = bufferSize;
	}

	/**
	 * 打印对象的字符串信息。
	 * @param object 打印的对象。
	 * @param type 输出的类型。
	 * @param newLine 是否换行。
	 * @param encoded 是否被编码，被编码的内容可在客户端解码。
	 */
	public synchronized void print(Object object, String type, boolean encoded) {
		String string;
		if (object == null)
			string = "null";
		else
			string = object.toString();
		if (buf.length() > 0)
			buf.append(',');
		buf.append("{type:\"");
		buf.append(type);
		buf.append("\",msg:");
		buf.append(StringUtil.quote(string));
		if (encoded)
			buf.append(",encode:true");
		buf.append('}');
		if (buf.length() > bufferSize)
			clear();
	}

	/**
	 * 重载该对象转字符串的方法。
	 */
	public String toString() {
		return buf.toString();
	}

	/**
	 * 清空缓存中的所有内容。
	 */
	public void clear() {
		buf.delete(0, buf.length());
	}
}
