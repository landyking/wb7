package com.wb.tool;

/** 字典记录实体类。 */
public class DictRecord {
	/** 链接到字段id */
	public String linkTo;
	/** 是否显示在列表中 */
	public boolean listable;
	/** 是否可编辑 */
	public boolean editable;
	/** 显示名称，为空表示根据字段属性 */
	public String dispText;
	/** 显示宽度，-1表示根据字段属性 */
	public int dispWidth;
	/** 显示格式 */
	public String dispFormat;
	/** 自动换行 */
	public boolean autoWrap;
	/** 是否允许为空，null表示根据字段属性 */
	public Boolean allowBlank;
	/** 是否只读，null表示根据字段属性  */
	public Boolean readOnly;
	/** 键值名称 */
	public String keyName;
	/** 字段长度，-1表示根据字段属性 */
	public int fieldSize;
	/** 小数位数，-1表示根据字段属性 */
	public int decimalPrecision;
	/** 编辑时合法性验证函数 */
	public String validator;
	/** 自定义显示函数 */
	public String renderer;
}