package com.wb.controls;

import com.wb.common.KVBuffer;

/**
 * Column控件的解析类。
 */
public class ExtColumn extends ExtControl {
	protected void extendConfig() {
		String keyName = gs("keyName");
		if (!keyName.isEmpty()) {
			if (hasItems)
				headerScript.append(',');
			else
				hasItems = true;
			headerScript.append("renderer:Wb.kvRenderer,keyItems:");
			headerScript.append(KVBuffer.getList(keyName));
		}
	}
}