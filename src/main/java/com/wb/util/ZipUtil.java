package com.wb.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;

import com.syspatch.zip.ZipEntry;
import com.syspatch.zip.ZipInputStream;
import com.syspatch.zip.ZipOutputStream;
import com.wb.common.Var;

/**
 * 压缩工具方法类。
 */
public class ZipUtil {
	/**
	 * 压缩文件列表到输入流中。压缩包中的文件名称编码由变量sys.locale.filenameCharset设定，默认为空表示
	 *使用操作系统默认编码。
	 * @param source 需要压缩的文件列表。
	 * @param outputStream 压缩的文件输出到该流。
	 * @throws IOException 压缩过程中发生异常。
	 */
	public static void zip(File source[], OutputStream outputStream)
			throws IOException {
		ZipOutputStream zipStream = new ZipOutputStream(outputStream);
		zipStream.fileCharset = Var.getString("sys.locale.filenameCharset");
		try {
			for (File file : source)
				zip(file, zipStream, file.getName());
		} finally {
			zipStream.close();
		}
	}

	/**
	 * 压缩文件列表到指定文件中。压缩包中的文件名称编码由变量sys.locale.filenameCharset设定，
	 *默认为空表示使用操作系统默认编码。
	 * @param source 需要压缩的文件列表。
	 * @param zipFile 压缩的文件输出到该文件。
	 * @throws IOException 压缩过程中发生异常。
	 */
	public static void zip(File source[], File zipFile) throws Exception {
		zip(source, new FileOutputStream(zipFile));
	}

	/**
	 * 压缩文件或目录指输出流。
	 * @param source 被压缩的文件。
	 * @param zipStream 输出的文件。
	 * @param base 文件地址。
	 * @throws IOException 压缩过程发生异常。
	 */
	private static void zip(File source, ZipOutputStream zipStream, String base)
			throws IOException {
		ZipEntry entry;

		if (source.isDirectory()) {
			entry = new ZipEntry(base + '/');
			entry.setTime(source.lastModified());
			zipStream.putNextEntry(entry);
			if (!StringUtil.isEmpty(base))
				base += '/';
			File[] fileList = FileUtil.listFiles(source);
			for (File file : fileList)
				zip(file, zipStream, base + file.getName());
		} else {
			entry = new ZipEntry(base);
			entry.setTime(source.lastModified());
			zipStream.putNextEntry(entry);
			FileInputStream in = new FileInputStream(source);
			try {
				IOUtils.copy(in, zipStream);
			} finally {
				in.close();
			}
		}
	}

	/**
	 * 解压缩流中的文件至指定目录。压缩包中的文件名称编码由变量sys.locale.filenameCharset设定，
	 *默认为空表示使用操作系统默认编码。
	 * @param inputStream 需要解压缩的流。
	 * @param dest 流中的文件解压缩到该目录。
	 * @throws IOException 解压缩过程发生异常。
	 */
	public static void unzip(InputStream inputStream, File dest)
			throws IOException {
		ZipInputStream zipStream = new ZipInputStream(inputStream);
		zipStream.fileCharset = Var.getString("sys.locale.filenameCharset");
		ZipEntry z;
		File f;
		String name;
		FileOutputStream out;

		try {
			while ((z = zipStream.getNextEntry()) != null) {
				name = z.getName();
				if (z.isDirectory()) {
					name = name.substring(0, name.length() - 1);
					f = new File(dest, name);
					if (!f.exists())
						f.mkdir();
				} else {
					f = new File(dest, name);
					if (!f.exists())
						f.createNewFile();
					out = new FileOutputStream(f);
					try {
						IOUtils.copy(zipStream, out);
					} finally {
						out.close();
					}
				}
				f.setLastModified(z.getTime());
			}
		} finally {
			zipStream.close();
		}
	}

	/**
	 * 解压缩压缩文件中的文件至指定目录。压缩包中的文件名称编码由变量sys.locale.filenameCharset设定，
	 *默认为空表示使用操作系统默认编码。
	 * @param zipFile 需要解压缩的文件。
	 * @param dest 文件解压缩到该目录。
	 * @throws IOException 解压缩过程发生异常。
	 */
	public static void unzip(File zipFile, File dest) throws IOException {
		unzip(new FileInputStream(zipFile), dest);
	}
}