package com.wb.tool;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.KVBuffer;
import com.wb.common.Str;
import com.wb.common.Var;
import com.wb.util.JsonUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;

public class DataOutput {

	/**
	 * 向指定输出流中输出Excel xls/xlsx格式的文件数据。是否使用xls/xlsx格式由变量
	 * sys.service.excel.xlsx设置。
	 * @param request 请求对象。
	 * @param outputStream 输出流。输出数据后该流不关闭，如果有必要需要手动关闭。
	 * @param headers 标题列。标题列允许嵌套，并根据设置的嵌套自动合并单元格。
	 * 标题列所有单元格都为字符串格式。
	 * @param records 输出的记录数据。
	 * @param title 标题。标题将显示在首行并合并所在行所有单元格且居中显示。如果为null，
	 * 将不生成标题。
	 * @param dateFormat 当未指定格式时使用的默认日期格式。
	 * @param timeFormat 当未指定格式时使用的默认时间格式。
	 * @param neptune 客户端是否为海王星主题，海王星主题列较宽映射到表格时需要按比缩小。
	 */
	public static void outputExcel(HttpServletRequest request,
			OutputStream outputStream, JSONArray headers, JSONArray records,
			String title, String dateFormat, String timeFormat, boolean neptune)
			throws Exception {
		Workbook book;
		Sheet sheet;
		Object values[];
		int headerCols, headerRows, startRow = 0;
		JSONArray fields;

		// POI在导出XML格式数据且程序重新加载时提示create a memory leak，实际无影响
		// POI3.5之后版本在某些应用服务器上运行一直有该信息提示，测试无内存泄漏
		book = getBook();
		try {
			sheet = book.createSheet();
			if (title != null) {
				startRow = 1;
				sheet.createRow(0);
			}
			values = createHeaders(sheet, headers, startRow, neptune);
			headerCols = (Integer) values[0];
			headerRows = (Integer) values[1];
			fields = (JSONArray) values[2];
			if (title != null)
				createTitle(sheet, title, headerCols);
			startRow += headerRows;
			if (Var.getBool("sys.service.excel.freezePane"))
				sheet.createFreezePane(0, startRow);
			createRecords(request, sheet, records, fields, startRow,
					dateFormat, timeFormat);
			book.write(outputStream);
		} finally {
			book.close();
		}
	}

	/**
	 * 设置Sheet中的标题及其样式。
	 * @param sheet sheet对象。
	 * @param title 标题文本。
	 * @param headerCols 标题占的列数量。
	 */
	private static void createTitle(Sheet sheet, String title, int headerCols) {
		Cell cell;
		Row row = sheet.getRow(0);
		Object styles[] = createCellStyle(sheet.getWorkbook(), "title");

		row.setHeight((Short) styles[1]);
		sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headerCols - 1));
		cell = row.createCell(0);
		cell.setCellStyle((CellStyle) styles[0]);
		cell.setCellValue(title);
	}

	/**
	 * 创建列标题。
	 * @param sheet Sheet对象。
	 * @param headers 原始列标题列表。
	 * @param startRow 开始行索引号。
	 * @param neptune 客户端是否为海王星主题。
	 * @return 对象数组，0项列宽度，1项列高度，2项字段元数据。
	 */
	private static Object[] createHeaders(Sheet sheet, JSONArray headers,
			int startRow, boolean neptune) {
		int i, j, x, y, colspan, rowspan;
		Workbook book = sheet.getWorkbook();
		Object result[];
		JSONObject header;
		JSONArray processedHeaders = new JSONArray();
		Object values[] = prepareHeaders(sheet, headers, processedHeaders,
				startRow, neptune);
		Cell cell, cells[][] = (Cell[][]) values[0];
		Object[] styles = createCellStyle(book, "header");
		CellStyle baseStyle = (CellStyle) styles[0], style;

		j = processedHeaders.length();
		for (i = 0; i < j; i++) {
			header = processedHeaders.getJSONObject(i);
			x = header.getInt("x");
			y = header.getInt("y");
			colspan = Math.max(header.getInt("colspan"), 0);
			rowspan = Math.max(header.getInt("rowspan"), 0);
			if (colspan > 0 || rowspan > 0) {
				sheet.addMergedRegion(new CellRangeAddress(y + startRow, y
						+ startRow + rowspan, x, x + colspan));
			}
			cell = cells[x][y];
			style = book.createCellStyle();
			style.cloneStyleFrom(baseStyle);
			style.setAlignment(getAlignment(header.optString("titleAlign"),
					header.has("child") ? CellStyle.ALIGN_CENTER
							: CellStyle.ALIGN_LEFT));
			cell.setCellStyle(style);
			cell.setCellValue(header.optString("text"));
		}
		result = new Object[3];
		result[0] = cells.length;
		result[1] = cells[0].length;
		result[2] = values[1];
		return result;
	}

	/**
	 * 预处理列标题，创建单元格方阵，并标记每个header的x,y,colspan,rowspan属性。
	 * @param sheet Excel的Sheet对象。
	 * @param rawHeaders 原始列标题列表。
	 * @param processedHeaders 处理后的列标题列表。
	 * @param startRow 开始行索引号。
	 * @param neptune 客户端是否为海王星主题。
	 * @return 对象数组，0项单元格方阵，1项字段元数据。
	 */
	private static Object[] prepareHeaders(Sheet sheet, JSONArray rawHeaders,
			JSONArray processedHeaders, int startRow, boolean neptune) {
		JSONArray leafs = new JSONArray();
		JSONObject node;
		Object[] result = new Object[2];
		int i, j, maxDepth, k, l, width;
		int flexWidth = Var.getInt("sys.service.excel.flexColumnMaxWidth");
		Row row;
		Object[] styles = createCellStyle(sheet.getWorkbook(), "header");
		CellStyle style = (CellStyle) styles[0];
		Cell cell, cells[][];
		short rowHeight = (Short) styles[1];
		double rate;

		if (neptune)
			rate = 32.06;
		else
			rate = 36.55;
		leafs.put(0);// 0项为headers最大深度
		markParents(leafs, rawHeaders, null, 0);
		maxDepth = leafs.getInt(0);
		leafs.remove(0);// 移除深度值
		j = leafs.length();
		for (i = 0; i < j; i++) {
			node = leafs.getJSONObject(i);
			if (node.has("width"))
				width = node.getInt("width");
			else if (node.has("flex"))
				width = flexWidth;
			else
				width = 100;
			sheet.setColumnWidth(i, (int) (width * rate));
			node.put("rowspan", maxDepth - node.getInt("y"));
			do {
				node.put("colspan", node.getInt("colspan") + 1);
				if (!node.has("x")) {
					node.put("x", i);
					processedHeaders.put(node);
				}
			} while ((node = (JSONObject) node.opt("parent")) != null);
		}
		maxDepth++;// maxDepth设置为以1为开始索引
		cells = new Cell[j][maxDepth];
		for (k = 0; k < maxDepth; k++) {
			row = sheet.createRow(k + startRow);
			row.setHeight(rowHeight);
			for (l = 0; l < j; l++) {
				cell = row.createCell(l);
				cell.setCellStyle(style);
				cells[l][k] = cell;
			}
		}
		result[0] = cells;
		result[1] = leafs;
		return result;
	}

	/**
	 * 标识header所有节点的父节点及其节点深度。
	 * @param leafs 子节点列表，系统遍历节点后把所有子节点添加到该列表中。
	 * @param headers 列标头列表。
	 * @param parent 父节点对象。
	 * @param depth 节点深度。
	 */
	private static void markParents(JSONArray leafs, JSONArray headers,
			JSONObject parent, int depth) {
		int i, j = headers.length();
		JSONObject header;
		JSONArray items;

		leafs.put(0, Math.max(leafs.getInt(0), depth));
		for (i = 0; i < j; i++) {
			header = headers.getJSONObject(i);
			header.put("y", depth);
			header.put("colspan", -1);
			header.put("rowspan", -1);
			if (parent != null) {
				header.put("parent", parent);
				parent.put("child", header);
			}
			items = (JSONArray) header.opt("items");
			if (items != null)
				markParents(leafs, items, header, depth + 1);
			else
				leafs.put(header);
		}
	}

	/**
	 * 在Excel Sheet中添加正文记录内容。
	 * @param sheet Excel的Sheet对象。
	 * @param records 记录集数据。
	 * @param fields 字段元数据列表。
	 * @param startRow 开始行索引号。
	 */
	private static void createRecords(HttpServletRequest request, Sheet sheet,
			JSONArray records, JSONArray fields, int startRow,
			String defaultDateFormat, String defaultTimeFormat) {
		int i, j = records.length(), k, l = fields.length();
		Cell cell;
		Row row;
		JSONObject record, field;
		String format, keyName, dataTypeStr, fieldNames[] = new String[l];
		Workbook book = sheet.getWorkbook();
		Object value, cellStyles[] = createCellStyle(book, "text");
		CellStyle style, dateStyle, dateTimeStyle;
		CellStyle baseStyle = (CellStyle) cellStyles[0];
		CellStyle colStyles[] = new CellStyle[l], dateTimeStyles[][] = new CellStyle[l][2];
		short rowHeight = (Short) cellStyles[1];
		String boolString = Var.getString("sys.service.excel.boolText");
		String boolStrings[], trueText = null, falseText = null, dateTimeStr;
		boolean useBoolString, hasTime, isRate[] = new boolean[l];
		int dataTypes[] = new int[l], dataType;
		double number;
		Date date;
		Object keyMaps[] = new Object[l];

		useBoolString = !boolString.isEmpty();
		if (useBoolString) {
			boolStrings = boolString.split(",");
			trueText = boolStrings[0];
			falseText = boolStrings[1];
		}
		for (k = 0; k < l; k++) {
			field = fields.getJSONObject(k);
			fieldNames[k] = field.optString("field");
			style = book.createCellStyle();
			style.cloneStyleFrom(baseStyle);
			style.setAlignment(getAlignment(field.optString("align"),
					CellStyle.ALIGN_LEFT));
			if (Boolean.TRUE.equals(field.opt("autoWrap")))
				style.setWrapText(true);
			keyName = field.optString("keyName");
			if (keyName.isEmpty()) {
				keyMaps[k] = null;
				dataTypeStr = field.optString("type").toLowerCase();
			} else {
				keyMaps[k] = KVBuffer.buffer.get(keyName);
				dataTypeStr = "string";
			}
			format = field.optString("format");
			isRate[k] = format.endsWith("%");
			if (dataTypeStr.equals("string"))
				dataType = 1;
			else if (dataTypeStr.startsWith("int")
					|| dataTypeStr.equals("float")
					|| dataTypeStr.equals("number")) {
				dataType = 2;
				if (!StringUtil.isEmpty(format)) {
					style.setDataFormat(book.createDataFormat().getFormat(
							format));
				}
			} else if (dataTypeStr.equals("date")) {
				dataType = 3;
				if (StringUtil.isEmpty(format)) {
					// 未指定格式创建默认的日期，时间和日期时间格式
					dateStyle = book.createCellStyle();
					dateStyle.cloneStyleFrom(style);
					dateTimeStyle = book.createCellStyle();
					dateTimeStyle.cloneStyleFrom(style);
					format = toExcelDateFormat(defaultDateFormat, true);
					dateStyle.setDataFormat(book.createDataFormat().getFormat(
							format));
					dateTimeStyles[k][0] = dateStyle;
					format = toExcelDateFormat(defaultDateFormat + " "
							+ defaultTimeFormat, true);
					dateTimeStyle.setDataFormat(book.createDataFormat()
							.getFormat(format));
					dateTimeStyles[k][1] = dateTimeStyle;
					style = dateStyle;// 使空单无格默认为日期格式
				} else {
					dateTimeStyles[0][0] = null;
					format = toExcelDateFormat(format, false);
					if (format == null)
						format = toExcelDateFormat(defaultDateFormat, true);
					style.setDataFormat(book.createDataFormat().getFormat(
							format));
				}
			} else if (dataTypeStr.startsWith("bool")) // bool&boolean
				dataType = 4;
			else
				dataType = 5;// auto
			dataTypes[k] = dataType;
			colStyles[k] = style;
		}
		for (i = 0; i < j; i++) {
			row = sheet.createRow(startRow + i);
			row.setHeight(rowHeight);
			record = (JSONObject) records.opt(i);
			for (k = 0; k < l; k++) {
				cell = row.createCell(k);
				cell.setCellStyle(colStyles[k]);
				value = JsonUtil.opt(record, fieldNames[k]);
				if (value == null)
					continue;
				if (keyMaps[k] != null) {
					// 键值转换
					value = KVBuffer.getValue(
							(ConcurrentHashMap<?, ?>) keyMaps[k], request,
							value);
				}
				if (dataTypes[k] == 5) {
					// 对自动类型进行归类
					if (value instanceof Number)
						dataTypes[k] = 2;
					else if (value instanceof Date)
						dataTypes[k] = 3;
					else if (value instanceof Boolean)
						dataTypes[k] = 4;
					else
						dataTypes[k] = 1;
				}
				switch (dataTypes[k]) {
				case 2:// number
					if (value instanceof Number)
						number = ((Number) value).doubleValue();
					else
						number = Double.parseDouble(value.toString());
					if (isRate[k])
						number = number / 100;
					cell.setCellValue(number);
					break;
				case 3:// date
					if (dateTimeStyles[k][0] == null) {
						// 使用指定的格式
						if (value instanceof Date)
							date = (Date) value;
						else
							date = Timestamp.valueOf(value.toString());
					} else {
						// 根据值判断使用的默认日期时间格式，并覆盖样式
						if (value instanceof Date) {
							date = (Date) value;
							hasTime = !com.wb.util.DateUtil.dateToStr(date)
									.endsWith("00:00:00.0");
						} else {
							dateTimeStr = value.toString();
							date = Timestamp.valueOf(dateTimeStr);
							// 字符串需要判断以下3种情景：
							// 无小数，Java以".0"结尾，JS以".000"结尾
							hasTime = !(dateTimeStr.endsWith("00:00:00.0") || dateTimeStr
									.endsWith("00:00:00")
									| dateTimeStr.endsWith("00:00:00.000"));
						}
						if (hasTime)
							cell.setCellStyle(dateTimeStyles[k][1]);
						else
							cell.setCellStyle(dateTimeStyles[k][0]);
					}
					cell.setCellValue(date);
					break;
				case 4:// bool
					if (useBoolString)
						cell
								.setCellValue(StringUtil.getBool(value
										.toString()) ? trueText : falseText);
					else
						cell.setCellValue(StringUtil.getBool(value.toString()));
					break;
				default:// string
					cell.setCellValue(value.toString());
					break;
				}
			}
		}
	}

	/**
	 * 根据设置的变量，创建并获取Excel指定版本的workbook对象。
	 * @return 新创建的workbook对象。
	 */
	public static Workbook getBook() {
		if (Var.getBool("sys.service.excel.xlsx"))
			return new XSSFWorkbook();
		else
			return new HSSFWorkbook();
	}

	/**
	 * 根据设置的变量，获取Excel文件的扩展名.xls或.xlsx。
	 * @return 指定版本的Excel文件扩展名。
	 */
	public static String getExtName() {
		if (Var.getBool("sys.service.excel.xlsx"))
			return ".xlsx";
		else
			return ".xls";
	}

	/**
	 * 根据变量的设置获取单元格样式对象。
	 * @param book 工作簿。
	 * @param type 类型。titile标题，header列标题，text正文。
	 * @return 样式对象数组：0项样式，1项行高度。
	 */
	public static Object[] createCellStyle(Workbook book, String type) {
		CellStyle style = book.createCellStyle();
		Font font = book.createFont();
		String fontName = Var.getString("sys.service.excel." + type
				+ ".fontName");
		int fontHeight = Var
				.getInt("sys.service.excel." + type + ".fontHeight");
		double rowHeight = Var.getDouble("sys.service.excel." + type
				+ ".rowHeight");
		Object result[] = new Object[2];

		if (!fontName.isEmpty())
			font.setFontName(fontName);
		font.setBoldweight((short) Var.getInt("sys.service.excel." + type
				+ ".fontWeight"));
		font.setFontHeight((short) fontHeight);
		if (rowHeight < 10)
			rowHeight = rowHeight * fontHeight;// 小于10定义为倍数
		if (!"text".equals(type)) {
			if (Var.getBool("sys.service.excel." + type + ".wrapText"))
				style.setWrapText(true);
		}
		if ("title".equals(type)) {
			String align = Var
					.getString("sys.service.excel." + type + ".align");
			if (!align.isEmpty()) {
				Object[][] alignments = { { "居中", CellStyle.ALIGN_CENTER },
						{ "左", CellStyle.ALIGN_LEFT },
						{ "右", CellStyle.ALIGN_RIGHT },
						{ "居中选择", CellStyle.ALIGN_CENTER_SELECTION },
						{ "填充", CellStyle.ALIGN_FILL },
						{ "常规", CellStyle.ALIGN_GENERAL },
						{ "两端对齐", CellStyle.ALIGN_JUSTIFY } };
				style.setAlignment((Short) SysUtil.getValue(alignments, align));
			}
		} else if (Var.getBool("sys.service.excel.border")) {
			style.setBorderTop(CellStyle.BORDER_THIN);
			style.setBorderBottom(CellStyle.BORDER_THIN);
			style.setBorderLeft(CellStyle.BORDER_THIN);
			style.setBorderRight(CellStyle.BORDER_THIN);
		}
		if ("header".equals(type)) {
			String backColor = Var
					.getString("sys.service.excel.header.backColor");
			if (!"默认".equals(backColor)) {
				// HSSFColor兼容XSSF
				Object[][] colors = { { "默认", -1 },
						{ "金色", HSSFColor.GOLD.index },
						{ "灰色", HSSFColor.GREY_25_PERCENT.index },
						{ "浅黄", HSSFColor.LIGHT_YELLOW.index } };
				style.setFillForegroundColor((Short) SysUtil.getValue(colors,
						backColor));
				style.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
			}
		}
		style.setVerticalAlignment((short) 1);// HSSFCellStyle.VERTICAL_CENTER
		style.setFont(font);
		result[0] = style;
		result[1] = ((Double) rowHeight).shortValue();
		return result;
	}

	/**
	 * 根据对齐方法字符串返回Excel对齐值。
	 * @param align 对齐字符串。
	 * @return 对齐值。
	 */
	public static short getAlignment(String align, short defaultAlign) {
		if ("right".equals(align))
			return CellStyle.ALIGN_RIGHT;
		else if ("center".equals("align"))
			return CellStyle.ALIGN_CENTER;
		else if ("left".equals("align"))
			return CellStyle.ALIGN_LEFT;
		else
			return defaultAlign;
	}

	/**
	 * 把包含Excel数据的输入流内容转换为JSON格式的字符串。每行数据以JSONObject存储，
	 * 不同行之间以换行符分隔。首行为字段名称，字段名称允许加备注，"()"内的内容将被忽略。
	 * @param inputStream 输入流。
	 * @param xlsxFormat Excel格式，true为xlsx，false为xls。
	 * @return 转换为JSON格式的字符串。
	 * @throws Exception 转换过程发生异常。
	 */
	public static String excelToJson(InputStream inputStream, boolean xlsxFormat)
			throws Exception {
		Workbook book;
		Sheet sheet;
		Row row;
		Cell cell;
		Iterator<?> rows, cells;
		Object value;
		int pos, pos1, colIndex, rowIndex = 0;
		String valueStr;
		ArrayList<String> fieldList = new ArrayList<String>();
		StringBuilder text = new StringBuilder("");

		if (xlsxFormat)
			book = new XSSFWorkbook(inputStream);
		else
			book = new HSSFWorkbook(inputStream);
		try {
			sheet = book.getSheetAt(0);
			rows = sheet.rowIterator();
			while (rows.hasNext()) {
				if (rowIndex > 1)
					text.append("\n{");
				else if (rowIndex > 0)
					text.append('{');
				row = (Row) rows.next();
				cells = row.cellIterator();
				colIndex = 0;
				while (cells.hasNext()) {
					cell = (Cell) cells.next();
					value = getCellValue(cell);
					if (rowIndex == 0) {
						if (value == null)
							throw new NullPointerException(
									"Field name has null value.");
						valueStr = value.toString();
						pos = valueStr.indexOf('(');
						pos1 = valueStr.indexOf("（");
						if (pos1 != -1 && (pos == -1 || pos1 < pos))
							pos = pos1;
						if (pos == -1)
							fieldList.add(valueStr);
						else
							fieldList.add(valueStr.substring(0, pos));
					} else {
						if (colIndex > 0)
							text.append(',');
						if (colIndex >= fieldList.size())
							throw new RuntimeException("Row " + (rowIndex + 1)
									+ " column " + (colIndex + 1)
									+ " is out of bounds.");
						text.append(StringUtil.quote(fieldList.get(colIndex)));
						text.append(':');
						text.append(StringUtil.encode(value));
					}
					colIndex++;
				}
				if (rowIndex > 0)
					text.append('}');
				rowIndex++;
			}
		} finally {
			book.close();
		}
		return text.toString();
	}

	/**
	 * 获取单元格的值。
	 * @param cell 单元格。
	 * @return 单元格值。如果为空返回null。
	 */
	public static Object getCellValue(Cell cell) {
		switch (cell.getCellType()) {
		case Cell.CELL_TYPE_FORMULA:
		case Cell.CELL_TYPE_NUMERIC:
			if (DateUtil.isCellDateFormatted(cell))
				return cell.getDateCellValue();
			else
				return cell.getNumericCellValue();
		case Cell.CELL_TYPE_STRING:
			return cell.getStringCellValue();
		case Cell.CELL_TYPE_BOOLEAN:
			return cell.getBooleanCellValue();
		default:
			return null;
		}
	}

	/**
	 * 向指定输出流中输出HTML格式的数据。
	 * @param request 请求对象。
	 * @param outputStream 输出流。输出数据后该流不关闭，如果有必要需要手动关闭。
	 * @param headers 标题列。标题列允许嵌套，并根据设置的嵌套自动合并单元格。
	 * 标题列所有单元格都为字符串格式。
	 * @param records 输出的记录数据。
	 * @param title 标题。标题将显示在首行并合并所在行所有单元格且居中显示。如果为null，
	 * 将不生成标题。
	 * @param dateFormat 当未指定格式时使用的默认日期格式。
	 * @param timeFormat 当未指定格式时使用的默认时间格式。
	 * @param neptune 客户端是否为海王星主题，海王星主题列较宽映射到表格时需要按比缩小。
	 * @param rowNumberWidth 行号列宽度，-1表示无行号列。
	 * @param rowNumberTitle 行号列标题。
	 * @param decimalSeparator 小数点符号。
	 * @param thousandSeparator 千分位符号。
	 */
	public static void outputHtml(HttpServletRequest request,
			OutputStream outputStream, JSONArray headers, JSONArray records,
			String title, String dateFormat, String timeFormat,
			boolean neptune, int rowNumberWidth, String rowNumberTitle,
			String decimalSeparator, String thousandSeparator) throws Exception {
		StringBuilder html = new StringBuilder();
		String value;
		JSONArray fields;

		html
				.append("<!DOCTYPE html><html><head><meta http-equiv=\"content-type\" content=\"text/html;charset=utf-8\"><title>");
		if (StringUtil.isEmpty(title))
			html.append(Str.format(request, "preview"));
		else
			html.append(title);
		html
				.append("</title><style type=\"text/css\">table{table-layout:fixed;border-collapse:collapse;word-wrap:break-word;");
		value = Var.getString("sys.service.preview.textFont");
		if (!value.isEmpty()) {
			html.append("font-family:");
			html.append(value);
			html.append(';');
		}
		html.append("line-height:");
		html.append(Var.getString("sys.service.preview.textLineHeight"));
		html.append(";font-size:");
		html.append(Var.getString("sys.service.preview.textFontSize"));
		html.append(";}.header{");
		value = Var.getString("sys.service.preview.headerBackColor");
		if (!value.isEmpty()) {
			html.append("background-color:");
			html.append(value);
			html.append(';');
		}
		html.append("font-weight:");
		html.append(Var.getString("sys.service.preview.headerFontWeight"));
		html
				.append(";}td{border:1px solid #000000;padding:0 2px 0 2px;}th{border:0;}.wrap{word-wrap:break-word;}</style></head><body>");
		// <table>在createHtmlHeaders方法内定义因为需要指定宽度
		fields = createHtmlHeaders(html, headers, title, neptune,
				rowNumberWidth, rowNumberTitle);
		getTextContent(request, html, records, fields, dateFormat, timeFormat,
				rowNumberWidth > -1, true, decimalSeparator, thousandSeparator,
				null);
		html.append("</table></body></html>");
		outputStream.write(html.toString().getBytes("utf-8"));
	}

	/**
	 * 向指定输出流中输出文本格式的数据。
	 * @param request 请求对象。
	 * @param outputStream 输出流。输出数据后该流不关闭，如果有必要需要手动关闭。
	 * @param headers 标题列。标题列允许嵌套，并根据设置的嵌套自动合并单元格。
	 * 标题列所有单元格都为字符串格式。
	 * @param records 输出的记录数据。
	 * @param defaultDateFormat 当未指定格式时使用的默认日期格式。
	 * @param defaultTimeFormat 当未指定格式时使用的默认时间格式。
	 * @param decimalSeparator 小数点符号。
	 * @param thousandSeparator 千分位符号。
	 */
	public static void outputText(HttpServletRequest request,
			OutputStream outputStream, JSONArray headers, JSONArray records,
			String defaultDateFormat, String defaultTimeFormat,
			String decimalSeparator, String thousandSeparator) throws Exception {
		StringBuilder text = new StringBuilder();
		JSONArray leafs = new JSONArray();
		String lineSeparator = SysUtil.getLineSeparator();
		int i, j;

		leafs.put(0);// 0项为headers最大深度
		markParents(leafs, headers, null, 0);
		// 0项为深度去除
		leafs.remove(0);
		j = leafs.length();
		for (i = 0; i < j; i++) {
			if (i > 0)
				text.append('\t');
			text.append(leafs.getJSONObject(i).optString("text"));
		}
		text.append(lineSeparator);
		getTextContent(request, text, records, leafs, defaultDateFormat,
				defaultTimeFormat, false, false, decimalSeparator,
				thousandSeparator, lineSeparator);
		outputStream.write(text.toString().getBytes("utf-8"));
	}

	/**
	 * 创建HTML表格列标头。
	 * @param html HTML脚本。创建的列标头脚本将输出至该对象。
	 * @param rawHeaders 原始列标题列表。
	 * @param title 标题。
	 * @param neptune 客户端是否为海王星主题。
	 * @param rowNumberWidth 行号列宽度。
	 * @param rowNumberTitle 行号列标题。
	 * @return 自段定义列表。
	 */
	private static JSONArray createHtmlHeaders(StringBuilder html,
			JSONArray rawHeaders, String title, boolean neptune,
			int rowNumberWidth, String rowNumberTitle) {
		JSONArray row, leafs = new JSONArray(), grid = new JSONArray();
		JSONObject node;
		int i, j, maxDepth, k, l, y, colspan, rowspan, tableWidth;
		int flexWidth = Var.getInt("sys.service.excel.flexColumnMaxWidth");// 共享变量
		double rate;
		String value, align;

		if (neptune)
			rate = 0.87719298;
		else
			rate = 1;
		if (rowNumberWidth > -1) {
			// 存在行号列
			rowNumberWidth = (int) Math.round(rowNumberWidth * rate);
			tableWidth = rowNumberWidth;
		} else
			tableWidth = 0;
		leafs.put(0);// 0项为headers最大深度
		markParents(leafs, rawHeaders, null, 0);
		maxDepth = leafs.getInt(0);
		leafs.remove(0);// 移除深度值
		j = leafs.length();
		for (i = 0; i < j; i++) {
			node = leafs.getJSONObject(i);
			tableWidth += getHtmlCellWidth(node, flexWidth, rate);
			y = node.getInt("y");
			node.put("rowspan", maxDepth - y);
			do {
				node.put("colspan", node.getInt("colspan") + 1);
				if (!node.has("x")) {
					node.put("x", i);
					y = node.getInt("y");
					row = grid.optJSONArray(y);
					if (row == null) {
						row = new JSONArray();
						grid.put(y, row);
					}
					row.put(i, node);
				}
			} while ((node = (JSONObject) node.opt("parent")) != null);
		}
		if (title != null) {
			html.append("<p style=\"text-align:center;width:");
			html.append(tableWidth);
			html.append("px;");
			value = Var.getString("sys.service.preview.titleFont");
			if (!value.isEmpty()) {
				html.append("font-family:");
				html.append(value);
				html.append(';');
			}
			html.append("font-weight:");
			html.append(Var.getString("sys.service.preview.titleFontWeight"));
			html.append(";line-height:");
			html.append(Var.getString("sys.service.preview.titleLineHeight"));
			html.append(";font-size:");
			html.append(Var.getString("sys.service.preview.titleFontSize"));
			html.append(";\">");
			html.append(StringUtil.toHTML(title, true, true));
			html.append("</p>");
		}
		html.append("<table style=\"width:");
		html.append(tableWidth);
		html.append("px;\">");
		// 添加首行高度为0的行用以指定各列的宽度
		html.append("<tr style=\"height:0\">");
		if (rowNumberWidth > -1) {
			// 行号列
			html.append("<th width=\"");
			html.append(rowNumberWidth);
			html.append("px\"></th>");
		}
		j = leafs.length();
		for (i = 0; i < j; i++) {
			node = leafs.getJSONObject(i);
			html.append("<th width=\"");
			html.append(getHtmlCellWidth(node, flexWidth, rate));
			html.append("px\"></th>");
		}
		html.append("</tr>");
		j = grid.length();
		for (i = 0; i < j; i++) {
			html.append("<tr class=\"header\">");
			if (rowNumberWidth > -1 && i == 0) {
				// 行号列
				html.append("<td rowspan=\"");
				html.append(maxDepth + 1);
				html.append("\">");
				html.append(rowNumberTitle);// 使用html格式
				html.append("</td>");
			}
			row = grid.getJSONArray(i);
			l = row.length();
			for (k = 0; k < l; k++) {
				node = row.optJSONObject(k);
				if (node == null)
					continue;
				html.append("<td align=\"");
				colspan = node.getInt("colspan");
				align = node.optString("titleAlign");
				if (StringUtil.isEmpty(align) && colspan > 0)
					align = "center";
				html.append(align);
				html.append('"');
				if (colspan > 0) {
					html.append(" colspan=\"");
					html.append(colspan + 1);
					html.append("\"");
				}
				rowspan = node.getInt("rowspan");
				if (rowspan > 0) {
					html.append(" rowspan=\"");
					html.append(rowspan + 1);
					html.append("\"");
				}
				html.append('>');
				html.append(node.optString("text"));// 使用html格式
				html.append("</td>");
			}
			html.append("</tr>");
		}
		return leafs;
	}

	/**
	 * 根据指定参数获取HTML单元格宽度。
	 * @param node 单元格JSONObject对象。
	 * @param flexWidth 列指定为flex时的宽度。
	 * @param rate neptune主题下的宽度比例。
	 * @return 计算得到的宽度。
	 */
	private static int getHtmlCellWidth(JSONObject node, int flexWidth,
			double rate) {
		int width;

		if (node.has("width"))
			width = node.getInt("width");
		else if (node.has("flex"))
			width = flexWidth;
		else
			width = 100;
		return (int) Math.round(width * rate);
	}

	/**
	 * 获取正文内容至指定格式。
	 * @param request 请求对象。
	 * @param buf 输出缓冲区。
	 * @param records 记录列表。
	 * @param fields 字段列表。
	 * @param defaultDateFormat 默认日期格式。
	 * @param defaultTimeFormat 默认时间格式。
	 * @param hasRowNumber 是否存在行号列。
	 * @param isHtml 输出是否为html格式，true html， false文本。
	 * @param decimalSeparator 小数点符号。
	 * @param thousandSeparator 千分位符号。
	 */
	private static void getTextContent(HttpServletRequest request,
			StringBuilder buf, JSONArray records, JSONArray fields,
			String defaultDateFormat, String defaultTimeFormat,
			boolean hasRowNumber, boolean isHtml, String decimalSeparator,
			String thousandSeparator, String lineSeparator) {
		int i, j = records.length(), k, l = fields.length();
		JSONObject record, field;
		String format, keyName, dataTypeStr, valueText, fieldNames[] = new String[l];
		String boolString = Var.getString("sys.service.excel.boolText");
		String boolStrings[], trueText = null, falseText = null, dateTimeStr;
		String aligns[] = new String[l];
		boolean useBoolString, hasTime;
		boolean wraps[] = new boolean[l], isRate[] = new boolean[l];
		int dataTypes[] = new int[l], dataType;
		double number;
		Date date;
		Format formats[] = new Format[l];
		Format dateFormat = new SimpleDateFormat(toJavaDateFormat(
				defaultDateFormat, true));
		Format dateTimeFormat = new SimpleDateFormat(toJavaDateFormat(
				defaultDateFormat + " " + defaultTimeFormat, true));
		DecimalFormat decimalFormat;
		DecimalFormatSymbols dfs;
		Object value, keyMaps[] = new Object[l];

		useBoolString = !boolString.isEmpty();
		if (useBoolString) {
			boolStrings = boolString.split(",");
			trueText = boolStrings[0];
			falseText = boolStrings[1];
		}
		for (k = 0; k < l; k++) {
			field = fields.getJSONObject(k);
			keyName = field.optString("keyName");
			if (keyName.isEmpty()) {
				keyMaps[k] = null;
				dataTypeStr = field.optString("type").toLowerCase();
			} else {
				keyMaps[k] = KVBuffer.buffer.get(keyName);
				dataTypeStr = "string";
			}
			format = field.optString("format");
			isRate[k] = format.endsWith("%");
			formats[k] = null;
			if (dataTypeStr.equals("string"))
				dataType = 1;
			else if (dataTypeStr.startsWith("int")
					|| dataTypeStr.equals("float")
					|| dataTypeStr.equals("number")) {
				dataType = 2;
				if (!format.isEmpty()) {
					decimalFormat = new DecimalFormat(format);
					decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
					dfs = new DecimalFormatSymbols();
					dfs.setDecimalSeparator(decimalSeparator.charAt(0));
					dfs.setGroupingSeparator(thousandSeparator.charAt(0));
					decimalFormat.setDecimalFormatSymbols(dfs);
					formats[k] = decimalFormat;
				}
			} else if (dataTypeStr.equals("date")) {
				dataType = 3;
				if (!StringUtil.isEmpty(format)) {
					format = toJavaDateFormat(format, false);
					if (format == null)
						formats[k] = dateFormat;
					else
						formats[k] = new SimpleDateFormat(format);
				}

			} else if (dataTypeStr.startsWith("bool")) // bool&boolean
				dataType = 4;
			else
				dataType = 5;// auto
			dataTypes[k] = dataType;
			fieldNames[k] = field.optString("field");
			aligns[k] = field.optString("align");
			wraps[k] = Boolean.TRUE.equals(field.opt("autoWrap"));
		}
		for (i = 0; i < j; i++) {
			record = (JSONObject) records.opt(i);
			if (isHtml) {
				buf.append("<tr>");
				if (hasRowNumber) {
					buf.append("<td align=\"right\">");
					buf.append(i + 1);
					buf.append("</td>");
				}
			} else if (i > 0)
				buf.append(lineSeparator);
			for (k = 0; k < l; k++) {
				value = JsonUtil.opt(record, fieldNames[k]);
				if (value == null) {
					if (isHtml) {
						buf.append("<td></td>");
					} else
						buf.append("\t");
					continue;
				}
				if (isHtml) {
					buf.append("<td");
					if (!aligns[k].isEmpty()) {
						buf.append(" align=\"");
						buf.append(aligns[k]);
						buf.append("\"");
					}
					if (wraps[k])
						buf.append(" class=\"wrap\"");
					buf.append('>');
				} else if (k > 0)
					buf.append("\t");
				if (keyMaps[k] != null) {
					// 键值转换
					value = KVBuffer.getValue(
							(ConcurrentHashMap<?, ?>) keyMaps[k], request,
							value);
				}
				if (dataTypes[k] == 5) {
					// 对自动类型进行归类
					if (value instanceof Number)
						dataTypes[k] = 2;
					else if (value instanceof Date)
						dataTypes[k] = 3;
					else if (value instanceof Boolean)
						dataTypes[k] = 4;
					else
						dataTypes[k] = 1;
				}
				switch (dataTypes[k]) {
				case 2:// number
					if (value instanceof Number) {
						if (formats[k] == null)
							valueText = StringUtil.replaceFirst(value
									.toString(), ".", decimalSeparator);
						else {
							number = ((Number) value).doubleValue();
							if (isRate[k])
								number = number / 100;
							valueText = formats[k].format(number);
						}
					} else {
						if (formats[k] == null)
							valueText = StringUtil.replaceFirst(value
									.toString(), ".", decimalSeparator);
						else {
							number = Double.parseDouble(value.toString());
							if (isRate[k])
								number = number / 100;
							valueText = formats[k].format(number);
						}
					}
					break;
				case 3:// date
					if (formats[k] == null) {
						// 根据值判断使用的默认日期时间格式，并覆盖样式
						if (value instanceof Date) {
							date = (Date) value;
							hasTime = !com.wb.util.DateUtil.dateToStr(date)
									.endsWith("00:00:00.0");
						} else {
							dateTimeStr = value.toString();
							date = Timestamp.valueOf(dateTimeStr);
							// 字符串需要判断以下3种情景：
							// 无小数，Java以".0"结尾，JS以".000"结尾
							hasTime = !(dateTimeStr.endsWith("00:00:00.0") || dateTimeStr
									.endsWith("00:00:00")
									| dateTimeStr.endsWith("00:00:00.000"));
						}
						if (hasTime)
							valueText = dateTimeFormat.format(date);
						else
							valueText = dateFormat.format(date);
					} else {
						// 使用指定的格式
						if (value instanceof Date)
							date = (Date) value;
						else
							date = Timestamp.valueOf(value.toString());
						valueText = formats[k].format(date);
					}
					break;
				case 4:// bool
					if (useBoolString)
						valueText = StringUtil.getBool(value.toString()) ? trueText
								: falseText;
					else
						valueText = value.toString();
					break;
				default:// string
					valueText = value.toString();
					break;
				}
				if (isHtml) {
					buf.append(StringUtil.toHTML(valueText, true, true));
					buf.append("</td>");
				} else
					buf.append(valueText);
			}
			if (isHtml)
				buf.append("</tr>");
		}
	}

	/**
	 * 获取前端日期时间格式对应到Java的日期时间格式。
	 * @param format 日期时间格式。
	 * @param returnDefault 如果格式不支持是否返回默认格式，true默认格式，false返回null。
	 * @return 转换后的Java日期时间格式。
	 */
	public static String toJavaDateFormat(String format, boolean returnDefault) {
		String[] unSupportFormats = { "N", "S", "D", "w", "z", "W", "t", "L",
				"o", "O", "P", "T", "Z", "c", "U", "F", "MS", "l", "M", "time",
				"timestamp" };
		String[][] supportFormats = { { "y", "yy" }, { "Y", "yyyy" },
				{ "m", "MM" }, { "n", "M" }, { "d", "dd" }, { "j", "d" },
				{ "H", "HH" }, { "h", "hh" }, { "G", "H" }, { "g", "h" },
				{ "i", "mm" }, { "s", "ss" }, { "u", "SSS" }, { "a", "'_x'" },
				{ "A", "'_X'" } };

		for (String s : unSupportFormats) {
			if (format.indexOf(s) != -1)
				return returnDefault ? "yyyy-MM-dd" : null;
		}
		for (String[] s : supportFormats) {
			format = StringUtil.replaceAll(format, s[0], s[1]);
		}
		return format;

	}

	/**
	 * 获取前端日期时间格式对应到Excel的日期时间格式。
	 * @param format 日期时间格式。
	 * @param returnDefault 如果格式不支持是否返回默认格式，true默认格式，false返回null。
	 * @return 转换后的Excel日期时间格式。
	 */
	public static String toExcelDateFormat(String format, boolean returnDefault) {
		String[] unSupportFormats = { "N", "S", "w", "z", "W", "t", "L", "o",
				"u", "O", "P", "T", "Z", "c", "U", "MS", "time", "timestamp" };
		String[][] supportFormats = { { "d", "dd" }, { "D", "aaa" },
				{ "j", "d" }, { "l", "aaaa" }, { "F", "mmmm" }, { "m", "mm" },
				{ "M", "mmm" }, { "n", "m" }, { "Y", "yyyy" }, { "y", "yy" },
				{ "a", "am/pm" }, { "A", "AM/PM" }, { "g", "h" },
				{ "G", "hh" }, { "h", "h" }, { "H", "hh" }, { "i", "mm" },
				{ "s", "ss" } };

		for (String s : unSupportFormats) {
			if (format.indexOf(s) != -1)
				return returnDefault ? "yyyy-mm-dd" : null;
		}
		for (String[] s : supportFormats) {
			format = StringUtil.replaceAll(format, s[0], s[1]);
		}
		return format;
	}
}