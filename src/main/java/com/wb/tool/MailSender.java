package com.wb.tool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;

import com.wb.common.Base;
import com.wb.common.Var;
import com.wb.util.FileUtil;
import com.wb.util.StringUtil;

public class MailSender {
	private Session session;
	private Transport transport;

	public MailSender(String smtp, String username, String password,
			boolean needAuth) throws Exception {
		Properties props = new Properties();
		props.put("mail.smtp.host", smtp);
		props.put("mail.smtp.auth", Boolean.toString(needAuth));
		session = Session.getDefaultInstance(props, null);
		transport = session.getTransport("smtp");
		try {
			transport.connect(smtp, username, password);
		} catch (Throwable e) {
			close();
		}
	}

	public void close() throws Exception {
		transport.close();
	}

	public void send(String from, String to, String cc, String bcc,
			String title, String content) throws Exception {
		send(from, to, cc, bcc, title, content, null, null, null, null);
	}

	public void send(String from, String to, String cc, String bcc,
			String title, String content, String attachFiles,
			HttpServletRequest request, String attachObjects,
			String attachObjectNames) throws Exception {
		Multipart multipart = new MimeMultipart();
		MimeMessage message = new MimeMessage(session);
		int sepPos;

		sepPos = from.indexOf('<');
		if (sepPos != -1)
			message.setFrom(new InternetAddress(from.substring(sepPos + 1, from
					.length() - 1), from.substring(0, sepPos).trim()));
		else
			message.setFrom(new InternetAddress(from));
		message.setRecipients(Message.RecipientType.TO, InternetAddress
				.parse(to));
		if (!StringUtil.isEmpty(cc))
			message.setRecipients(Message.RecipientType.CC, InternetAddress
					.parse(cc));
		if (!StringUtil.isEmpty(bcc))
			message.setRecipients(Message.RecipientType.BCC, InternetAddress
					.parse(bcc));
		message.setSubject(title);
		message.setSentDate(new Date());
		addContent(multipart, content);
		attachFiles(multipart, attachFiles, request, attachObjects,
				attachObjectNames);
		message.setContent(multipart);
		message.saveChanges();
		transport.sendMessage(message, message.getAllRecipients());
	}

	private void addContent(Multipart multipart, String content)
			throws Exception {
		BodyPart bodyPart = new MimeBodyPart();

		bodyPart.setContent(content, "text/html;charset=utf-8");
		multipart.addBodyPart(bodyPart);
	}

	private void attachFiles(Multipart multipart, String attachFiles,
			HttpServletRequest request, String attachObjects,
			String attachObjectNames) throws Exception {
		boolean hasObjNames;
		int i, j;
		Object object;
		BodyPart bodyPart;

		if (!StringUtil.isEmpty(attachFiles)) {
			if (!attachFiles.startsWith("["))
				attachFiles = "[" + attachFiles + "]";
			JSONArray ja = new JSONArray(attachFiles);
			String file;

			j = ja.length();
			for (i = 0; i < j; i++) {
				bodyPart = new MimeBodyPart();
				file = ja.getString(i);
				bodyPart.setDataHandler(new DataHandler(new FileDataSource(
						new File(Base.path, file))));
				bodyPart.setFileName(MimeUtility.encodeText(FileUtil
						.getFilename(file)));
				bodyPart.setHeader("content-id", "attach" + i);
				multipart.addBodyPart(bodyPart);
			}
		}
		if (!StringUtil.isEmpty(attachObjects)) {
			String[] list, objNames = null;
			list = StringUtil.split(attachObjects, ",");
			hasObjNames = !StringUtil.isEmpty(attachObjectNames);
			if (hasObjNames)
				objNames = StringUtil.split(attachObjectNames, ",");
			j = list.length;
			for (i = 0; i < j; i++) {
				object = request.getAttribute(list[i]);
				DataSource dataSource;
				if (object != null) {
					if (object instanceof InputStream)
						dataSource = new BinDataSource((InputStream) object);
					else if (object instanceof byte[])
						dataSource = new BinDataSource((byte[]) object);
					else
						dataSource = new BinDataSource(object.toString());
					bodyPart = new MimeBodyPart();
					bodyPart.setDataHandler(new DataHandler(dataSource));
					if (hasObjNames)
						bodyPart.setFileName(MimeUtility
								.encodeText(objNames[i]));
					else
						bodyPart.setFileName(MimeUtility.encodeText(list[i]));
					bodyPart.setHeader("content-id", list[i]);
					multipart.addBodyPart(bodyPart);
				}
			}
		}
	}

	private class BinDataSource implements DataSource {
		private byte[] byteData;

		public BinDataSource(InputStream stream) throws IOException {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			int ch;
			while ((ch = stream.read()) != -1)
				os.write(ch);
			byteData = os.toByteArray();
		}

		public BinDataSource(byte[] data) {
			byteData = data;
		}

		public BinDataSource(String data) throws Exception {
			String charset = Var.getString("sys.locale.mailCharset");

			if (StringUtil.isEmpty(charset))
				byteData = data.getBytes();
			else
				byteData = data.getBytes(charset);
		}

		public InputStream getInputStream() throws IOException {
			return new ByteArrayInputStream(byteData);
		}

		public OutputStream getOutputStream() throws IOException {
			return null;
		}

		public String getContentType() {
			return "application/octet-stream";
		}

		public String getName() {
			return "dummy";
		}
	}
}
