package com.wb.controls;

import org.json.JSONObject;

import com.wb.common.Var;
import com.wb.tool.Updater;

/**
 * 上下文绑定的数据库更新控件，见：{@link Updater}
 */
public class UpdaterControl extends Control {
	public void create() throws Exception {
		if (gb("disabled", false))
			return;
		Updater updater = new Updater();
		String value;

		updater.request = request;
		updater.jndi = gs("jndi");
		updater.tableName = gs("tableName");
		updater.transaction = gs("transaction");
		updater.isolation = gs("isolation");
		updater.type = gs("type");
		updater.batchUpdate = gb("batchUpdate", Var.batchUpdate);
		updater.uniqueUpdate = gb("uniqueUpdate", true);
		updater.sqlDelete = gs("sqlDelete");
		updater.sqlInsert = gs("sqlInsert");
		updater.sqlUpdate = gs("sqlUpdate");
		updater.paramDelete = gs("paramDelete");
		updater.paramInsert = gs("paramInsert");
		updater.paramUpdate = gs("paramUpdate");
		updater.whereFields = gs("whereFields");
		updater.useExistFields = gb("useExistFields", true);
		updater.mode = gs("mode");
		value = gs("fieldsMap");
		updater.fieldsMap = value.isEmpty() ? null : new JSONObject(value);
		updater.ignoreBlob = gb("ignoreBlob", false);
		updater.run();
	}
}