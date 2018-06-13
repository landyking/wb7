package com.wb.common;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;

import com.wb.tool.DictRecord;
import com.wb.util.DbUtil;
import com.wb.util.StringUtil;

/**
 * 数据库数据字典。
 */
public class Dictionary {
	/** 字典数据缓存。 */
	public static ConcurrentHashMap<String, ConcurrentHashMap<String, DictRecord>> buffer;

	/**
	 * 在指定表名的字典信息中查找指定字段名称的字典定义记录。
	 * @param tableNames 表名列表。
	 * @param fieldName 字段名称。
	 * @return 字典定义记录。
	 */
	public static DictRecord find(String[] tableNames, String fieldName) {
		DictRecord dictRecord;
		ConcurrentHashMap<String, DictRecord> fieldMap;
		String upperFieldName = fieldName.toUpperCase();
		for (String tableName : tableNames) {
			fieldMap = buffer.get(tableName.toUpperCase());
			if (fieldMap != null) {
				dictRecord = fieldMap.get(upperFieldName);
				if (dictRecord != null) {
					if (dictRecord.linkTo == null)
						return dictRecord;
					else {
						String tableField[] = StringUtil.split(
								dictRecord.linkTo.toUpperCase(), '.');
						fieldMap = buffer.get(tableField[0]);
						if (fieldMap == null)
							return null;
						else
							return fieldMap.get(tableField[1]);
					}
				}
			}
		}
		return null;
	}

	/**
	 * 获得结果集当前记录的字典定义对象。
	 * @param rs 结果集。
	 * @return 字典记录定义对象。
	 * @throws Exception 读取过程发生异常。
	 */
	public static DictRecord getDictRecord(ResultSet rs) throws Exception {
		DictRecord dictRecord = new DictRecord();
		dictRecord.linkTo = StringUtil.force(rs.getString("LINK_TO"));
		dictRecord.listable = !"0".equals(rs.getString("LISTABLE"));
		dictRecord.editable = !"0".equals(rs.getString("EDITABLE"));
		dictRecord.dispText = StringUtil.force(rs.getString("DISP_TEXT"));
		dictRecord.dispWidth = rs.getInt("DISP_WIDTH");
		if (rs.wasNull())
			dictRecord.dispWidth = -1;
		dictRecord.dispFormat = StringUtil.force(rs.getString("DISP_FORMAT"));
		dictRecord.autoWrap = "1".equals(rs.getString("AUTO_WRAP"));
		dictRecord.allowBlank = StringUtil
				.getBoolA(rs.getString("ALLOW_BLANK"));
		dictRecord.readOnly = StringUtil.getBoolA(rs.getString("READ_ONLY"));
		dictRecord.keyName = StringUtil.force(rs.getString("KEY_NAME"));
		dictRecord.fieldSize = rs.getInt("FIELD_SIZE");
		if (rs.wasNull())
			dictRecord.fieldSize = -1;
		dictRecord.decimalPrecision = rs.getInt("DECIMAL_PRECISION");
		if (rs.wasNull())
			dictRecord.decimalPrecision = -1;
		dictRecord.validator = StringUtil.force(rs.getString("VALIDATOR"));
		dictRecord.renderer = StringUtil.force(rs.getString("RENDERER"));
		return dictRecord;
	}

	/**
	 * 加载和初始化。
	 */
	public static synchronized void load() {
		try {
			buffer = new ConcurrentHashMap<String, ConcurrentHashMap<String, DictRecord>>();
			Connection conn = null;
			Statement st = null;
			ResultSet rs = null;
			String tableName = null, preTableName = null;
			ConcurrentHashMap<String, DictRecord> map = new ConcurrentHashMap<String, DictRecord>();
			try {
				conn = DbUtil.getConnection();
				st = conn.createStatement();
				rs = st
						.executeQuery("select * from WB_DICT order by TABLE_NAME");

				while (rs.next()) {
					tableName = rs.getString("TABLE_NAME").toUpperCase();
					if (preTableName != null && !preTableName.equals(tableName)) {
						buffer.put(preTableName, map);
						map = new ConcurrentHashMap<String, DictRecord>();
					}
					map.put(rs.getString("FIELD_NAME").toUpperCase(),
							getDictRecord(rs));
					preTableName = tableName;
				}
				if (preTableName != null)
					buffer.put(preTableName, map);
			} finally {
				DbUtil.close(rs);
				DbUtil.close(st);
				DbUtil.close(conn);
			}
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
}
