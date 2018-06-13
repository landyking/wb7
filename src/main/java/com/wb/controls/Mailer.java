package com.wb.controls;

import com.wb.tool.MailSender;

/**
 * 发送邮件控件。
 */
public class Mailer extends Control {
	public void create() throws Exception {
		if (gb("disabled", false))
			return;
		MailSender mailSender = new MailSender(gs("smtp"), gs("username"),
				gs("password"), gb("needAuth", true));
		try {
			mailSender.send(gs("from"), gs("to"), gs("cc"), gs("bcc"),
					gs("title"), gs("content"), gs("attachFiles"), request,
					gs("attachObjects"), gs("attachObjectNames"));
		} finally {
			mailSender.close();
		}
	}
}
