package com.wb.controls;

public class Array extends ScriptControl {
	public void create() throws Exception {
		boolean parentRoot = Boolean.TRUE.equals(parentGeneral.opt("root"));
		if (parentRoot) {
			headerScript.append("app.");
			headerScript.append(gs("itemId"));
			headerScript.append("=[");
			footerScript.append("];");
		} else {
			if ("Array".equals(parentGeneral.opt("type"))) {
				headerScript.append("[");
			} else {
				headerScript.append(gs("itemId"));
				headerScript.append(":[");
			}
			if (lastNode)
				footerScript.append("]");
			else
				footerScript.append("],");
		}
	}
}
