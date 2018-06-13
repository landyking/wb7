package com.wb.controls;

import com.wb.common.KVBuffer;

/**
 * Combo控件的解析类。
 */
public class ExtCombo extends ExtControl {
	protected void extendConfig() throws Exception {
		String keyName = gs("keyName");
		if (!keyName.isEmpty()) {
			if (hasItems)
				headerScript.append(',');
			else
				hasItems = true;
			headerScript
					.append("displayField:\"V\",valueField:\"K\",forceSelection:true,queryMode:\"local\",store:{fields:[\"K\",\"V\"],sorters:\"K\",data:");
			headerScript.append(KVBuffer.getList(keyName));
			headerScript.append('}');
		}
	}
}
