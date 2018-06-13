package com.wb.util;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期工具方法类。
 */
public class DateUtil {
	/**
	 * 格式化日期。
	 * 
	 * @param date 需要格式化的日期。
	 * @param format 日期格式。
	 * @return 格式化后的日期字符串。如果date为空则返回空字符串。
	 */
	public static String format(Date date, String format) {
		if (date == null)
			return "";
		SimpleDateFormat dateFormat = new SimpleDateFormat(format);
		return dateFormat.format(date);
	}

	/**
	 * 按yyyy-MM-dd HH:mm:ss格式化日期。
	 * 
	 * @param date 需要格式化的日期。
	 * @return 格式化后的日期字符串。如果date为空则返回空字符串。
	 */
	public static String format(Date date) {
		return format(date, "yyyy-MM-dd HH:mm:ss");
	}

	/**
	 * 把以长整数表示的时间戳转换为Timestamp对象。
	 * 
	 * @param time 时间戳值。
	 * @return 转换后的Timestamp对象。
	 */
	public static Timestamp getTimestamp(long time) {
		return new Timestamp(time);
	}

	/**
	 * 获取指定日期所在月的天数。
	 * 
	 * @param date 日期。
	 * @return 位于所在月的天数。
	 */
	public static int daysInMonth(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
	}

	/**
	 * 获取指定日期在所在月位于第几天。
	 * 
	 * @param date 日期。
	 * @return 位于所在月的第天几。
	 */
	public static int dayOfMonth(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		return cal.get(Calendar.DAY_OF_MONTH);
	}

	/**
	 * 获取指定日期的年部分值。
	 * 
	 * @param date 日期。
	 * @return 年部分值。
	 */
	public static int yearOf(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		return cal.get(Calendar.YEAR);
	}

	/**
	 * 获取指定日期在所在年位于第几天。
	 * 
	 * @param date 日期。
	 * @return 位于所在年的第几天。
	 */
	public static int dayOfYear(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		return cal.get(Calendar.DAY_OF_YEAR);
	}

	/**
	 * 获取指定日期在所在周位于第几天。
	 * 
	 * @param date 日期。
	 * @return 位于所在周的第几天。
	 */
	public static int dayOfWeek(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		return cal.get(Calendar.DAY_OF_WEEK);
	}

	/**
	 * 把日期转换成yyyy-MM-dd HH:mm:ss.fffffffff格式的字符串。
	 * 
	 * @param value 需要转换的日期。
	 * @return 转换后的字符串。
	 */
	public static String dateToStr(Date value) {
		if (value == null)
			return null;
		Timestamp t = new Timestamp(value.getTime());
		return t.toString();
	}

	/**
	 * 把以 yyyy-MM-dd HH:mm:ss[.f...] 格式的字符串转换成时间戳。如果value为null或空串返回null。
	 * 
	 * @param value 需要转换的字符串。
	 * @return 转换后的时间戳。
	 */
	public static Timestamp strToDate(String value) {
		if (StringUtil.isEmpty(value))
			return null;
		return Timestamp.valueOf(value);
	}

	/**
	 * 在指定日期上增加年数。
	 * 
	 * @param date 日期。
	 * @param years 增加的年数。
	 * @return 增加年数后的日期。
	 */
	public static Date incYear(Date date, int years) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(Calendar.YEAR, years);
		return cal.getTime();
	}

	/**
	 * 在指定日期上增加月数。
	 * 
	 * @param date 日期。
	 * @param months 增加的月数。
	 * @return 增加月数后的日期。
	 */
	public static Date incMonth(Date date, int months) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(Calendar.MONTH, months);
		return cal.getTime();
	}

	/**
	 * 获取指定日期的小时部分值。
	 * 
	 * @param date 日期。
	 * @return 小时值。
	 */
	public static int hourOfDay(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		return cal.get(Calendar.HOUR_OF_DAY);
	}

	/**
	 * 把指定毫秒数格式化成 HH:mm:ss.fff形式显示的字符串。小时值允许大于24小时显示。
	 * @param milliSecs 毫秒数。
	 * @return 格式化后的文本。
	 */
	public static String format(long milliSecs) {
		long h = milliSecs / 3600000, hm = milliSecs % 3600000;
		long m = hm / 60000, mm = hm % 60000;
		long s = mm / 1000, sm = mm % 1000;
		return StringUtil.concat(Long.toString(h), ":", Long.toString(m), ":",
				Long.toString(s), ".", Long.toString(sm));
	}

	/**
	 * 在指定日期上增加天数。
	 * 
	 * @param date 日期。
	 * @param days 增加的天数。
	 * @return 增加天数后的日期。
	 */
	public static Date incDay(Date date, long days) {
		return new Date(date.getTime() + 86400000 * days);
	}

	/**
	 * 在指定日期上增加秒数。
	 * 
	 * @param date 日期。
	 * @param days 增加的秒数。
	 * @return 增加秒数后的日期。
	 */
	public static Date incSecond(Date date, long seconds) {
		return new Date(date.getTime() + 1000 * seconds);
	}

	/**
	 * 计算两个日期相差的天数
	 * @param beginDate 开始日期。
	 * @param endDate 结束日期。
	 * @return 去掉小数后的相差天数。
	 */
	public static int getElapsedDays(Date beginDate, Date endDate) {
		return (int) ((endDate.getTime() - beginDate.getTime()) / 86400000);
	}

	/**
	 * 修正以字符串形式表示的时间值，如果时间不符合格式将修正该值。
	 * @param str 以字符串形式表达的时间值。
	 * @return 修正后的值。
	 */
	public static String fixTime(String str) {
		if (str.indexOf(':') == -1)
			return "00:00:00";
		int b = str.indexOf(' '), e = str.indexOf('.');
		if (b == -1)
			b = 0;
		else
			b++;
		if (e == -1)
			e = str.length();
		return str.substring(b, e);
	}

	/**
	 * 修正以字符串形式表示的日期时间值，如果日期时间不符合格式将修正该值。
	 * @param str 以字符串形式表达的日期时间值。
	 * @param dateOnly 是否只返回日期部分字符串。
	 * @return 修正后的值。
	 */
	public static String fixTimestamp(String str, boolean dateOnly) {
		int pos = str.indexOf(' ');
		String datePart, timePart = null, sec[];
		if (pos == -1) {
			datePart = str;
			if (!dateOnly)
				timePart = "00:00:00";
		} else {
			datePart = str.substring(0, pos);
			if (!dateOnly)
				timePart = str.substring(pos + 1);
		}
		sec = StringUtil.split(datePart, "-");
		if (sec.length == 3) {
			StringBuilder buf = new StringBuilder(dateOnly ? 10 : 30);
			buf.append(sec[0]);
			buf.append('-');
			if (sec[1].length() == 1)
				buf.append('0');
			buf.append(sec[1]);
			buf.append('-');
			if (sec[2].length() == 1)
				buf.append('0');
			buf.append(sec[2]);
			if (!dateOnly) {
				buf.append(' ');
				buf.append(timePart);
			}
			return buf.toString();
		} else
			return str;
	}
}
