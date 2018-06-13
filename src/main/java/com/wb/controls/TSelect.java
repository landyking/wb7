package com.wb.controls;

import com.wb.common.KVBuffer;

/**
 * Touch Select控件的解析类。
 */
public class TSelect extends ExtControl {
	protected void extendConfig() {
		String keyName = gs("keyName");
		if (!keyName.isEmpty()) {
			if (hasItems)
				headerScript.append(',');
			else
				hasItems = true;
			headerScript
					.append("displayField:\"V\",valueField:\"K\",store:{fields:[\"K\",\"V\"],sorters:\"K\",data:");
			headerScript.append(KVBuffer.getList(keyName));
			headerScript.append('}');
		}
	}
}
