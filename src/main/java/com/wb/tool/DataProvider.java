package com.wb.tool;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Dictionary;
import com.wb.common.KVBuffer;
import com.wb.common.Var;
import com.wb.util.DbUtil;
import com.wb.util.StringUtil;
import com.wb.util.WebUtil;

/**
 * 从数据库获取数据，并输出指定格式的脚本、图片或流数据至客户端。
 */
public class DataProvider {
	/** 请求对象。 */
	public HttpServletRequest request;
	/** 响应对象。 */
	public HttpServletResponse response;
	/** 记录集对象，用于输出数据。 */
	public ResultSet resultSet;
	/** 记录总数。 */
	public Long totalCount;
	/** 开始执行的时间。 */
	public long startTime;
	/** 开始行索引号，首行为1，包含该行。 */
	public long beginIndex = 1;
	/** 结束行索引号，首行为1，包含该行。 */
	public long endIndex = Long.MAX_VALUE;
	/** 输出的数据类别。可能值为array,object,tree,stream,download,image,gif,jpg,png,bmp */
	public String type;
	/** 字典定义的表名列表。 */
	public String[] dictTableNames;
	/** 字典定义中字段与表的对应关系。用于多表重名字段的表名指定。 */
	public String dictFieldsMap;
	/** 是否输出指定字段的键值字段，键值字段名称为“字段名称__V”。 */
	public boolean createKeyValues;
	/** 字段元数据定义，定义的字段将覆盖系统自动生成的字段定义，未定义的字段仍将使用系统自动
	 *生成的字段定义。设置为"-"将不生成定义。 */
	public String fields;
	/** 字段元数据定义附加项，定义的脚本将直接输出，该属性适用于包含function语法的字段定义。 */
	public String fieldsTag;
	/** 输出到数组的附加信息。 */
	public String tag;
	/** 查询时默认最多允许单次输出到客户端的记录数。 */
	public Integer limitRecords;
	/** 导出时默认最多允许单次输出到客户端的记录数。 */
	public Integer limitExportRecords;
	/** 是否创建列模型，默认为true。 */
	public boolean createColumns = true;
	/** 字段值转换为键值定义的名称列表。 */
	public String keyDefines;
	/** tree内置字段项。 */
	private static JSONArray treeMeta = new JSONArray(
			"[{name:'parentId',type:'auto',defaultValue:null,useNull:true},{name:'index',type:'int',defaultValue:-1,persist:false,convert:null},{name:'depth',type:'int',defaultValue:0,persist:false,convert:null},{name:'expanded',type:'bool',defaultValue:false,persist:false},{name:'expandable',type:'bool',defaultValue:true,persist:false},{name:'checked',type:'bool',defaultValue:null,persist:false},{name:'leaf',type:'bool',defaultValue:false},{name:'cls',type:'string',defaultValue:'',persist:false,convert:null},{name:'iconCls',type:'string',defaultValue:'',persist:false,convert:null},{name:'icon',type:'string',defaultValue:'',persist:false,convert:null},{name:'root',type:'bool',defaultValue:false,persist:false},{name:'isLast',type:'bool',defaultValue:false,persist:false},{name:'isFirst',type:'bool',defaultValue:false,persist:false},{name:'allowDrop',type:'bool',defaultValue:true,persist:false},{name:'allowDrag',type:'bool',defaultValue:true,persist:false},{name:'loaded',type:'bool',defaultValue:false,persist:false},{name:'loading',type:'bool',defaultValue:false,persist:false},{name:'href',type:'string',defaultValue:'',persist:false,convert:null},{name:'hrefTarget',type:'string',defaultValue:'',persist:false,convert:null},{name:'qtip',type:'string',defaultValue:'',persist:false,convert:null},{name:'qtitle',type:'string',defaultValue:'',persist:false,convert:null},{name:'qshowDelay',type:'int',defaultValue:0,persist:false,convert:null},{name:'children',type:'auto',defaultValue:null,persist:false,convert:null},{name:'visible',type:'bool',defaultValue:true,persist:false}]");

	/**
	 * 获得结果集生成的脚本内容，如果type不是array/object/tree将抛出异常。
	 * @throws Exception 读取数据库或输出过程发生异常。
	 */
	public String getScript() throws Exception {
		if ("array".equals(type) || StringUtil.isEmpty(type))
			return getArray(false);
		else if ("tree".equals(type))
			return getArray(true);
		else if ("object".equals(type))
			return getObject();
		throw new IllegalArgumentException("The type is invalid.");
	}

	/**
	 * 按类型直接输出获得的结果集内容，包括脚本，二进制流或图片等。
	 * @throws Exception 读取数据库或输出过程发生异常。
	 */
	public void output() throws Exception {
		String script;
		if ("array".equals(type) || StringUtil.isEmpty(type))
			script = getArray(false);
		else if ("tree".equals(type))
			script = getArray(true);
		else if ("object".equals(type))
			script = getObject();
		else {
			DbUtil.outputBlob(resultSet, request, response, type);
			return;
		}
		if (WebUtil.jsonResponse(request))
			WebUtil.send(response, script, true);
		else
			WebUtil.send(response, script);
	}

	/**
	 * 从结果集生成指定格式的数组字符串。
	 * @param 是否生成tree格式脚本。
	 * @return 生成的数组字符串。
	 * @throws Exception 读取过程发生异常。
	 */
	public String getArray(boolean isTree) throws Exception {
		int i, j, maxRecs;
		long count = 0;
		// 内置标记，标记值为1时仅生成rows数据，不生成字段定义和列模型数据
		boolean rowOnly = WebUtil.exists(request, "sys.rowOnly"), hasKeyDefine;
		boolean hasDict = dictTableNames != null, first = true, hasTotal = totalCount != null;
		Object object;
		String val, kdValue, names[], keyNames[];
		Object keyMaps[], kdMaps[];
		JSONArray sysMeta;
		JSONObject kd, dictFieldsObj;
		int types[];
		DictRecord dictRecord;
		StringBuilder buf = new StringBuilder();
		ResultSetMetaData meta = resultSet.getMetaData();

		if (WebUtil.exists(request, "sys.fromExport")) {
			if (limitExportRecords == null)
				maxRecs = Var.limitExportRecords;
			else if (limitExportRecords == -1)
				maxRecs = Integer.MAX_VALUE;
			else
				maxRecs = limitExportRecords;
		} else {
			if (limitRecords == null)
				maxRecs = Var.limitRecords;
			else if (limitRecords == -1)
				maxRecs = Integer.MAX_VALUE;
			else
				maxRecs = limitRecords;
		}
		j = meta.getColumnCount();
		names = new String[j];
		keyNames = new String[j];
		types = new int[j];
		if (createKeyValues)
			keyMaps = new Object[j];
		else
			keyMaps = null;
		if (StringUtil.isEmpty(dictFieldsMap))
			dictFieldsObj = null;
		else
			dictFieldsObj = new JSONObject(dictFieldsMap);
		hasKeyDefine = !StringUtil.isEmpty(keyDefines);
		if (hasKeyDefine) {
			kd = new JSONObject(keyDefines);
			kdMaps = new Object[j];
		} else {
			kd = null;
			kdMaps = null;
		}
		for (i = 0; i < j; i++) {
			names[i] = meta.getColumnLabel(i + 1);
			names[i] = DbUtil.getFieldName(names[i]);
			if (StringUtil.isEmpty(names[i]))
				names[i] = "FIELD" + Integer.toString(i + 1);
			if (createKeyValues) {
				if (hasDict) {
					dictRecord = Dictionary.find(dictTableNames, names[i]);
					if (dictRecord == null || dictRecord.keyName == null)
						keyMaps[i] = null;
					else
						keyMaps[i] = KVBuffer.buffer.get(dictRecord.keyName);
				} else
					keyMaps[i] = null;
			}
			if (hasKeyDefine) {
				kdValue = (String) kd.opt(names[i]);
				if (kdValue == null)
					kdMaps[i] = null;
				else
					kdMaps[i] = KVBuffer.buffer.get(kdValue);
			}
			keyNames[i] = StringUtil.quote(names[i] + "__V");
			names[i] = StringUtil.quote(names[i]);
			types[i] = meta.getColumnType(i + 1);
		}
		buf.append("{\"success\":true");
		if (!rowOnly && !"-".equals(fields)) {
			sysMeta = DbUtil.getFields(meta, createKeyValues ? dictTableNames
					: null, dictFieldsObj, kd);
			if (!StringUtil.isEmpty(fields))
				mergeFields(sysMeta, new JSONArray(fields));
			buf.append(",\"metaData\":{\"fields\":");
			if (isTree)
				buf.append(mergeFields(sysMeta, treeMeta).toString());
			else
				buf.append(sysMeta.toString());
			if (!StringUtil.isEmpty(fieldsTag)) {
				buf.insert(buf.length() - 1, ',' + fieldsTag.substring(1,
						fieldsTag.length() - 1));
			}
			buf.append('}');
		}
		if (isTree)
			buf.append(",\"children\":[");
		else {
			if (!rowOnly && (createColumns || hasDict)) {
				buf.append(",\"columns\":");
				buf.append(DbUtil.getColumns(meta, dictTableNames,
						dictFieldsObj, kd));
			}
			buf.append(",\"rows\":[");
		}
		while (resultSet.next()) {
			count++;
			if (count > maxRecs) {
				count--;
				break;
			}
			if (count < beginIndex)
				continue;
			else if (count > endIndex) {
				if (hasTotal)
					break;
				else
					continue;
			}
			if (first)
				first = false;
			else
				buf.append(',');
			buf.append('{');
			for (i = 0; i < j; i++) {
				if (i > 0)
					buf.append(',');
				object = DbUtil.getObject(resultSet, i + 1, types[i]);
				if (hasKeyDefine && kdMaps[i] != null)
					object = KVBuffer.getValue(
							((ConcurrentHashMap<?, ?>) kdMaps[i]), request,
							object);
				buf.append(names[i]);
				buf.append(':');
				if (isTree) {
					if (object == null)
						val = "null";
					else {
						val = object.toString();
						if (val.equals("[]") && "\"children\"".equals(names[i]))
							val = "[]";
						else
							val = StringUtil.encode(val);
					}
					buf.append(val);
				} else
					buf.append(StringUtil.encode(object));
				if (createKeyValues && keyMaps[i] != null) {
					buf.append(',');
					buf.append(keyNames[i]);
					buf.append(':');
					if (object == null)
						buf.append("null");
					else {
						val = KVBuffer.getValue(
								((ConcurrentHashMap<?, ?>) keyMaps[i]),
								request, object);
						buf.append(StringUtil.quote(val));
					}
				}
			}
			buf.append('}');
		}
		if (!hasTotal)
			totalCount = count;
		buf.append("],\"total\":");
		buf.append(totalCount);
		if (!StringUtil.isEmpty(tag)) {
			buf.append(',');
			buf.append(tag);
		}
		if (startTime > 0) {
			buf.append(",\"elapsed\":");
			buf.append(Long.toString(System.currentTimeMillis() - startTime));
		}
		buf.append("}");
		return buf.toString();
	}

	/**
	 * 从结果集首行记录生成指定格式的JSON对象字符串。
	 * @return 生成的JSON对象字符串。
	 * @throws Exception 读取过程发生异常。
	 */
	public String getObject() throws Exception {
		JSONObject jo = new JSONObject();

		if (resultSet.next()) {
			int i, j, type;
			ResultSetMetaData meta = resultSet.getMetaData();
			String key, kdValue;
			Object value;
			JSONObject kd;

			if (StringUtil.isEmpty(keyDefines))
				kd = null;
			else
				kd = new JSONObject(keyDefines);
			j = meta.getColumnCount();
			for (i = 0; i < j; i++) {
				type = meta.getColumnType(i + 1);
				key = meta.getColumnLabel(i + 1);
				key = DbUtil.getFieldName(key);
				if (StringUtil.isEmpty(key))
					key = "FIELD" + Integer.toString(i + 1);
				value = DbUtil.getObject(resultSet, i + 1, type);
				if (value == null)
					value = JSONObject.NULL;
				else {
					if (kd != null) {
						kdValue = (String) kd.opt(key);
						if (kdValue != null) {
							value = KVBuffer.getValue(KVBuffer.buffer
									.get(kdValue), request, value);
						}
					}
				}
				jo.put(key, value);
			}
		}
		return jo.toString();
	}

	/**
	 * 把dest中的项合并到source中，如果source中已经存在指定名称的项将被覆盖。
	 * @param source 合并项1。
	 * @param dest 合并项2。
	 * @return source本身。
	 */
	private JSONArray mergeFields(JSONArray source, JSONArray dest) {
		int i, j = source.length() - 1, k, l = dest.length();
		JSONObject sourceObj, destObj;
		String destName;

		for (k = 0; k < l; k++) {
			destObj = dest.getJSONObject(k);
			destName = destObj.getString("name");
			for (i = j; i >= 0; i--) {
				sourceObj = source.getJSONObject(i);
				if (destName.equals(sourceObj.getString("name"))) {
					source.remove(i);
					j--;
					break;
				}
			}
		}
		for (k = 0; k < l; k++) {
			source.put(dest.getJSONObject(k));
		}
		return source;
	}
}
