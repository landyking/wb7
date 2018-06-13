package com.wb.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;

import javax.naming.InitialContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Base;
import com.wb.common.Dictionary;
import com.wb.common.KVBuffer;
import com.wb.common.Var;
import com.wb.tool.DictRecord;
import com.wb.tool.Query;
import com.wb.tool.Updater;

/**
 * 数据库工具方法类。
 */
public class DbUtil {
	/** 预定义的SQL类型和名称对照表 */
	public static final Object[][] sqlTypes = { { "BIT", Types.BIT },
			{ "TINYINT", Types.TINYINT }, { "SMALLINT", Types.SMALLINT },
			{ "INTEGER", Types.INTEGER }, { "BIGINT", Types.BIGINT },
			{ "FLOAT", Types.FLOAT }, { "REAL", Types.REAL },
			{ "DOUBLE", Types.DOUBLE }, { "NUMERIC", Types.NUMERIC },
			{ "DECIMAL", Types.DECIMAL }, { "CHAR", Types.CHAR },
			{ "VARCHAR", Types.VARCHAR }, { "LONGVARCHAR", Types.LONGVARCHAR },
			{ "DATE", Types.DATE }, { "TIME", Types.TIME },
			{ "TIMESTAMP", Types.TIMESTAMP }, { "BINARY", Types.BINARY },
			{ "VARBINARY", Types.VARBINARY },
			{ "LONGVARBINARY", Types.LONGVARBINARY }, { "NULL", Types.NULL },
			{ "OTHER", Types.OTHER }, { "JAVA_OBJECT", Types.JAVA_OBJECT },
			{ "DISTINCT", Types.DISTINCT }, { "STRUCT", Types.STRUCT },
			{ "ARRAY", Types.ARRAY }, { "BLOB", Types.BLOB },
			{ "CLOB", Types.CLOB }, { "REF", Types.REF },
			{ "DATALINK", Types.DATALINK }, { "BOOLEAN", Types.BOOLEAN },
			{ "ROWID", Types.ROWID }, { "NCHAR", Types.NCHAR },
			{ "NVARCHAR", Types.NVARCHAR },
			{ "LONGNVARCHAR", Types.LONGNVARCHAR }, { "NCLOB", Types.NCLOB },
			{ "SQLXML", Types.SQLXML } };

	/**
	 * 根据字段类型名称获得字段类型编号。如果名称为空返回VARCHAR，如果没有找到返回null。
	 * @param name 字段类型名称。
	 * @return 字段类型编号。
	 */
	public static Integer getFieldType(String name) {
		if (StringUtil.isEmpty(name))
			return Types.VARCHAR;
		int i, j = sqlTypes.length;

		for (i = 0; i < j; i++)
			if (name.equalsIgnoreCase((String) sqlTypes[i][0]))
				return (Integer) (sqlTypes[i][1]);
		if (StringUtil.isNumeric(name, false))
			return Integer.parseInt(name);
		return null;
	}

	/**
	 * 根据字段类型编号获得字段类型名称。如果没有找到名称直接返回type。
	 * @param type 字段类型编号。
	 * @return 字段类型名称。
	 */
	public static String getTypeName(int type) {
		int i, j = sqlTypes.length;

		switch (type) {
		case Types.CHAR:
		case Types.NCHAR:
		case Types.VARCHAR:
		case Types.NVARCHAR:
			return null;
		}
		for (i = 0; i < j; i++)
			if (type == (Integer) sqlTypes[i][1])
				return ((String) (sqlTypes[i][0])).toLowerCase();
		return Integer.toString(type);
	}

	/**
	 * 导入JSON数据至指定数据库表。导入完成后自动关闭reader。
	 * 
	 * @param connection 数据库连接对象。
	 * @param tableName 表名。
	 * @param reader 读数据的reader对象。
	 * @throws Exception 导入过程发生异常。
	 */
	public static void importData(Connection connection, String tableName,
			BufferedReader reader) throws Exception {
		importData(connection, tableName, reader, null, ' ');
	}

	/**
	 * 导入数据至指定数据库表。导入的数据可以为txt,excel或json格式。导入完成后自动关闭reader。
	 * 
	 * @param connection 数据库连接对象。
	 * @param tableName 表名。
	 * @param reader 读数据的reader对象。
	 * @param fieldList 字段名称列表，如果此参数为null，表示数据为json格式。
	 * @param fieldSeparator 字段值之间的分隔符，此参数只有当数据为非json格式时才有意义。
	 * @throws Exception 导入过程发生异常。
	 */
	public static void importData(Connection connection, String tableName,
			BufferedReader reader, String[] fieldList, char fieldSeparator)
			throws Exception {
		ResultSet rs = null;
		PreparedStatement st = null;
		JSONObject record;
		String line, fieldNames[], quoteNames[], value, values[];
		int i, j, k, types[], indexList[];
		boolean jsonFormat = fieldList == null;

		try {
			st = connection.prepareStatement(StringUtil.concat(
					"select * from ", tableName, " where 1=0"));
			rs = st.executeQuery();
			ResultSetMetaData meta = rs.getMetaData();
			j = meta.getColumnCount();
			if (jsonFormat)
				indexList = null;
			else
				indexList = new int[j];
			types = new int[j];
			fieldNames = new String[j];
			quoteNames = new String[j];
			for (i = 0; i < j; i++) {
				k = i + 1;
				types[i] = meta.getColumnType(k);
				fieldNames[i] = meta.getColumnLabel(k);
				fieldNames[i] = DbUtil.getFieldName(fieldNames[i]);
				quoteNames[i] = StringUtil.quoteIf(fieldNames[i]);
				if (!jsonFormat)
					indexList[i] = StringUtil.indexOf(fieldList, fieldNames[i]);
			}
			close(rs);
			close(st);
			st = connection.prepareStatement(StringUtil.concat("insert into ",
					tableName, "(", StringUtil.join(quoteNames, ','),
					") values (?", StringUtil.repeat(",?", j - 1), ")"));
			while ((line = reader.readLine()) != null) {
				if (jsonFormat) {
					record = new JSONObject(line);
					for (i = 0; i < j; i++)
						setObject(st, i + 1, types[i], JsonUtil.opt(record,
								fieldNames[i])); // 可能存在Json.null值因此使用JsonUtil.opt
				} else {
					values = StringUtil.split(line, fieldSeparator);
					for (i = 0; i < j; i++) {
						value = indexList[i] == -1 ? null
								: values[indexList[i]];
						setObject(st, i + 1, types[i], value);
					}
				}
				DbUtil.addBatch(st);
			}
			DbUtil.executeBatch(st);
		} finally {
			close(rs);
			close(st);
			reader.close();
		}
	}

	/**
	 * 导出指定结果集数据。
	 * 
	 * @param rs 导出的结果集。
	 * @param writer 写数据的writer对象。
	 * @throws Exception 导出过程发生异常。
	 */
	public static void exportData(ResultSet rs, Writer writer) throws Exception {
		ResultSetMetaData meta = rs.getMetaData();
		InputStream stream;
		boolean newLine = false;
		int i, j = meta.getColumnCount(), k, types[] = new int[j];
		String names[] = new String[j];

		for (i = 0; i < j; i++) {
			types[i] = meta.getColumnType(i + 1);
			names[i] = meta.getColumnLabel(i + 1);
			names[i] = DbUtil.getFieldName(names[i]);
		}
		while (rs.next()) {
			if (newLine)
				writer.write('\n');
			else
				newLine = true;
			writer.write('{');
			for (i = 0; i < j; i++) {
				k = i + 1;
				if (i > 0)
					writer.write(',');
				writer.write(StringUtil.quote(names[i]));
				writer.write(':');
				if (isBlobField(types[i])) {
					stream = rs.getBinaryStream(k);
					try {
						writer.write(StringUtil.encode(stream));
					} finally {
						IOUtils.closeQuietly(stream);
					}
				} else {
					writer.write(StringUtil.encode(DbUtil.getObject(rs, k,
							types[i])));
				}
			}
			writer.write('}');
		}
		writer.flush();
	}

	/**
	 * 如果系统允许批操作该方法等同statement.addBatch,否则等同statement.executeUpdate。
	 * @param statement 执行更新的PreparedStatement对象。
	 * @throws SQLException 执行过程发生异常。
	 */
	public static void addBatch(PreparedStatement statement)
			throws SQLException {
		if (Var.batchUpdate)
			statement.addBatch();
		else
			statement.executeUpdate();
	}

	/**
	 * 如果系统允许批操作该方法等同executeBatch,否则无任何效果。
	 * @param statement 执行更新的PreparedStatement对象。
	 * @return 每次执行影响记录数量的数组。如果不允许批操作则返回null。
	 * @throws SQLException 执行过程发生异常。
	 */
	public static int[] executeBatch(PreparedStatement statement)
			throws SQLException {
		if (Var.batchUpdate)
			return statement.executeBatch();
		else
			return null;
	}

	/**
	 * 在数据库连接池中获取新的默认数据库连接，默认连接jndi由变量sys.jndi指定。
	 * 
	 * @return 新的默认数据库连接。
	 * @throws Exception
	 *             获取连接过程发生异常。
	 */
	public static Connection getConnection() throws Exception {
		return getConnection("");
	}

	/**
	 * 在当前HttpServletRequest请求对象中获取共享的默认数据库连接，默认连接jndi由变量
	 * sys.jndi指定。在请求周期内，该连接将被使用相同JNDI的所有数据库组件共享。
	 * 完成请求后，连接将被自动关闭。 如果连接存在未提交的事务且在请求期间未发生异常，
	 * 则系统自动提交事务，否则回滚事务。
	 * 
	 * @param request 请求对象。
	 * @return 共享的默认数据库连接。
	 * @throws Exception
	 *             获取连接过程发生异常。
	 */
	public static Connection getConnection(HttpServletRequest request)
			throws Exception {
		return getConnection(request, null);
	}

	/**
	 * 在数据库连接池中获取新的指定JNDI的数据库连接。如果参数JNDI为空则使用默认JNDI。
	 * 
	 * @param jndi 在WebBuilder变量sys.jndi注册过的jndi名称。
	 * @return 数据库连接。
	 * @throws Exception 获取连接过程发生异常。
	 */
	public static Connection getConnection(String jndi) throws Exception {
		// 仅允许访问注册过的jndi，防止jndi RMI注入。
		if (StringUtil.isEmpty(jndi))
			jndi = Var.jndi;
		else
			jndi = Var.getString("sys.jndi." + jndi);
		InitialContext context = new InitialContext();
		DataSource ds = (DataSource) context.lookup(jndi);
		return ds.getConnection();
	}

	/**
	 * 在当前HttpServletRequest请求对象中获取指定JNDI的数据库连接。
	 * 在请求周期内，该连接将被使用相同JNDI的所有数据库组件共享。
	 * 完成请求后，连接将被自动关闭。 如果连接存在未提交的事务且在请求期间未发生异常，
	 * 则系统自动提交事务，否则回滚事务。如果参数JNDI为空则使用默认JNDI。
	 * @param request 请求对象。
	 * @return 共享的指定JNDI数据库连接。
	 * @throws Exception 获取连接过程发生异常。
	 */
	public static Connection getConnection(HttpServletRequest request,
			String jndi) throws Exception {
		Connection conn;
		String storeName;
		Object obj;

		if (StringUtil.isEmpty(jndi))
			storeName = "conn@@";
		else
			storeName = "conn@@" + jndi;
		obj = WebUtil.getObject(request, storeName);
		if (obj == null) {
			conn = getConnection(jndi);
			WebUtil.setObject(request, storeName, conn);
		} else
			conn = (Connection) obj;
		return conn;
	}

	/**
	 * 重新开始新的数据库事务，设置连接为非自动提交模式，并设置事务的隔离级别。如果连接存在
	 *未提交的事务，首先提交事务，然后开始新的事务。
	 * @param connection 连接对象。
	 * @param isolation 事务孤立程度。
	 * @throws Exception 设置事务发生异常。
	 */
	public static void startTransaction(Connection connection, String isolation)
			throws Exception {
		if (!connection.getAutoCommit())
			connection.commit();
		connection.setAutoCommit(false);
		if (!StringUtil.isEmpty(isolation)) {
			if (isolation.equals("readUncommitted"))
				connection
						.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
			else if (isolation.equals("readCommitted"))
				connection
						.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			else if (isolation.equals("repeatableRead"))
				connection
						.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			else if (isolation.equals("serializable"))
				connection
						.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
		}
	}

	/**
	 * 关闭指定的ResultSet。如果关闭ResultSet过程中发生错误，系统将不会抛出任何异常。
	 * @param resultSet 需要关闭的ResultSet。
	 */
	private static void closeResult(ResultSet resultSet) {
		try {
			resultSet.close();
			resultSet = null;
		} catch (Throwable e) {
		}
	}

	/**
	 * 关闭指定的Statement。如果关闭Statement过程中发生错误，系统将不会抛出任何异常。
	 * @param statement 需要关闭的Statement。
	 */
	private static void closeStatement(Statement statement) {
		try {
			statement.close();
			statement = null;
		} catch (Throwable e) {
		}
	}

	/**
	 * 关闭指定的Connection。如果关闭连接过程中发生错误，系统将不会抛出任何异常。如果指定
	 *关闭连接前提交事务，首先尝试提交操作，如果失败则尝试回滚操作。
	 * @param connection 需要关闭的连接。
	 * @param rollback 如果连接存在未提交的事务，是否执行回滚。true回滚，false提交。
	 */
	private static void closeConnection(Connection connection, boolean rollback) {
		try {
			if (connection.isClosed())
				return;
			try {
				if (!connection.getAutoCommit())
					if (rollback)
						connection.rollback();
					else
						connection.commit();
			} catch (Throwable e) {
				if (!rollback)
					connection.rollback();
			} finally {
				connection.close();
				connection = null;
			}
		} catch (Throwable e) {
		}
	}

	/**
	 * 关闭指定的数据库资源，这些资源包括ResultSet，Statement，Connection。
	 * 如果object为空该方法不产生任何效果。如果关闭过程发生异常，这些异常将不会被抛出。
	 * @param object 需要关闭的资源
	 */
	public static void close(Object object) {
		if (object instanceof ResultSet)
			closeResult((ResultSet) object);
		else if (object instanceof Statement)
			closeStatement((Statement) object);
		else if (object instanceof Connection)
			closeConnection((Connection) object, true);
	}

	/**
	 * 关闭指定的数据库连接。如果关闭连接过程中发生错误，系统将不会抛出任何异常。如果连接
	 * 存在未提交的事务，首先尝试提交事务如果失败再尝试回滚。
	 * @param connection 需要关闭的连接。
	 */
	public static void closeCommit(Connection connection) {
		if (connection != null)
			closeConnection(connection, false);
	}

	/**
	 * 判断指定字段类型是否是二进制类型。
	 * @param type 字段类型。
	 * @return true二进制类型，false不是二进制类型。
	 */
	public static boolean isBlobField(int type) {
		switch (type) {
		case Types.BINARY:
		case Types.VARBINARY:
		case Types.LONGVARBINARY:
		case Types.BLOB:
			return true;
		}
		return false;
	}

	/**
	 * 判断指定字段类型是否是大文本类型。
	 * @param type 字段类型。
	 * @return true大文本类型，false不是大文本类型。
	 */
	public static boolean isTextField(int type) {
		switch (type) {
		case Types.LONGVARCHAR:
		case Types.LONGNVARCHAR:
		case Types.CLOB:
		case Types.NCLOB:
			return true;
		}
		return false;
	}

	/**
	 * 判断指定字段类型是否是字符串类型。
	 * @param type 字段类型。
	 * @return true字符串类型，false不是字符串类型。
	 */
	public static boolean isStringField(int type) {
		switch (type) {
		case Types.CHAR:
		case Types.NCHAR:
		case Types.VARCHAR:
		case Types.NVARCHAR:
			return true;
		}
		return false;
	}

	/**
	 * 判断指定字段类型是否可能是浮点数类型。
	 * @param type 字段类型。
	 * @return true可能是浮点数类型，false不可能是浮点数类型。
	 */
	public static boolean maybeFloatField(int type) {
		switch (type) {
		case Types.FLOAT:
		case Types.REAL:
		case Types.DOUBLE:
		case Types.NUMERIC:
		case Types.DECIMAL:
			return true;
		}
		return false;
	}

	/**
	 * 读取指定结果集首行所有字段数据，并存储至request的attribute对象。
	 * 存储的属性名称为[prefix.字段名称]，属性值为字段值。
	 * @param request 请求对象。
	 * @param resultSet 结果集对象。
	 * @param prefix 存储的名称前缀，如果值为空将不添加前缀。
	 * @throws Exception 读取结果集过程发生异常。
	 */
	public static void loadFirstRow(HttpServletRequest request,
			ResultSet resultSet, String prefix) throws Exception {
		if (!resultSet.next())
			return;
		ResultSetMetaData meta = resultSet.getMetaData();
		int i, j = meta.getColumnCount();
		boolean hasPrefix = !StringUtil.isEmpty(prefix);
		String name;
		Object object;

		for (i = 1; i <= j; i++) {
			name = meta.getColumnLabel(i);
			name = DbUtil.getFieldName(name);
			if (hasPrefix)
				name = StringUtil.concat(prefix, ".", name);
			object = DbUtil.getObject(resultSet, i, meta.getColumnType(i));
			if (object instanceof ResultSet || object instanceof InputStream)
				WebUtil.setObject(request, SysUtil.getId(), object);
			request.setAttribute(name, object);
		}
	}

	/**
	 * 获取指定字段类型的大类别。
	 * @param type 字段类型。
	 * @param precision
	 * @return 类别。
	 */
	public static String getTypeCategory(int type, int precision) {
		switch (type) {
		case Types.BIGINT:
		case Types.INTEGER:
		case Types.SMALLINT:
		case Types.TINYINT:
		case Types.BOOLEAN:
		case Types.BIT:
			// boolean bit为兼容不同数据库返回int型
			return "int";
		case Types.DECIMAL:
		case Types.DOUBLE:
		case Types.FLOAT:
		case Types.NUMERIC:
		case Types.REAL:
			if(precision>15){
				return "string";
			}
			return "float";
		case Types.TIMESTAMP:
		case Types.DATE:
		case Types.TIME:
			return "date";
		default:
			return "string";
		}
	}

	/**
	 * 获得结果集当前记录指定索引号大文本字段的字符串值。大文本字段通常指类似CLOB类型的字段。
	 * @param rs 结果集对象。
	 * @param index 字段索引号。
	 * @return 字段值。
	 * @throws Exception 读取过程发生异常。
	 */
	public static String getText(ResultSet rs, int index) throws Exception {
		return (String) getObject(rs, index, -1);
	}

	/**
	 * 获得结果集当前记录指定名称大文本字段的字符串值。大文本字段通常指类似CLOB类型的字段。
	 * @param rs 结果集对象。
	 * @param fieldName 字段名称。
	 * @return 字段值。
	 * @throws Exception 读取过程发生异常。
	 */
	public static String getText(ResultSet rs, String fieldName)
			throws Exception {
		return (String) getObject(rs, fieldName, -1);
	}

	/**
	 * 设置PreparedStatement指定索引号参数的大文本值。大文本值将使用字符串流的方式进行设置。
	 * @param statement PreparedStatement对象。
	 * @param index 参数引号。
	 * @param value 设置的值。
	 * @throws Exception 设置参数值过程发生异常。
	 */
	public static void setText(PreparedStatement statement, int index,
			String value) throws Exception {
		setObject(statement, index, -1, value);
	}

	/**
	 * 获取CallableStatement指定索引号的参数值。
	 * @param statement CallableStatement对象。
	 * @param index 参数索引号。
	 * @param type 参数类型。
	 * @return 参数值。
	 * @throws Exception 获取参数值过程发生异常。
	 */
	public static Object getObject(CallableStatement statement, int index,
			int type) throws Exception {
		Object obj;
		switch (type) {
		case Types.CHAR:
		case Types.NCHAR:
		case Types.VARCHAR:
		case Types.NVARCHAR:
			obj = statement.getString(index);
			break;
		case Types.INTEGER:
			obj = statement.getInt(index);
			break;
		case Types.TINYINT:
			obj = statement.getByte(index);
			break;
		case Types.SMALLINT:
			obj = statement.getShort(index);
			break;
		case Types.BIGINT:
			obj = statement.getLong(index);
			break;
		case Types.REAL:
		case Types.FLOAT:
			obj = statement.getFloat(index);
			break;
		case Types.DOUBLE:
			obj = statement.getDouble(index);
			break;
		case Types.DECIMAL:
		case Types.NUMERIC:
			obj = statement.getBigDecimal(index);
			break;
		case Types.TIMESTAMP:
			obj = statement.getTimestamp(index);
			break;
		case Types.DATE:
			obj = statement.getDate(index);
			break;
		case Types.TIME:
			obj = statement.getTime(index);
			break;
		case Types.BOOLEAN:
		case Types.BIT:
			// boolean bit为兼容不同数据库返回int型
			obj = statement.getBoolean(index) ? 1 : 0;
			break;
		case Types.LONGVARCHAR:
		case Types.LONGNVARCHAR:
		case Types.CLOB:
		case Types.NCLOB:
			Reader rd = statement.getCharacterStream(index);
			if (rd == null)
				obj = null;
			else
				obj = SysUtil.readString(rd);
			break;
		default:
			obj = statement.getObject(index);
		}
		if (statement.wasNull())
			return null;
		else
			return obj;
	}

	/**
	 * 获取结果集当前记录指定索引号的字段值。
	 * @param rs 结果集。
	 * @param index 字段索引号。
	 * @param type 字段类型。
	 * @return 字段值。
	 * @throws Exception 获取字段值过程发生异常。
	 */
	public static Object getObject(ResultSet rs, int index, int type)
			throws Exception {
		Object obj;
		switch (type) {
		case Types.CHAR:
		case Types.NCHAR:
		case Types.VARCHAR:
		case Types.NVARCHAR:
			obj = rs.getString(index);
			break;
		case Types.INTEGER:
			obj = rs.getInt(index);
			break;
		case Types.TINYINT:
			obj = rs.getByte(index);
			break;
		case Types.SMALLINT:
			obj = rs.getShort(index);
			break;
		case Types.BIGINT:
			obj = rs.getLong(index);
			break;
		case Types.REAL:
		case Types.FLOAT:
			obj = rs.getFloat(index);
			break;
		case Types.DOUBLE:
			obj = rs.getDouble(index);
			break;
		case Types.DECIMAL:
		case Types.NUMERIC:
			obj = rs.getBigDecimal(index);
			break;
		case Types.TIMESTAMP:
			obj = rs.getTimestamp(index);
			break;
		case Types.DATE:
			obj = rs.getDate(index);
			break;
		case Types.TIME:
			obj = rs.getTime(index);
			break;
		case Types.BOOLEAN:
		case Types.BIT:
			// boolean bit为兼容不同数据库返回int型
			obj = rs.getBoolean(index) ? 1 : 0;
			break;
		case Types.LONGVARCHAR:
		case Types.LONGNVARCHAR:
		case Types.CLOB:
		case Types.NCLOB:
			Reader rd = rs.getCharacterStream(index);
			if (rd == null)
				obj = null;
			else
				obj = SysUtil.readString(rd);
			break;
		case Types.BLOB:
		case Types.BINARY:
		case Types.VARBINARY:
		case Types.LONGVARBINARY:
			InputStream is = rs.getBinaryStream(index);
			if (is != null)
				is.close();// 读取之后再关闭wasNull方法才可判断是否为空
			obj = "(blob)";
			// 如果需要读取数据可直接读取流
			break;
		default:
			obj = rs.getObject(index);
		}
		if (rs.wasNull())
			return null;
		else
			return obj;
	}

	/**
	 * 获取结果集当前记录指定名称的字段值。
	 * @param rs 结果集。
	 * @param fieldName 字段名称。
	 * @param type 字段类型。
	 * @return 字段值。
	 * @throws Exception 获取字段值过程发生异常。
	 */
	public static Object getObject(ResultSet rs, String fieldName, int type)
			throws Exception {
		Object obj;
		switch (type) {
		case Types.CHAR:
		case Types.NCHAR:
		case Types.VARCHAR:
		case Types.NVARCHAR:
			obj = rs.getString(fieldName);
			break;
		case Types.INTEGER:
			obj = rs.getInt(fieldName);
			break;
		case Types.TINYINT:
			obj = rs.getByte(fieldName);
			break;
		case Types.SMALLINT:
			obj = rs.getShort(fieldName);
			break;
		case Types.BIGINT:
			obj = rs.getLong(fieldName);
			break;
		case Types.REAL:
		case Types.FLOAT:
			obj = rs.getFloat(fieldName);
			break;
		case Types.DOUBLE:
			obj = rs.getDouble(fieldName);
			break;
		case Types.DECIMAL:
		case Types.NUMERIC:
			obj = rs.getBigDecimal(fieldName);
			break;
		case Types.TIMESTAMP:
			obj = rs.getTimestamp(fieldName);
			break;
		case Types.DATE:
			obj = rs.getDate(fieldName);
			break;
		case Types.TIME:
			obj = rs.getTime(fieldName);
			break;
		case Types.BOOLEAN:
		case Types.BIT:
			// boolean bit为兼容不同数据库返回int型
			obj = rs.getBoolean(fieldName) ? 1 : 0;
			break;
		case Types.LONGVARCHAR:
		case Types.LONGNVARCHAR:
		case Types.CLOB:
		case Types.NCLOB:
			Reader rd = rs.getCharacterStream(fieldName);
			if (rd == null)
				obj = null;
			else
				obj = SysUtil.readString(rd);
			break;
		case Types.BLOB:
		case Types.BINARY:
		case Types.VARBINARY:
		case Types.LONGVARBINARY:
			InputStream is = rs.getBinaryStream(fieldName);
			if (is != null)
				is.close();// 读取之后再关闭wasNull方法才可判断是否为空
			obj = "(blob)";
			// 如果需要读取数据可直接读取流
			break;
		default:
			obj = rs.getObject(fieldName);
		}
		if (rs.wasNull())
			return null;
		else
			return obj;
	}

	/**
	 * 设置PreparedStatement对象指定索引号的参数值。
	 * @param statement PreparedStatement对象。
	 * @param index 参数索引。
	 * @param type 参数类型。
	 * @param object 参数值。
	 * @throws Exception 设置参数值过程发生异常。
	 */
	public static void setObject(PreparedStatement statement, int index,
			int type, Object object) throws Exception {
		if (object == null || object instanceof String) {
			String value;

			if (object == null)
				value = null;
			else
				value = (String) object;
			if (StringUtil.isEmpty(value))
				statement.setNull(index, type);
			else {
				switch (type) {
				case Types.CHAR:
				case Types.NCHAR:
				case Types.VARCHAR:
				case Types.NVARCHAR:
					if (Var.emptyString.equals(value))
						statement.setString(index, "");
					else
						statement.setString(index, value);
					break;
				case Types.INTEGER:
					statement.setInt(index, Integer.parseInt(StringUtil
							.convertBool(value)));
					break;
				case Types.TINYINT:
					statement.setByte(index, Byte.parseByte(StringUtil
							.convertBool(value)));
					break;
				case Types.SMALLINT:
					statement.setShort(index, Short.parseShort(StringUtil
							.convertBool(value)));
					break;
				case Types.BIGINT:
					statement.setLong(index, Long.parseLong(StringUtil
							.convertBool(value)));
					break;
				case Types.REAL:
				case Types.FLOAT:
					statement.setFloat(index, Float.parseFloat(StringUtil
							.convertBool(value)));
					break;
				case Types.DOUBLE:
					statement.setDouble(index, Double.parseDouble(StringUtil
							.convertBool(value)));
					break;
				case Types.DECIMAL:
				case Types.NUMERIC:
					statement.setBigDecimal(index, new BigDecimal(StringUtil
							.convertBool(value)));
					break;
				case Types.TIMESTAMP:
					statement.setTimestamp(index, Timestamp.valueOf(DateUtil
							.fixTimestamp(value, false)));
					break;
				case Types.DATE:
					// 类似Oracle驱动早期版本有bug，把Date型(含时间）映射为Types.Date
					// 判断如果值有空格使用timestamp类型处理
					if (value.indexOf(' ') != -1)
						statement.setTimestamp(index, Timestamp
								.valueOf(DateUtil.fixTimestamp(value, false)));
					else
						statement.setDate(index, java.sql.Date.valueOf(DateUtil
								.fixTimestamp(value, true)));
					break;
				case Types.TIME:
					// 如果值有空格或'-'使用timestamp类型处理
					if (value.indexOf(' ') != -1 || value.indexOf('-') != -1)
						statement.setTimestamp(index, Timestamp
								.valueOf(DateUtil.fixTimestamp(value, false)));
					else
						statement.setTime(index, Time.valueOf(DateUtil
								.fixTime(value)));
					break;
				case Types.BOOLEAN:
				case Types.BIT:
					statement.setBoolean(index, StringUtil.getBool(value));
					break;
				case Types.LONGVARCHAR:
				case Types.LONGNVARCHAR:
				case Types.CLOB:
				case Types.NCLOB:
					statement.setCharacterStream(index,
							new StringReader(value), value.length());
					break;
				case Types.BLOB:
				case Types.BINARY:
				case Types.VARBINARY:
				case Types.LONGVARBINARY:
					// 字符串存储到二进制字段视为BASE64编码
					InputStream is = new ByteArrayInputStream(StringUtil
							.decodeBase64(value));
					statement.setBinaryStream(index, is, is.available());
					break;
				default:
					statement.setObject(index, value, type);
				}
			}
		} else {
			if (object instanceof InputStream)
				statement.setBinaryStream(index, (InputStream) object,
						((InputStream) object).available());
			else if (object instanceof java.util.Date)
				statement.setTimestamp(index, new Timestamp(
						((java.util.Date) object).getTime()));
			else
				statement.setObject(index, object, type);
		}
	}

	/**
	 * 运行SQL语句，并获得返回值。可能返回值为结果集，影响记录数或输出参数结果Map。
	 * @param request 请求对象。用户获取参数和存取值。
	 * @param sql SQL语句。
	 * @param jndi 数据库连接jndi。
	 * @return 运行的结果。
	 * @throws Exception 运行SQL异常。
	 */
	public static Object run(HttpServletRequest request, String sql, String jndi)
			throws Exception {
		Query query = new Query();
		query.request = request;
		query.sql = sql;
		query.jndi = jndi;
		return query.run();
	}

	/**
	 * 在默认数据库运行SQL语句，并获得返回值。可能返回值为结果集，影响记录数或输出参数结果Map。
	 * @param request 请求对象。用户获取参数和存取值。
	 * @param sql SQL语句。
	 * @return 运行的结果。
	 * @throws Exception 运行SQL异常。
	 */
	public static Object run(HttpServletRequest request, String sql)
			throws Exception {
		return run(request, sql, null);
	}

	/**
	 * 在默认数据库运行上下文关联的SQL更新语句。SQL参数取自表字段同名的request参数。
	 * @param request 请求对象。
	 * @param tableName 表名。
	 * @param mode SQL语句模式：'insert', 'update', 'delete'。
	 * @throws Exception 运行SQL异常。
	 */
	public static void update(HttpServletRequest request, String tableName,
			String mode) throws Exception {
		Updater updater = new Updater();

		updater.request = request;
		updater.tableName = tableName;
		updater.mode = mode;
		updater.run();
	}

	/**
	 * 根据变量设置有条件地把字段名称转换为大写，只有带#号的字段才不转换为大写。
	 * @param fieldName 字段名称。
	 * @return 转换为大写后的字段名称或字段名称本身。
	 */
	public static String getFieldName(String fieldName) {
		if (Var.forceUpperCase) {
			if (fieldName.startsWith("#"))
				return fieldName.substring(1);
			else
				return fieldName.toUpperCase();
		} else
			return fieldName;
	}

	/**
	 * 获取结果集元数据的字段定义信息。
	 * @param meta 结果集元数据。
	 * @param dictTableNames 字典表名列表，如果该值不是null，将创建字段对应的键值字段定义。
	 * 键值字段名称为“字段名__V”。 
	 * @param dictFieldsMap 字典定义中字段与表的对应关系。用于多表重名字段的表名指定。
	 * @param keyDefines 字段值转换为键值定义的名称列表。
	 * @return 元数据定义。
	 * @throws Exception 读取过程发生异常。
	 */
	public static JSONArray getFields(ResultSetMetaData meta,
			String[] dictTableNames, JSONObject dictFieldsMap,
			JSONObject keyDefines) throws Exception {
		int i, j = meta.getColumnCount(), k, type,precision;
		String name, format, category, mapTable[] = new String[1];
		JSONArray ja = new JSONArray();
		JSONObject jo;
		DictRecord dictRecord;
		boolean hasDict = dictTableNames != null;

		for (i = 0; i < j; i++) {
			k = i + 1;
			jo = new JSONObject();
			name = meta.getColumnLabel(k);
			name = DbUtil.getFieldName(name);
			precision=meta.getPrecision(k);
			if (StringUtil.isEmpty(name))
				name = "FIELD" + Integer.toString(k);
			type = meta.getColumnType(k);
			if (keyDefines != null && keyDefines.has(name))
				category = "string";
			else
				category = getTypeCategory(type,precision);
			switch (type) {
			case Types.TIMESTAMP:
				format = "Y-m-d H:i:s.u";
				break;
			case Types.DATE:
				format = "Y-m-d";
				break;
			case Types.TIME:
				format = "H:i:s";
				break;
			default:
				format = null;
			}
			jo.put("name", name);
			jo.put("type", category);
			if (format != null)
				jo.put("dateFormat", format);
			if (category.equals("string"))
				jo.put("useNull", false);
			ja.put(jo);
			if (hasDict) {
				dictRecord = null;
				if (dictFieldsMap != null) {
					mapTable[0] = dictFieldsMap.optString(name);
					if (!StringUtil.isEmpty(mapTable[0]))
						dictRecord = Dictionary.find(mapTable, name);
				}
				if (dictRecord != null)
					dictRecord = Dictionary.find(dictTableNames, name);
				if (dictRecord != null && dictRecord.keyName != null) {
					jo = new JSONObject();
					jo.put("name", name + "__V");
					jo.put("type", "string");
					jo.put("useNull", false);
					ja.put(jo);
				}
			}
		}
		return ja;
	}

	/**
	 * 获取结果集元数据列模型定义。
	 * @param meta 结果集元数据。
	 * @param dictTableNames 以逗号分隔的字典定义表名列表。
	 * @param dictFieldsMap 字典定义中字段与表的对应关系。用于多表重名字段的表名指定。
	 * @param keyDefines 字段值转换为键值定义的名称列表。
	 * @return 列模型定义。
	 * @throws Exception 读取过程发生异常。
	 */
	public static String getColumns(ResultSetMetaData meta,
			String[] dictTableNames, JSONObject dictFieldsMap,
			JSONObject keyDefines) throws Exception {
		int i, j = meta.getColumnCount(), index, len, type, fieldNameLen, scaleNum;
		String fieldName, category, editor, precision, scale;
		String dictSize, dictScale, mapTable[] = new String[1], keyItems = null;
		StringBuilder buf = new StringBuilder();
		DictRecord fieldDict = null;
		boolean isDateTime, hasRenderer, hasFieldDict, hasKeyName;

		buf.append('[');
		buf.append("{\"xtype\":\"rownumberer\"}");
		for (i = 0; i < j; i++) {
			index = i + 1;
			fieldName = meta.getColumnLabel(index);
			fieldName = DbUtil.getFieldName(fieldName);
			if (StringUtil.isEmpty(fieldName))
				fieldName = "FIELD" + Integer.toString(index);
			fieldDict = null;
			if (dictFieldsMap != null) {
				mapTable[0] = dictFieldsMap.optString(fieldName);
				if (!StringUtil.isEmpty(mapTable[0]))
					fieldDict = Dictionary.find(mapTable, fieldName);
			}
			if (fieldDict == null && dictTableNames != null)
				fieldDict = Dictionary.find(dictTableNames, fieldName);
			hasFieldDict = fieldDict != null;
			hasKeyName = hasFieldDict && fieldDict.keyName != null;
			if (keyDefines != null && keyDefines.has(fieldName)) {
				len = 200;
				precision = "200";
				scale = "0";
				type = Types.VARCHAR;
			} else {
				if (hasKeyName)
					len = 10;
				else {
					len = meta.getPrecision(index);// 代替getColumnDisplaySize
					if (len <= 0)
						len = 100;
				}
				precision = Integer.toString(len);
				scaleNum = meta.getScale(index);
				if (scaleNum < 0)
					scaleNum = 100;
				scale = Integer.toString(scaleNum);
				type = meta.getColumnType(index);
				if ((type == Types.NVARCHAR || type == Types.VARCHAR)
						&& len > Var.stringAsText)
					type = Types.CLOB;
			}
			fieldNameLen = fieldName.length();
			fieldName = StringUtil.quote(fieldName);
			buf.append(',');
			buf.append("{\"dataIndex\":");
			buf.append(fieldName);
			buf.append(",\"text\":");
			buf.append(hasFieldDict && fieldDict.dispText != null ? StringUtil
					.quote(fieldDict.dispText) : fieldName);
			editor = null;
			isDateTime = false;
			hasRenderer = hasFieldDict
					&& (fieldDict.renderer != null || hasKeyName);
			switch (type) {
			case Types.TIMESTAMP:
				category = "timestamp";
				editor = "\"datetimefield\"";
				isDateTime = true;
				len = 18;
				break;
			case Types.DATE:
				category = "date";
				editor = "\"datefield\"";
				isDateTime = true;
				len = 12;
				break;
			case Types.TIME:
				category = "time";
				editor = "\"timefield\"";
				isDateTime = true;
				len = 10;
				if (!hasRenderer) {
					// 时间已经转为js date型，因此需重写renderer
					buf.append(",\"renderer\":Wb.timeRenderer");
				}
				break;
			case Types.BIGINT:
			case Types.INTEGER:
			case Types.SMALLINT:
			case Types.TINYINT:
			case Types.DECIMAL:
			case Types.DOUBLE:
			case Types.FLOAT:
			case Types.NUMERIC:
			case Types.REAL:
				category = "number";
				if (!hasKeyName)
					buf.append(",\"align\":\"right\"");
				if (hasFieldDict) {
					if (fieldDict.fieldSize == -1)
						dictSize = precision;
					else
						dictSize = Integer.toString(fieldDict.fieldSize);
					if (fieldDict.decimalPrecision == -1)
						dictScale = scale;
					else
						dictScale = Integer
								.toString(fieldDict.decimalPrecision);
					editor = StringUtil.concat(
							"\"numberfield\",\"decimalPrecision\":", dictScale);
					if (fieldDict.validator == null)
						editor = StringUtil.concat(editor,
								",\"validator\":Wb.numValidator(", dictSize,
								",", dictScale, ")");
				} else {
					editor = StringUtil.concat(
							"\"numberfield\",\"decimalPrecision\":", scale,
							",\"validator\":Wb.numValidator(", precision, ",",
							scale, ")");
				}
				break;
			case Types.LONGVARCHAR:
			case Types.LONGNVARCHAR:
			case Types.CLOB:
			case Types.NCLOB:
				category = "text";
				editor = "\"textarea\",\"height\":120";
				len = 18;
				break;
			case Types.BINARY:
			case Types.VARBINARY:
			case Types.LONGVARBINARY:
			case Types.BLOB:
				category = "blob";
				editor = "\"filefield\"";
				len = 16;// 默认长度兼容英文需要
				if (!hasRenderer) {
					buf.append(",\"renderer\":Wb.blobRenderer");
				}
				break;
			case Types.BOOLEAN:
			case Types.BIT:
				category = "number";
				if (!hasKeyName)
					buf.append(",\"align\":\"right\"");
				editor = "\"numberfield\",\"maxValue\":1,\"minValue\":0";
				break;
			default:
				category = "string";
				editor = "\"textfield\",\"maxLength\":"
						+ (hasFieldDict && fieldDict.fieldSize != -1 ? Integer
								.toString(fieldDict.fieldSize) : precision);
			}
			if (hasFieldDict) {
				// 日期时间类型字段允许通过设置字段长度来重置使用何种编辑器
				if (isDateTime && fieldDict.fieldSize != -1) {
					switch (fieldDict.fieldSize) {
					case 1:
						editor = "\"datefield\"";
						len = 12;
						break;
					case 2:
						editor = "\"timefield\"";
						len = 10;
						break;
					case 3:
						category = "timestamp";
						editor = "\"datetimefield\"";
						len = 18;
						break;
					}
				}
				if (!fieldDict.listable)
					buf.append(",\"hidden\":true,\"showInMenu\":false");
				if (fieldDict.dispFormat != null) {
					buf.append(",\"format\":");
					buf.append(StringUtil.quote(fieldDict.dispFormat));
				}
				if (hasRenderer) {
					if (fieldDict.renderer != null) {
						buf
								.append(",\"renderer\":function(value,metaData,record,rowIndex,colIndex,store,view){");
						buf.append(fieldDict.renderer);
						buf.append('}');
					} else {
						buf.append(",\"renderer\":Wb.kvRenderer");
					}
				}
				if (fieldDict.autoWrap)
					buf.append(",\"autoWrap\":true");
			}
			buf.append(",\"category\":\"");
			buf.append(category);
			if (hasFieldDict && fieldDict.dispWidth != -1) {
				if (fieldDict.dispWidth < 10) {
					// 如果值小于10视为flex
					buf.append("\",\"flex\":");
					buf.append(fieldDict.dispWidth);
				} else {
					buf.append("\",\"width\":");
					buf.append(fieldDict.dispWidth);
				}
			} else {
				buf.append("\",\"width\":");
				len = Math.max(len, fieldNameLen + 3);
				if (len < 5)
					len = 5;
				if (len > 18)
					len = 18;
				buf.append(len * 10);
			}
			if (hasKeyName) {
				keyItems = KVBuffer.getList(fieldDict.keyName);
				buf.append(",\"keyName\":");
				buf.append(StringUtil.quote(fieldDict.keyName));
				buf.append(",\"keyItems\":");
				buf.append(keyItems);
			}
			if ((!hasFieldDict || fieldDict.editable) && editor != null) {
				if ("blob".equals(category))
					buf.append(",\"blobEditor\":{\"xtype\":");// 用于对话框编辑模式
				else
					buf.append(",\"editor\":{\"xtype\":");
				if (hasKeyName) {
					buf.append("\"combo\",\"keyName\":");
					buf.append(StringUtil.quote(fieldDict.keyName));
					buf
							.append(",\"displayField\":\"V\",\"valueField\":\"K\",\"forceSelection\":true,\"queryMode\":\"local\",\"store\":{\"fields\":[\"K\",\"V\"],\"sorters\":\"K\",\"data\":");
					buf.append(keyItems);
					buf.append('}');
				} else
					buf.append(editor);
				if (hasFieldDict) {
					if (fieldDict.allowBlank == null) {
						if (meta.isNullable(index) == ResultSetMetaData.columnNoNulls)
							buf
									.append(",\"allowBlank\":false,\"required\":true");
					} else if (!fieldDict.allowBlank)
						buf.append(",\"allowBlank\":false,\"required\":true");
					if (fieldDict.readOnly == null) {
						if (meta.isReadOnly(index))
							buf.append(",\"readOnly\":true");
					} else if (fieldDict.readOnly)
						buf.append(",\"readOnly\":true");
					if (fieldDict.validator != null) {
						buf.append(",\"validator\":function(value){");
						buf.append(fieldDict.validator);
						buf.append('}');
					}
				} else {
					if (meta.isNullable(index) == ResultSetMetaData.columnNoNulls)
						buf.append(",\"allowBlank\":false,\"required\":true");
					if (meta.isReadOnly(index))
						buf.append(",\"readOnly\":true");
				}
				buf.append("},\"editable\":true");
			}
			buf.append(",\"metaType\":\"");
			buf.append(meta.getColumnTypeName(index));
			buf.append("\",\"metaRequired\":");
			buf
					.append(meta.isNullable(index) == ResultSetMetaData.columnNoNulls ? "true"
							: "false");
			buf.append(",\"metaSize\":");
			buf.append(precision);
			buf.append(",\"metaScale\":");
			buf.append(scale);
			buf.append('}');
		}
		buf.append(']');
		return buf.toString();
	}

	/**
	 * 输出结果集首行记录首个BLOB字段至客户端。如果记录存在第2个字段，作为输出的文件名，
	 * 如果记录存在第3个字段，作为输出内容的长度。
	 * @param resultSet 结果集。
	 * @param request 请求对象。
	 * @param response 响应对象
	 * @param contentType 内容类型，可为download,stream,image或其他用户自定义头信息。
	 * 如果指定为image类型但第2个字段未指定图片格式，默认为image/jpg。
	 * 该类型可以由用户自定义，如image/png。
	 * @throws Exception 读过数据库或输出过程发生异常。
	 */
	public static void outputBlob(ResultSet resultSet,
			HttpServletRequest request, HttpServletResponse response,
			String contentType) throws Exception {
		InputStream inputStream = null;
		try {
			OutputStream outputStream;
			ResultSetMetaData meta = resultSet.getMetaData();
			int rowCount = meta.getColumnCount();
			String name = DbUtil.getFieldName(meta.getColumnLabel(1)), size = null;

			if (StringUtil.isEmpty(name))
				name = "blob";
			response.reset();
			if (resultSet.next()) {
				switch (rowCount) {
				case 1:
					inputStream = resultSet.getBinaryStream(1);
					break;
				case 2:
					name = resultSet.getString(2);
					inputStream = resultSet.getBinaryStream(1);
					break;
				case 3:
					name = resultSet.getString(2);
					size = resultSet.getString(3);
					inputStream = resultSet.getBinaryStream(1);
					break;
				}
			} else
				throw new Exception("Empty ResultSet.");
			outputStream = response.getOutputStream();
			if ("download".equals(contentType))
				contentType = "application/force-download";
			else if ("stream".equals(contentType))
				contentType = "application/octet-stream";
			else if ("image".equals(contentType)) {
				if (inputStream == null) {
					File nullGif = new File(Base.path, "wb/images/null.gif");
					inputStream = new FileInputStream(nullGif);
					size = Long.toString(nullGif.length());
					contentType = "image/gif";
				} else {
					String extName = FileUtil.getFileExt(name);
					if (extName.isEmpty())
						contentType = "image/jpg";
					else
						contentType = "image/" + extName;
				}
			}
			response.setHeader("content-type", contentType);
			response.setHeader("content-disposition", "attachment;"
					+ WebUtil.encodeFilename(request, name));
			if (size != null)
				response.setHeader("content-length", size);
			if (inputStream != null)
				IOUtils.copy(inputStream, outputStream);
			response.flushBuffer();
		} finally {
			IOUtils.closeQuietly(inputStream);
		}
	}

	/**
	 * 获取指定表的自动化insert, update, delete和select SQL语句。这些语句为带参数的SQL语句，
	 * 通常用于自动化作业，比如使用updater控件更新数据。
	 * @param jndi 数据库连接jndi。
	 * @param tableName 表名。
	 * @param ignoreBlob 是否忽略blob字段。
	 * @param scriptType 0原生，1参数，2替换。
	 * @param request 请求对象，用于判断是否对指定上传的blob字段进行处理。
	 * @param fields 生成where语句前的字段列表。如果为null生成所有字段。
	 * 否则只使用存在于该列表中的字段。
	 * @param whereFields 生成where语句的字段列表。如果为null使用所有有效字段，
	 * 否则只使用存在于该列表中的字段。
	 * @param fieldsMap 字段映射对象，用于把键指定字段映射为值指定字段。
	 * @return SQL语句数组，依次为insert, update, delete和select语句。
	 * @throws Exception 生成SQL语句发生异常。
	 */
	public static String[] buildSQLs(String jndi, String tableName,
			boolean ignoreBlob, int scriptType, HttpServletRequest request,
			JSONObject fields, JSONObject whereFields, JSONObject fieldsMap)
			throws Exception {
		String[] sqls = new String[4];
		Connection conn = null;
		PreparedStatement st = null;
		ResultSet rs = null;
		ResultSetMetaData meta;
		StringBuilder selectFields = new StringBuilder();
		StringBuilder insertFields = new StringBuilder();
		StringBuilder insertParams = new StringBuilder();
		StringBuilder condition = new StringBuilder();
		StringBuilder updateParams = new StringBuilder();
		String fieldName, fieldValueName, param, typeName, mapName;
		int i, j, type, precision, scale;
		boolean isFirstSelect = true, isFirstUpdate = true, isFirstCondi = true;
		boolean required, readOnly, isText, isBlob, isFloat, hasRequest = request != null;

		try {
			if (request == null)
				conn = DbUtil.getConnection(jndi);
			else
				conn = DbUtil.getConnection(request, jndi);
			st = conn.prepareStatement("select * from " + tableName
					+ " where 1=0");
			rs = st.executeQuery();
			meta = rs.getMetaData();
			j = meta.getColumnCount() + 1;
			for (i = 1; i < j; i++) {
				type = meta.getColumnType(i);
				typeName = getTypeName(type);
				precision = meta.getPrecision(i);
				if (precision <= 0)
					precision = 100;
				isText = isTextField(type) || precision > 10000;
				isBlob = isBlobField(type);
				scale = meta.getScale(i);
				if (scale < 0)
					scale = 100;
				isFloat = maybeFloatField(type) && scale > 0;
				if (isFloat && Var.useDouble) {
					type = Types.DOUBLE;
					typeName = "double";
				}
				required = meta.isNullable(i) == ResultSetMetaData.columnNoNulls;
				readOnly = meta.isReadOnly(i);
				fieldName = meta.getColumnLabel(i);
				fieldName = DbUtil.getFieldName(fieldName);
				fieldValueName = fieldName;
				fieldName = StringUtil.quoteIf(fieldName);
				if (fieldsMap != null) {
					mapName = fieldsMap.optString(fieldValueName, null);
					if (mapName != null)
						fieldValueName = mapName;
				}
				// $fieldName!=1表示忽略对blob空字段的处理，通常在修改记录时未更改blob即忽略处理。
				if (isBlob
						&& hasRequest
						&& StringUtil.isEmpty(WebUtil.fetch(request,
								fieldValueName))
						&& !"1".equals(WebUtil.fetch(request, "$"
								+ fieldValueName)))
					continue;
				if ((!ignoreBlob || !isBlob)
						&& (fields == null || (fields.has(fieldValueName) || fields
								.has("$" + fieldValueName)))) {
					if (isFirstSelect)
						isFirstSelect = false;
					else {
						selectFields.append(',');
					}
					selectFields.append(fieldName);
					if (!readOnly) {
						if (isFirstUpdate)
							isFirstUpdate = false;
						else {
							insertFields.append(',');
							insertParams.append(',');
							updateParams.append(',');
						}
						switch (scriptType) {
						case 1:
							if (typeName == null)
								param = StringUtil.concat("{?", fieldValueName,
										"?}");
							else
								param = StringUtil.concat("{?", typeName, ".",
										fieldValueName, "?}");
							break;
						case 2:
							param = StringUtil.concat("{#", fieldValueName,
									"#}");
							break;
						default:
							param = fieldValueName;
						}
						insertFields.append(fieldName);
						insertParams.append(param);
						updateParams.append(fieldName);
						updateParams.append('=');
						updateParams.append(param);
					}
				}
				if (!isText
						&& !isBlob
						&& !isFloat
						&& (whereFields == null || whereFields
								.has(fieldValueName))) {
					if (isFirstCondi)
						isFirstCondi = false;
					else {
						condition.append(" and ");
					}
					condition
							.append(getCondition(fieldName, fieldValueName,
									isStringField(type), typeName, required,
									scriptType));
				}
			}
			sqls[0] = StringUtil.concat("insert into ", tableName, " (",
					insertFields.toString(), ") values (", insertParams
							.toString(), ")");
			sqls[1] = StringUtil.concat("update ", tableName, " set ",
					updateParams.toString(), " where ", condition.toString());
			sqls[2] = StringUtil.concat("delete from ", tableName, " where ",
					condition.toString());
			sqls[3] = StringUtil.concat("select ", selectFields.toString(),
					" from ", tableName, " where ", condition.toString());
		} finally {
			close(rs);
			close(st);
			if (request == null)
				close(conn); // request!=null共享连接且自动释放
		}
		return sqls;
	}

	/**
	 * 获取条件表达式SQL语句片段。
	 * @param fieldName 字段名称。
	 * @param typeName 字段类型。
	 * @param required 字段是否为必须。
	 * @param scriptType 生成的脚本类型。
	 * @return 条件表达式。
	 */
	private static String getCondition(String fieldName, String fieldValueName,
			boolean isStringField, String typeName, boolean required,
			int scriptType) {
		StringBuilder buf = new StringBuilder();
		switch (scriptType) {
		case 1:
			if (isStringField) {
				// 字符型处理的区别在于某些数据库允许输入空串
				buf.append("({?#");
				buf.append(fieldValueName);
				buf.append("?} is null and (");
				buf.append(fieldName);
				buf.append(" is null or ");
				buf.append(fieldName);
				buf.append("='') or ");
				buf.append(fieldName);
				buf.append("={?");
				if (typeName == null) {
					buf.append('#');
				} else {
					buf.append(typeName);
					buf.append(".#");
				}
				buf.append(fieldValueName);
				buf.append("?})");
			} else {
				if (!required) {
					buf.append("({?#");
					buf.append(fieldValueName);
					buf.append("?} is null and ");
					buf.append(fieldName);
					buf.append(" is null or ");
				}
				buf.append(fieldName);
				buf.append("={?");
				if (typeName == null) {
					buf.append('#');
				} else {
					buf.append(typeName);
					buf.append(".#");
				}
				buf.append(fieldValueName);
				if (required)
					buf.append("?}");
				else
					buf.append("?})");
			}
			break;
		case 2:
			if (!required) {
				buf.append("({##");
				buf.append(fieldValueName);
				buf.append("#} is null and ");
				buf.append(fieldName);
				buf.append(" is null or ");
			}
			buf.append(fieldName);
			buf.append("={##");
			buf.append(fieldValueName);
			if (required)
				buf.append("#}");
			else
				buf.append("#})");
			break;
		default:
			if (!required) {
				buf.append("(#");
				buf.append(fieldValueName);
				buf.append(" is null and ");
				buf.append(fieldName);
				buf.append(" is null or ");
			}
			buf.append(fieldName);
			buf.append("=#");
			buf.append(fieldValueName);
			if (!required)
				buf.append(')');
			break;
		}
		return buf.toString();
	}
}
