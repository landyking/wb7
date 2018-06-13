package com.wb.tool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import org.apache.commons.io.IOUtils;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.wb.common.Var;
import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * JS和CSS脚本压缩器。
 */
public class ScriptCompressor {
	/**
	 * 压缩JS文件。
	 * 
	 * @param oldFile 压缩之前的文件。
	 * @param newFile 压缩之后的新文件。
	 * @throws IOException 压缩过程发生异常。
	 */
	public static synchronized void compressJs(File oldFile, File newFile,
			boolean lineBreak) throws IOException {
		Reader in = new BufferedReader(new InputStreamReader(
				new FileInputStream(oldFile), "utf-8"));
		Writer out = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(newFile), "utf-8"));
		final String filename = oldFile.getName();

		try {
			JavaScriptCompressor compressor = new JavaScriptCompressor(in,
					new ErrorReporter() {
						public void warning(String message, String sourceName,
								int line, String lineSource, int lineOffset) {
							if (Var.printError) {
								System.err.println("WARNING in compress "
										+ filename);
								if (line < 0) {
									System.err.println("  " + message);
								} else {
									System.err.println("  " + line + ':'
											+ lineOffset + ':' + message);
								}
							}
						}

						public void error(String message, String sourceName,
								int line, String lineSource, int lineOffset) {
							if (Var.printError) {
								System.err.println("ERROR in compress "
										+ filename);
								if (line < 0) {
									System.err.println("  " + message);
								} else {
									System.err.println("  " + line + ':'
											+ lineOffset + ':' + message);
								}
							}
						}

						public EvaluatorException runtimeError(String message,
								String sourceName, int line, String lineSource,
								int lineOffset) {
							error(message, sourceName, line, lineSource,
									lineOffset);
							return new EvaluatorException(message);
						}
					});
			if (lineBreak)
				compressor.compress(out, null, 0, true, false, true, false);
			else
				compressor.compress(out, null, -1, true, false, false, false);
		} finally {
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(out);
		}
	}

	/**
	 * 压缩CSS文件。
	 * 
	 * @param oldFile
	 *            压缩之前的文件。
	 * @param newFile
	 *            压缩之后的新文件。
	 * @throws IOException
	 *             压缩过程发生异常。
	 */
	public static synchronized void compressCss(File oldFile, File newFile)
			throws IOException {
		Reader in = new BufferedReader(new InputStreamReader(
				new FileInputStream(oldFile), "utf-8"));
		Writer out = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(newFile), "utf-8"));

		try {
			CssCompressor compressor = new CssCompressor(in);
			compressor.compress(out, -1);
		} finally {
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(out);
		}
	}
}
