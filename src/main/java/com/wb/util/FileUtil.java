package com.wb.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.common.Base;
import com.wb.common.UrlBuffer;
import com.wb.common.Var;
import com.wb.common.XwlBuffer;

/**
 * 文件工具方法类。
 */
public class FileUtil {
	/**
	 * 获取指定文件名在同级目录下的唯一文件名称。
	 * 如果文件名称重复将通过在文件名称后添加序号的方法来获得唯一的名称。
	 * @param file 文件对象。
	 * @return 获得唯一文件名称的文件对象。
	 */
	public static File getUniqueFile(File file) {
		if (file.exists()) {
			File parent = file.getParentFile();
			String fullName = file.getName(), namePart = removeExtension(fullName);
			String extPart = getFileExt(fullName);
			boolean emptyExt = extPart.isEmpty();
			int i = 1;

			if (!emptyExt)
				extPart = '.' + extPart;
			do {
				if (emptyExt)
					file = new File(parent, StringUtil.concat(namePart, Integer
							.toString(i)));
				else
					file = new File(parent, StringUtil.concat(namePart, Integer
							.toString(i), extPart));
				i++;
			} while (file.exists());
		}
		return file;
	}

	/**
	 * 读取以utf-8编码的文本文件数据。
	 * @param file 读取的文件。
	 * @return 文件中存储的文本。
	 * @throws IOException 读取文本过程发生异常。
	 */
	public static String readString(File file) throws IOException {
		return FileUtils.readFileToString(file, "utf-8");
	}

	/**
	 * 把字符串以utf-8编码写入文件。
	 * @param file 写入的文件。
	 * @param content 写入的字符串。
	 * @throws IOException 写入文本过程发生异常。
	 */
	public static void writeString(File file, String content)
			throws IOException {
		FileUtils.writeStringToFile(file, content, "utf-8");
	}

	/**
	 * 保存输入流对象数据至文件。保存完成后将关闭输入流inputStream。
	 * @param inputStream 输入流对象。
	 * @param file 文件对象。
	 * @throws IOException 保存过程发生异常。
	 */
	public static void saveStream(InputStream inputStream, File file)
			throws IOException {
		FileOutputStream os = new FileOutputStream(file);
		try {
			IOUtils.copy(inputStream, os);
		} finally {
			IOUtils.closeQuietly(inputStream);
			IOUtils.closeQuietly(os);
		}
	}

	/**
	 * 获取指定文件名的扩展名，文件扩展名为文件名称中最后一个“.”号后的字符串。
	 * 如果文件名为空或没有扩展名则返回空字符串。
	 * @param fileName 文件名称。
	 * @return 文件扩展名。
	 */
	public static String getFileExt(String fileName) {
		if (fileName != null) {
			int i = fileName.lastIndexOf('.');
			if (i != -1)
				return fileName.substring(i + 1);
		}
		return "";
	}

	/**
	 * 获取文件或目录的类别显示名称。
	 * @param file 文件对象。
	 * @return 文件类别显示名称。
	 */
	public static String getFileType(File file) {
		String type;

		try {
			type = FileSystemView.getFileSystemView().getSystemTypeDescription(
					file);
		} catch (Throwable e) {
			type = null;
		}
		if (StringUtil.isEmpty(type))
			return getFileExt(file.getName());
		else
			return type;
	}

	/**
	 * 获取文件路径中的文件名称部分，文件名称包含文件扩展名。
	 * @param path 文件路径。
	 * @return 文件名称。
	 */
	public static String getFilename(String path) {
		if (StringUtil.isEmpty(path))
			return "";
		int p = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));

		if (p == -1)
			return path;
		else
			return path.substring(p + 1);
	}

	/**
	 * 移去文件名称中的扩展名称部分。
	 * @param fileName 文件路径或名称。
	 * @return 移去扩展名后的文件名称，不含路径。
	 */
	public static String removeExtension(String fileName) {
		String s = getFilename(fileName);
		int i = s.lastIndexOf('.');
		if (i != -1)
			return s.substring(0, i);
		else
			return s;
	}

	/**
	 * 把路径中所有的分隔符“\”转换成“/”。
	 * @param path 路径。
	 * @return 替换后的路径。
	 */
	public static String getPath(String path) {
		return StringUtil.replaceAll(path, "\\", "/");
	}

	/**
	 * 获取指定文件/目录的绝对路径，路径分隔符统一使用“/”来表示。
	 * @param file 文件或目录。
	 * @return 绝对路径。
	 */
	public static String getPath(File file) {
		return getPath(file.getAbsolutePath());
	}

	/**
	 * 获取下级文件/目录相对于上级文件/目录的相对路径。
	 * @param parent 上级文件/目录。
	 * @param child 下级文件/目录。
	 * @return 相对路径。如果parent和child不存在上下级关系则返回null。
	 * @throws IOException 获取相对路径发生异常。
	 * @throws NullPointerException 如果parent和child目录中的任何一个为空。
	 */
	public static String getRelativePath(File parent, File child) {
		if (parent == null)
			throw new NullPointerException("Parent file is null.");
		if (child == null)
			throw new NullPointerException("Child file is null.");
		String originChildPath = child.getAbsolutePath();
		String childPath = originChildPath + File.separatorChar;
		String parentPath = parent.getAbsolutePath() + File.separatorChar;

		if (childPath.equals(parentPath))
			return "";
		if (childPath.startsWith(parentPath))
			return getPath(originChildPath.substring(parentPath.length()));
		else
			return null;
	}

	/**
	 * 判断两个文件/目录是否存在上下级关系。
	 * @param parent 上级文件/目录。
	 * @param child 下级文件/目录。
	 * @return 如果parent等于child或者是child的上级目录则返回true，否则返回false。
	 * @throws IOException 读取文件/目录正式路径时发生异常。
	 */
	public static boolean isAncestor(File parent, File child)
			throws IOException {
		return isAncestor(parent, child, true);
	}

	/**
	 * 判断两个文件/目录是否存在上下级关系。
	 * @param parent 上级文件/目录。
	 * @param child 下级文件/目录。
	 * @param includeSelf 是否包括本身，如果true表示两个文件相等也属于上下级关系。
	 * @return parent和child存在上下级关系则返回true，否则返回false。
	 * @throws IOException 读取文件/目录正式路径时发生异常。
	 */
	public static boolean isAncestor(File parent, File child,
			boolean includeSelf) throws IOException {
		String parentPath = parent.getCanonicalPath();
		String childPath = child.getCanonicalPath();

		// 某些操作系统根目录路径带File.separatorChar
		if (!parentPath.endsWith(File.separatorChar + ""))
			parentPath += File.separatorChar;
		if (!childPath.endsWith(File.separatorChar + ""))
			childPath += File.separatorChar;
		return childPath.startsWith(parentPath)
				&& (includeSelf || childPath.length() > parentPath.length());
	}

	/**
	 * 判断指定目录是否是空目录。
	 * @param folder 目录对象。
	 * @return 如果目录为空或folder对象为null则返回true，否则返回false。
	 */
	public static boolean isEmpty(File folder) {
		String[] fs;
		return folder == null || (fs = folder.list()) == null || fs.length == 0;
	}

	/**
	 * 判断指定目录是否含有子目录。
	 * @param folder 目录对象。
	 * @return 如果目录含子目录返回true，否则返回false。
	 */
	public static boolean hasFolder(File folder) {
		File[] fs;
		if (folder != null && (fs = folder.listFiles()) != null
				&& fs.length > 0) {
			for (File file : fs) {
				if (file.isDirectory())
					return true;
			}
		}
		return false;
	}

	/**
	 * 获取指定目录下的所有子文件/目录列表。该方法功能同File.listFiles，
	 * 区别在于该方法使用空数组替代返回null。
	 * @param file 目录对象。
	 * @return 目录下的所有子文件/目录列表。
	 */
	public static File[] listFiles(File file) {
		if (!file.exists())
			throw new RuntimeException("\"" + file.getName()
					+ "\" does not exist.");
		File[] fs = file.listFiles();
		if (fs == null)
			return new File[0];
		else
			return fs;
	}

	/**
	 * 复制文件或目录至目标目录，并同步复制同步目录下的同名文件或目录。如果在同一目录内复制
	 *系统会自动进行重命名。同步目录由变量sys.syncPath指定。
	 * 
	 * @param src 复制的文件或目录。
	 * @param dst 复制到目标目录。
	 * @throws IOException 复制时发生异常。
	 * @return 复制后的文件或目录和目标是否存在组成的对象数组。
	 * @see FileUtil#getSyncPath
	 * @see FileUtil#syncCopyA
	 */
	public static Object[] syncCopy(File src, File dst) throws IOException {
		String name = src.getName();
		dst = new File(dst, name);
		File syncDst = getSyncPath(dst);
		boolean dstExists, isDir = src.isDirectory();
		boolean sameParent = src.getParentFile().equals(dst.getParentFile());

		// 同一目录内的复制自动重命名
		if (sameParent)
			dst = getUniqueFile(dst);
		dstExists = dst.exists();
		if (isDir)
			FileUtils.copyDirectory(src, dst);
		else
			FileUtils.copyFile(src, dst);
		if (syncDst != null) {
			if (sameParent)
				syncDst = getUniqueFile(syncDst);
			if (isDir)
				FileUtils.copyDirectory(src, syncDst);
			else
				FileUtils.copyFile(src, syncDst);
		}
		Object[] result = new Object[2];
		result[0] = getPath(dst);
		result[1] = dstExists;
		return result;
	}

	/**
	 * 复制文件或目录至目标文件或目录，并同步复制同步目录下的同名文件或目录。该方法按指定路径
	 *进行复制。该方法同syncCopy的区别为，前者严格按路径复制，后者相当于在文件管理器中执行复制
	 *操作。同步目录由变量sys.syncPath指定。
	 * 
	 * @param src 复制的文件或目录。
	 * @param dst 复制后的目标文件或目录。
	 * @throws IOException 复制时发生异常。
	 * @see FileUtil#getSyncPath
	 * @see FileUtil#syncCopy
	 */
	public static void syncCopyA(File src, File dst) throws IOException {
		boolean isDir = src.isDirectory();
		if (isDir)
			FileUtils.copyDirectory(src, dst);
		else
			FileUtils.copyFile(src, dst);
		File syncPath = FileUtil.getSyncPath(dst);
		if (syncPath != null) {
			if (isDir)
				FileUtils.copyDirectory(src, syncPath);
			else
				FileUtils.copyFile(src, syncPath);
		}
	}

	/**
	 * 创建文件或目录，并同步创建同步目录下的同名文件或目录。
	 *同步目录由变量sys.syncPath指定。
	 * 
	 * @param file 需要创建的文件或目录。
	 * @param isDir 是否创建目录。
	 * @throws IOException 无法创建时抛出异常。
	 * @see FileUtil#getSyncPath
	 */
	public static void syncCreate(File file, boolean isDir) throws IOException {
		String name = file.getName();
		File syncPath = getSyncPath(file);
		if (file.exists())
			throw new IllegalArgumentException("\"" + name
					+ "\" already exists.");
		if (syncPath != null && syncPath.exists())
			throw new IllegalArgumentException("\""
					+ syncPath.getAbsolutePath() + "\" already exists.");
		if (isDir) {
			if (!file.mkdir())
				throw new IOException("Create \"" + name + "\" failure.");
			if (syncPath != null && !syncPath.mkdir())
				throw new IOException("Create \"" + syncPath.getAbsolutePath()
						+ "\" failure.");
		} else {
			if (!file.createNewFile())
				throw new IOException("Create \"" + name + "\" failure.");
			if (syncPath != null && !syncPath.createNewFile())
				throw new IOException("Create \"" + syncPath.getAbsolutePath()
						+ "\" failure.");
		}
		if (syncPath != null)
			syncPath.setLastModified(file.lastModified());
	}

	/**
	 * 删除指定的文件或目录，并同步删除同步目录下的同名文件或目录。
	 *同步目录由变量sys.syncPath指定。
	 * 
	 * @param file 需要删除的文件或目录。
	 * @param clearUrl 是否清除URL捷径。
	 * @throws IOException 无法删除时抛出异常。
	 * @see FileUtil#getSyncPath
	 */
	public static void syncDelete(File file, boolean clearUrl) throws Exception {
		if (!FileUtils.deleteQuietly(file))
			throw new IOException("Cannot delete \"" + file.getName() + "\".");
		File syncPath = getSyncPath(file);
		if (syncPath != null && !FileUtils.deleteQuietly(syncPath))
			throw new IOException("Cannot delete \"" + syncPath.toString()
					+ "\".");
		clearFiles(file, clearUrl);
	}

	/**
	 * 移动文件或目录至目标目录，并同步移动同步目录下的同名文件或目录。
	 *同步目录由变量sys.syncPath指定。
	 * 
	 * @param src
	 *            移动的文件或目录。
	 * @param dst
	 *            移动到目标目录。
	 * @throws IOException
	 *             移动时发生异常。
	 * @see FileUtil#getSyncPath
	 */
	public static void syncMove(File src, File dst) throws Exception {
		File syncDst = getSyncPath(dst);

		FileUtils.moveToDirectory(src, dst, true);
		if (syncDst != null) {
			try {
				File syncSrc = getSyncPath(src);
				// 源目录无需同步时直接复制
				if (syncSrc == null) {
					if (syncDst.isDirectory())
						FileUtils.copyDirectory(dst, syncDst);
					else
						FileUtils.copyFile(dst, syncDst);
				} else
					FileUtils.moveToDirectory(syncSrc, syncDst, true);
			} catch (Throwable e) {
				// 忽略
			}
		}
		clearFiles(src, false);
	}

	/**
	 * 重命名文件或目录，并同步重命名同步目录下的同名文件或目录。
	 *同步目录由变量sys.syncPath指定。
	 * 
	 * @param file
	 *            需要重命名的文件或目录。
	 * @param newFile
	 *            命名为新的文件或目录。
	 * @throws IOException
	 *             不能重命名。
	 * @see FileUtil#getSyncPath
	 */
	public static void syncRename(File file, File newFile) throws IOException {
		File syncPath = getSyncPath(file);

		if (!file.renameTo(newFile))
			throw new IOException("Cannot rename \"" + file.getName() + "\".");
		if (syncPath != null && !syncPath.renameTo(getSyncPath(newFile)))
			throw new IOException("Cannot rename \"" + syncPath.toString()
					+ "\".");

	}

	/**
	 * 保存二进制数据至指定文件，并同步保存至另一目录。
	 *同步目录由变量sys.syncPath指定。
	 * 
	 * @param file
	 *            保存的文件对象。
	 * @param content
	 *            保存的二进制字节内容。
	 * @throws IOException
	 *             保存过存中发生异常。
	 * @see FileUtil#getSyncPath
	 */
	public static void syncSave(File file, byte[] content) throws Exception {
		FileUtils.writeByteArrayToFile(file, content);
		File syncPath = getSyncPath(file);
		if (syncPath != null)
			FileUtils.copyFile(file, syncPath);
	}

	/**
	 * 使用utf-8编码保存文本数据至指定文件，并同步保存至另一目录。
	 *同步保存的目录由变量sys.syncPath指定。
	 * 
	 * @param file 保存的文件对象。
	 * @param content 保存的文本内容。
	 * @throws IOException 保存过存中发生异常。
	 * @see FileUtil#getSyncPath
	 */
	public static void syncSave(File file, String content) throws Exception {
		syncSave(file, content, "utf-8", false);
	}

	/**
	 * 使用指定编码保存文本数据至指定文件，并同步保存至另一目录。
	 *同步保存的目录由变量sys.syncPath指定。
	 * @param file 保存的文件对象。
	 * @param content 保存的文本内容。
	 * @param charset 字符编码。
	 * @throws IOException 保存过存中发生异常。
	 * @see FileUtil#getSyncPath
	 */
	public static void syncSave(File file, String content, String charset)
			throws Exception {
		syncSave(file, content, charset, false);
	}

	/**
	 * 使用指定编码保存文本数据至指定文件，并同步保存至另一目录。
	 *同步保存的目录由变量sys.syncPath指定。
	 * @param file 保存的文件对象。
	 * @param content 保存的文本内容。
	 * @param charset 字符编码。
	 * @param keepLastModified 是否不更新文件最后更新时间。
	 * @throws IOException 保存过存中发生异常。
	 * @see FileUtil#getSyncPath
	 */
	public static void syncSave(File file, String content, String charset,
			boolean keepLastModified) throws Exception {
		// 无需加文件锁
		long lastModified = keepLastModified ? file.lastModified() : 0;
		if (StringUtil.isEmpty(charset))
			FileUtils.writeStringToFile(file, content);
		else
			FileUtils.writeStringToFile(file, content, charset);
		if (keepLastModified)
			file.setLastModified(lastModified);
		File syncPath = getSyncPath(file);
		if (syncPath != null) {
			FileUtils.copyFile(file, syncPath);
			if (keepLastModified)
				syncPath.setLastModified(lastModified);
		}
		String relPath = FileUtil.getModulePath(file);
		if (relPath != null)
			XwlBuffer.clear(relPath);
	}

	/**
	 * 获取位于同步目录下的指定文件或目录。路径为同步目录+指定文件/目录相对于应用目录的相对路径。
	 * 
	 * @param path 获取同步文件/目录时的参考文件/目录。
	 * @return 位于同步目录下的指定文件，如果不存在返回null。
	 * @throws IOException 获取过程发生异常。
	 */
	public static File getSyncPath(File path) throws IOException {
		if (!Var.syncPath.isEmpty() && isAncestor(Base.path, path)) {
			File base = new File(Var.syncPath);
			return new File(base, FileUtil.getPath(path)
					.substring(Base.pathLen));
		}
		return null;
	}

	/**
	 * 清除指定文件/目录相关的资源。 
	 * @param file 需要清除的文件或目录。
	 * @param clearUrl 是否清除URL捷径。
	 * @throws IOException 读写文件发生异常。
	 */
	private static void clearFiles(File file, boolean clearUrl)
			throws Exception {
		// 清除索引信息
		File folder = file.getParentFile();
		File configFile = new File(folder, "folder.json");
		if (configFile.exists()) {
			JSONObject object = JsonUtil.readObject(configFile);
			JSONArray index = object.optJSONArray("index");
			if (index != null) {
				int i, j = index.length();
				File indexFile;

				for (i = j - 1; i >= 0; i--) {
					indexFile = new File(folder, index.getString(i));
					if (!indexFile.exists())
						index.remove(i);
				}
				syncSave(configFile, object.toString());
			}
		}

		// 清除模块缓存数据
		String relPath = FileUtil.getModulePath(file);
		if (relPath != null) {
			XwlBuffer.clear(relPath);
			if (clearUrl) {
				if (UrlBuffer.remove(relPath))
					UrlBuffer.save();
			}
		}
	}

	/**
	 * 获得模块文件的相对路径。如果指定文件不是模块目录的子文件返回null。
	 * @param file 模块文件。
	 * @return 模块文件相对模块目录相对路径。
	 */
	public static String getModulePath(File file) {
		String path = FileUtil.getPath(file);
		if (path.startsWith(Base.modulePathText))
			return path.substring(Base.modulePathLen);
		else
			return null;
	}

	/**
	 * 获取url中的模块文件路径，url可为合法的xwl引用或其捷径。如果未找到文件抛出异常。
	 * @param url url路径。
	 * @return xwl模块文件相对路径。
	 */
	public static String getModuleFile(String url) {
		return getModuleFile(url, false);
	}

	/**
	 * 获取url中的模块文件路径，url可为合法的xwl引用或其捷径。
	 * @param url url路径。
	 * @param silent 如果未找到，true返回null，false抛出异常。
	 * @return xwl模块文件相对路径。
	 */
	public static String getModuleFile(String url, boolean silent) {
		if (url == null) {
			if (silent)
				return null;
			throw new NullPointerException(
					"The requested url is not specified.");
		}
		if (url.startsWith("m?xwl="))
			return url.substring(6) + ".xwl";
		else if (url.endsWith(".xwl"))
			return url;
		else {
			// 捷径
			String shortcut = url;
			url = UrlBuffer.get("/" + shortcut);
			if (url == null) {
				if (silent)
					return null;
				throw new NullPointerException("The requested url shortcut \""
						+ shortcut + "\" is not found.");
			}
			return url;
		}
	}
}