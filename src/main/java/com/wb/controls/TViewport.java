package com.wb.controls;

public class TViewport extends ScriptControl {
	public void create() throws Exception {
		if (normalRunType) {
			// 属性在Ext.Setup中设置
			headerScript.append("this.add([");
			footerScript.insert(0, "]);");
		} else {
			// 把TViewport转换为Container处理
			ExtControl control = new ExtControl();
			control.init(request, response, controlData, controlMeta,
					parentGeneral, lastNode, normalRunType);
			control.create();
			headerScript.append(control.getHeaderScript());
			footerScript.insert(0, control.getFooterScript());
		}
	}
}
