package com.wb.util;

import java.io.File;
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 排序工具方法类。
 */
public class SortUtil {
	/**
	 * 对指定文件列表中的文件/目录按名称正向排序。
	 * 目录将排列在文件之前。排序将影响files内文件的顺序。
	 * @param files 需要排序的文件列表。
	 * @return files对象本身。
	 */
	public static File[] sort(File[] files) {
		sort(files, 0, false);
		return files;
	}

	/**
	 * 对指定文件列表中的文件/目录按指定类别排序。
	 * 目录将排列在文件之前。排序将影响files内文件的顺序。
	 * 排序将影响files内文件的顺序。
	 * @param files 需要排序的文件列表。
	 * @param type 排序类别，0文件名，1文件大小，2文件类别，3修改日期。
	 * @param desc 是否逆向排序。
	 * @return files对象本身。
	 */
	public static File[] sort(File[] files, int type, boolean desc) {
		final int fType = type;
		final boolean fDesc = desc;
		final Collator collator = Collator.getInstance();
		Arrays.sort(files, new Comparator<File>() {
			public int compare(File f1, File f2) {
				switch (fType) {
				case 1:
					Long l1 = f1.isDirectory() ? -1 : f1.length();
					Long l2 = f2.isDirectory() ? -1 : f2.length();
					if (fDesc)
						return l2.compareTo(l1);
					return l1.compareTo(l2);
				case 2:
					CollationKey t1 = collator
							.getCollationKey(f1.isDirectory() ? "0" : "1"
									+ FileUtil.getFileType(f1).toLowerCase());
					CollationKey t2 = collator
							.getCollationKey(f2.isDirectory() ? "0" : "1"
									+ FileUtil.getFileType(f2).toLowerCase());
					if (fDesc)
						return t2.compareTo(t1);
					return t1.compareTo(t2);
				case 3:
					Long d1 = f1.lastModified();
					Long d2 = f2.lastModified();
					boolean b1 = f1.isDirectory();
					boolean b2 = f2.isDirectory();

					if (b1 && !b2)
						d1 = Long.MIN_VALUE;
					if (b2 && !b1)
						d2 = Long.MIN_VALUE;
					if (fDesc)
						return d2.compareTo(d1);
					return d1.compareTo(d2);
				default:
					int result;
					String file1 = f1.getName();
					String file2 = f2.getName();
					CollationKey k1 = collator.getCollationKey((f1
							.isDirectory() ? 0 : 1)
							+ FileUtil.removeExtension(file1).toLowerCase());
					CollationKey k2 = collator.getCollationKey((f2
							.isDirectory() ? 0 : 1)
							+ FileUtil.removeExtension(file2).toLowerCase());
					if (fDesc)
						result = k2.compareTo(k1);
					else
						result = k1.compareTo(k2);
					if (result == 0) {
						CollationKey ke1 = collator.getCollationKey((f1
								.isDirectory() ? 0 : 1)
								+ FileUtil.getFileExt(file1).toLowerCase());
						CollationKey ke2 = collator.getCollationKey((f2
								.isDirectory() ? 0 : 1)
								+ FileUtil.getFileExt(file2).toLowerCase());
						if (fDesc)
							result = ke2.compareTo(ke1);
						else
							result = ke1.compareTo(ke2);
					}
					return result;
				}
			}
		});
		return files;
	}

	/**
	 * 对数组中的字符串按正序进行排序。排序结果将影响list。
	 * @param list 需要排序的字符串数组。
	 * @return list本身。
	 */
	public static String[] sort(String[] list) {
		Arrays.sort(list, new Comparator<String>() {
			Collator collator = Collator.getInstance();

			public int compare(String s1, String s2) {
				CollationKey key1 = collator.getCollationKey(StringUtil.opt(s1)
						.toLowerCase());
				CollationKey key2 = collator.getCollationKey(StringUtil.opt(s2)
						.toLowerCase());
				return key1.compareTo(key2);
			}
		});
		return list;
	}

	/**
	 * 对Map中的键名按正序进行排序。
	 * @param map 需要排序的map。
	 * @return 排序后的Map Entry列表。
	 */
	public static <K, V> ArrayList<Entry<K, V>> sortKey(Map<K, V> map) {
		return sortKey(map, false);
	}

	/**
	 * 对Map中的键名按正序进行排序。
	 * @param map 需要排序的map。
	 * @param keyAsNumber 是否把键名按数字进行排序。
	 * @return 排序后的Map Entry列表。
	 */
	public static <K, V> ArrayList<Entry<K, V>> sortKey(Map<K, V> map,
			boolean keyAsNumber) {
		ArrayList<Entry<K, V>> list = new ArrayList<Entry<K, V>>(map.entrySet());
		final boolean keyAsNum = keyAsNumber;
		final Collator collator = Collator.getInstance();

		Collections.sort(list, new Comparator<Entry<K, V>>() {
			public int compare(Entry<K, V> e1, Entry<K, V> e2) {
				Object k1 = e1.getKey(), k2 = e2.getKey();
				if (keyAsNum) {
					if (k1 instanceof Number && k2 instanceof Number)
						return (int) Math.ceil(((Number) k1).doubleValue()
								- ((Number) k2).doubleValue());
					else
						return (int) Math.ceil(Double
								.parseDouble(k1.toString())
								- Double.parseDouble(k2.toString()));
				} else {
					CollationKey key1 = collator.getCollationKey(k1.toString()
							.toLowerCase());
					CollationKey key2 = collator.getCollationKey(k2.toString()
							.toLowerCase());
					return key1.compareTo(key2);
				}
			}
		});
		return list;
	}

	/**
	 * 对Map中的值按正序进行排序。
	 * @param map 需要排序的map。
	 * @return 排序后的Map Entry列表。
	 */
	public static <K, V> ArrayList<Entry<K, V>> sortValue(Map<K, V> map) {
		return sortValue(map, false);
	}

	/**
	 * 对Map中的值按正序进行排序。
	 * @param map 需要排序的map。
	 * @param keyAsNumber 是否把值按数字进行排序。
	 * @return 排序后的Map Entry列表。
	 */
	public static <K, V> ArrayList<Entry<K, V>> sortValue(Map<K, V> map,
			boolean keyAsNumber) {
		ArrayList<Entry<K, V>> list = new ArrayList<Entry<K, V>>(map.entrySet());
		final boolean keyAsNum = keyAsNumber;
		final Collator collator = Collator.getInstance();

		Collections.sort(list, new Comparator<Entry<K, V>>() {
			public int compare(Entry<K, V> e1, Entry<K, V> e2) {
				Object v1 = e1.getValue(), v2 = e2.getValue();
				if (keyAsNum) {
					if (v1 instanceof Number && v2 instanceof Number)
						return (int) Math.ceil(((Number) v1).doubleValue()
								- ((Number) v2).doubleValue());
					else
						return (int) Math.ceil(Double
								.parseDouble(v1.toString())
								- Double.parseDouble(v2.toString()));
				} else {
					CollationKey key1 = collator.getCollationKey(v1.toString()
							.toLowerCase());
					CollationKey key2 = collator.getCollationKey(v2.toString()
							.toLowerCase());
					return key1.compareTo(key2);
				}
			}
		});
		return list;
	}

	/**
	 * 对List进行排序，排序忽略大小写。
	 * @param list 需要排序的list。
	 * @return list本身。
	 */
	public static <T> List<T> sort(List<T> list) {
		Collections.sort(list, new Comparator<T>() {
			public int compare(T b1, T b2) {
				return b1.toString().toLowerCase().compareTo(
						b2.toString().toLowerCase());
			}
		});
		return list;
	}
}
