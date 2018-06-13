package com.wb.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.wb.controls.Control;
import com.wb.controls.ExtControl;
import com.wb.controls.ScriptControl;
import com.wb.interact.Controls;
import com.wb.util.DbUtil;
import com.wb.util.FileUtil;
import com.wb.util.LogUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;
import com.wb.util.WebUtil;

/**
 * 模块解析器，解析并执行模块文件。解析完成后，系统把生成的客户端脚本发送到客户端。
 */
public class Parser {
	/** html头文本 */
	private StringBuilder headerHtml = new StringBuilder();
	/** html脚文本 */
	private ArrayList<String> footerHtml = new ArrayList<String>(15);
	/** html脚文本指针 */
	private int htmlPointer;
	/** js头文本 */
	private StringBuilder headerScript = new StringBuilder();
	/** js脚文本 */
	private ArrayList<String> footerScript = new ArrayList<String>(15);
	/** js脚文本指针 */
	private int scriptPointer;
	/** 是否是普通运行模式 */
	private HttpServletRequest request;
	/** HttpServletResponse响应对象 */
	private HttpServletResponse response;
	/** 普通运行模式 */
	private static final int RUN_NORMAL = 0;
	/** 模块引用运行模式 */
	private static final int RUN_MODULE = 1;
	/** 控件引用运行模式 */
	private static final int RUN_CONTROL = 2;
	/** 外部调用模式  */
	private static final int RUN_INVOKE = 3;

	/**
	 * XWL文件解析器构造函数。
	 * 
	 * @param req HttpServletRequest 请求对象。
	 * @param resp HttpServletRequest 响应对象。
	 */
	public Parser(HttpServletRequest request, HttpServletResponse response) {
		this.request = request;
		this.response = response;
	}

	/**
	 * 解析xwl模块。依次遍历模块内所有节点，执行控件的create方法。
	 *执行完成后系统自动关闭和释放资源。
	 * @param moduleFile xwl文件相对路径。
	 * @see Parser#execute(String, int)
	 */
	public void parse(String moduleFile) throws ServletException {
		boolean hasExcept = false;
		List<FileItem> fileItemList = null;
		ConcurrentHashMap<String, Object> varMap = null;

		try {
			varMap = new ConcurrentHashMap<String, Object>();
			request.setAttribute("sys.varMap", varMap);
			if (ServletFileUpload.isMultipartContent(request))
				fileItemList = WebUtil.setUploadFile(request);
			execute(moduleFile,
					request.getParameter("xwlt") == null ? RUN_NORMAL
							: RUN_INVOKE, null);
		} catch (Throwable e) {
			hasExcept = true;
			WebUtil.showException(e, request, response);
		} finally {
			closeObjects(varMap, hasExcept);
			if (fileItemList != null)
				WebUtil.clearUploadFile(request, fileItemList);
		}
	}

	/**
	 * 解析和执行xwl文件。依次遍历模块内所有节点，执行对应控件的create方法。
	 * @param moduleFile xwl文件相对路径。
	 * @param moduleRunType 运行模式。
	 * RUN_NORMAL：普通运行模式，完成全部流程，如果有前端脚本不返回入口。
	 * RUN_MODULE：不验证权限不关闭资源，如果有前端脚本不返回入口。
	 * RUN_CONTROL：不验证权限不关闭资源，如果有前端脚本返回app主入口。
	 * RUN_INVOKE：完成全部流程，如果有前端脚本返回整个app对象。
	 * @param xwlId	模块itemId名称，用于在命名空间中子空间名称指定。
	 * @throws Exception 如果解析过程中发生异常将抛出。
	 */
	private void execute(String moduleFile, int runType, String xwlId)
			throws Exception {
		JSONObject fullModule = XwlBuffer.get(moduleFile, false);
		JSONObject module = (JSONObject) ((JSONArray) fullModule
				.opt("children")).opt(0);
		JSONObject configs = (JSONObject) module.opt("configs");
		boolean runNormal = runType == RUN_NORMAL, runInvoke = runType == RUN_INVOKE;
		if (runNormal || runInvoke) {
			// 检查头信息是否一致
			String method = getString(configs, "method"), tokens = getString(
					configs, "tokens");
			if (!method.isEmpty()
					&& !method.equalsIgnoreCase(request.getMethod())) {
				throw new IllegalArgumentException("Method not allowed");
			}
			if (tokens.isEmpty() || !checkToken(tokens)) {
				// 验证登录和权限
				if (Boolean.TRUE.equals(fullModule.opt("loginRequired"))) {
					if (!WebUtil.checkLogin(request, response))
						return;// 未登录在checkLogin方法中返回SC_UNAUTHORIZED
					// 未具备权限抛出异常
					if (!XwlBuffer.canAccess(fullModule, Session
							.getRoles(request)))
						throw new Exception(Str.format(request, "forbidden",
								StringUtil.select(
										fullModule.optString("title"), FileUtil
												.getFilename(moduleFile)),
								moduleFile));
				}
			}
		}
		JSONObject events = (JSONObject) module.opt("events"), emptyJson = new JSONObject();
		JSONObject moduleGeneral = (JSONObject) Controls.get("module").opt(
				"general");
		String namespace, theme = null, touchTheme = null, content;
		boolean createFrame, libTypes[] = null;
		boolean hasChildren = module.has("children"), hasEvents = events != null;
		HttpSession session = null;

		content = getString(configs, "logMessage");
		if (!content.isEmpty())
			LogUtil.info(request, content);
		// initScript在importModule之前运行，serverScript在importModule之后运行
		content = getString(configs, "initScript");
		if (!content.isEmpty())
			ScriptBuffer.run(StringUtil.concat((String) configs.opt("id"),
					".is"), content, request, response, moduleFile);
		content = getString(configs, "serverMethod");
		if (!content.isEmpty())
			SysUtil.executeMethod(content, request, response);
		createFrame = getBool(configs, "createFrame", true);
		if (createFrame && runNormal) {
			String title, tagConfigs;
			headerHtml
					.append("<!DOCTYPE html>\n<html>\n<head>\n<meta http-equiv=\"content-type\" content=\"text/html;charset=utf-8\"/>\n<meta name=\"description\" content=\"Welcome to www.putdb.com, we provide excellent solutions.\"/>\n<title>");
			title = getString(configs, "title");
			if (title.isEmpty()) {
				title = fullModule.optString("title");// 默认页面标题为模块的标题
				if (title.startsWith("Str."))
					title = Str.format(request, title.substring(4));
			} else if (title.equals("-"))
				title = null; // "-"表示标题为空
			if (!StringUtil.isEmpty(title))
				headerHtml.append(title);
			headerHtml.append("</title>");
			appendScript(headerHtml, getString(configs, "head"));
			session = request.getSession(false);
			theme = session == null ? null : (String) session
					.getAttribute("sys.theme");
			if (theme == null)
				theme = Var.getString("sys.app.theme");
			touchTheme = session == null ? null : (String) session
					.getAttribute("sys.touchTheme");
			if (touchTheme == null)
				touchTheme = Var.getString("sys.app.touchTheme");
			libTypes = setLinks(configs, theme, touchTheme);
			tagConfigs = getString(configs, "tagConfigs");
			if (tagConfigs.isEmpty())
				headerHtml.append("\n</head>\n<body>");
			else {
				headerHtml.append("\n</head>\n<body ");
				headerHtml.append(tagConfigs);
				headerHtml.append('>');
			}
			headerScript
					.append("<script language=\"javascript\" type=\"text/javascript\">");
		}
		appendScript(headerHtml, getString(configs, "initHtml"));
		if (createFrame) {
			if (headerScript.length() > 0)
				headerScript.append('\n');
			if (runNormal && libTypes[1]) {
				// 加载了Ext
				headerScript
						.append("Ext.onReady(function(contextOptions,contextOwner){");
			} else if (runNormal && libTypes[2]) {
				// 加载了touch
				headerScript.append("Ext.setup({");
				if (hasChildren)
					headerScript.append(getTouchViewport((JSONArray) module
							.opt("children"), moduleGeneral, runNormal));
				headerScript
						.append("onReady:function(contextOptions,contextOwner){");
			} else
				headerScript.append("(function(contextOptions,contextOwner){");
			namespace = (String) configs.opt("itemId");
			// 如果模块itemId未改名则创建内部命名空间。
			if (namespace.equals("module")) {
				headerScript.append("\nvar app={};");
			} else {
				// 创建命名空间
				headerScript.append("\nWb.ns(\"");
				headerScript.append(namespace);
				headerScript.append("\");\nvar app=");
				headerScript.append(namespace);
				headerScript.append(";");
			}
			if (runNormal && libTypes[2]) {
				// 设置viewport appscope和引用
				headerScript
						.append("\nthis.appScope=app;\napp[this.itemId]=this;");
			}
			headerScript.append("\napp.contextOwner=contextOwner;");
			if (runNormal) {
				// 设置常用变量
				headerScript.append("\nwindow.app=app;\nWb.init({zo:");
				if (Var.useLocalTime) {
					Calendar cal = Calendar.getInstance();
					headerScript.append((cal.get(Calendar.ZONE_OFFSET) + cal
							.get(Calendar.DST_OFFSET)) / 60000);
				} else
					headerScript.append("-1");
				if (Var.maskTimeout != 1500) {
					headerScript.append(",mask:");
					headerScript.append(Var.maskTimeout);
				}
				if (Var.ajaxTimeout != 0) {
					headerScript.append(",timeout:");
					headerScript.append(Var.ajaxTimeout);
				}
				if (!"gray".equals(theme)) {
					headerScript.append(",theme:\"");
					headerScript.append(theme);
					headerScript.append('\"');
				}
				if (!"classic".equals(touchTheme)) {
					headerScript.append(",touchTheme:\"");
					headerScript.append(touchTheme);
					headerScript.append('\"');
				}
				theme = session == null ? null : (String) session
						.getAttribute("sys.editTheme");
				if (theme == null)
					theme = Var.getString("sys.ide.editTheme");
				if (!"default".equals(theme)) {
					headerScript.append(",editTheme:\"");
					headerScript.append(theme);
					headerScript.append('\"');
				}
				headerScript.append("});");
			} else if (runType == RUN_CONTROL || runType == RUN_MODULE) {
				// 添加xwl控件有xwlId，导入方法无xwlId
				if (xwlId != null) {
					headerScript.append("\ncontextOwner[");
					headerScript.append(StringUtil.quote(xwlId));
					headerScript.append("]=app;");
				}
			}
		}
		// 导入模块，在解析子控件之前执行
		content = getString(configs, "importModules");
		if (!content.isEmpty())
			importModules(content);
		// serverScript在导入模块之后运行，在开始运行可使用initScript
		content = getString(configs, "serverScript");
		if (!content.isEmpty())
			ScriptBuffer.run(StringUtil.concat((String) configs.opt("id"),
					".ss"), content, request, response, moduleFile);
		if (hasEvents) {
			String beforeunload = getString(events, "beforeunload");
			if (!beforeunload.isEmpty())
				appendScript(headerScript, StringUtil.concat(
						"Wb.onUnload(function(){\n", beforeunload,
						"\n},contextOwner);"));
			appendScript(headerScript, getString(events, "initialize"));
		}
		if (hasChildren)
			scan(module, moduleGeneral, emptyJson, runNormal);
		// 如果已经提交，则脚本无需再输出。
		if (response.isCommitted())
			return;
		appendScript(headerHtml, getString(configs, "finalHtml"));
		if (hasEvents)
			appendScript(headerScript, getString(events, "finalize"));
		if (createFrame) {
			if (runNormal) {
				if (libTypes[1]) // Ext
					headerScript.append("\n});");
				else if (libTypes[2])// touch
					headerScript.append("\n}});");
				else
					headerScript.append("\n})();");
			} else if (runType == RUN_CONTROL) {
				headerScript.append("\nreturn Wb.optMain(app);\n})(null,app)");
			} else if (runType == RUN_MODULE)
				headerScript.append("\n})(null,app);");
			else
				// RUN_INVOKE
				headerScript.append("\nreturn app;\n})();");// 不可带参数，在引用时自动传入
		}
		if (runNormal) {
			if (createFrame)
				headerScript.append("\n</script>\n</body>\n</html>");
			output();
		} else if (runInvoke)
			output();
	}

	/**
	 * 检查当前请求url中带的_token参数是否有效。
	 * @param tokens 模块设置的token列表。
	 * @return true有效，false无效。
	 */
	private boolean checkToken(String tokens) {
		String token = request.getParameter("_token");
		if (StringUtil.isEmpty(token))
			return false;
		String[] ls = StringUtil.split(tokens, ",");
		for (String s : ls) {
			if (token.equals(s.trim()))
				return true;
		}
		return false;
	}

	/**
	 * 立即把脚本输出至客户端。
	 * @throws IOException 
	 */
	public void output() throws IOException {
		if (headerHtml.length() > 0 && headerScript.length() > 0)
			headerHtml.append('\n');
		// 把headerScript合并到headerHtml中并输出
		headerHtml.append(headerScript);
		if (headerHtml.length() > 0) {
			if (WebUtil.jsonResponse(request))
				WebUtil.send(response, headerHtml.toString(), true);
			else
				WebUtil.send(response, headerHtml);
		}
	}

	/**
	 * 按顺序递归模块中所有节点内容并执行，如果节点生成客户端脚本将获取这些脚本。
	 * 
	 * @param parentNode 当前节点对象。
	 * @param parentGeneral 父级点元数据常规配置信息。
	 * @param emptyJson 空JSON对象，用于指定父控件空元数据。
	 * @param normalType 是否为普通运行模式。
	 */
	private void scan(JSONObject parentNode, JSONObject parentGeneral,
			JSONObject emptyJson, boolean normalType) throws Exception {
		JSONArray ja = (JSONArray) parentNode.opt("children");
		Control control;
		JSONObject jo, meta, general, configItems;
		String className, type, lastScript;
		boolean isScriptControl, rootParent;
		int i, j = ja.length(), k = j - 1, quoteIndex;

		for (i = 0; i < j; i++) {
			jo = (JSONObject) ja.opt(i);
			type = (String) jo.opt("type");
			meta = Controls.get(type);
			general = (JSONObject) meta.opt("general");
			className = (String) general.opt("class");
			if (className == null) {
				// 未指定类名
				control = new ExtControl();
				isScriptControl = true;
			} else if (className.equals("null")) {
				// 类名指定为null
				control = null;
				isScriptControl = false;
				if (type.equals("xwl")) {
					rootParent = Boolean.TRUE.equals(parentGeneral.opt("root"));
					addModule(jo, rootParent);
					if (!rootParent && i < j - 1)
						headerScript.append(',');
				}
			} else {
				if (className.indexOf('.') == -1)
					className = "com.wb.controls." + className;
				control = (Control) Class.forName(className).newInstance();
				isScriptControl = control instanceof ScriptControl;
			}
			if (control != null) {
				control.init(request, response, jo, meta, parentGeneral,
						i == k, normalType);
				control.create();
			}
 			if (isScriptControl) {
				ScriptControl sc = (ScriptControl) control;
				appendScript(headerHtml, sc.getHeaderHtml());
				pushHtml(sc.getFooterHtml());
				appendScript(headerScript, sc.getHeaderScript());
				pushScript(sc.getFooterScript());
			}
			// 配置的子项作为控件
			if (jo.has("children"))
				scan(jo, general, emptyJson, normalType);
			if (isScriptControl) {
				appendScript(headerHtml, popHtml());
				lastScript = popScript();
				quoteIndex = lastScript.lastIndexOf('}');
				if (quoteIndex != -1
						&& (configItems = (JSONObject) jo.opt("__configs")) != null) {
					// 注入在XwlBuffer.optimize中自动生成的子项作为配置项
					appendScript(headerScript, lastScript.substring(0,
							quoteIndex));
					headerScript.append(',');
					scan((JSONObject) configItems, emptyJson, emptyJson,
							normalType);
					appendScript(headerScript, lastScript.substring(quoteIndex));
				} else
					appendScript(headerScript, lastScript);
			}
		}
	}

	/**
	 * 解析xwl模块控件。
	 * @param jo 控件数据对象。
	 * @param rootParent 父控件是否为根控件。
	 */
	private void addModule(JSONObject jo, boolean rootParent) throws Exception {
		JSONObject configs = (JSONObject) jo.opt("configs");
		String file = (String) configs.opt("file");

		if (file != null)
			execute(FileUtil.getModuleFile(file),
					rootParent ? Parser.RUN_MODULE : Parser.RUN_CONTROL,
					(String) configs.opt("itemId"));
	}

	/**
	 * 把指定footerHtml脚本添加到堆栈中。此方法较Stack类更高效。
	 * @param script 添加的footerHtml脚本。
	 */
	private void pushHtml(String script) {
		htmlPointer++;
		if (footerHtml.size() < htmlPointer)
			footerHtml.add(script);
		else
			footerHtml.set(htmlPointer - 1, script);
	}

	/**
	 * 提取堆栈中最后一项footerHtml脚本。
	 * @return footerHtml脚本。
	 */
	private String popHtml() {
		htmlPointer--;
		return footerHtml.get(htmlPointer);
	}

	/**
	 * 把指定footerScript脚本添加到堆栈中。此方法较Stack类更高效。
	 * @param script 添加的footerScript脚本。
	 */
	private void pushScript(String script) {
		scriptPointer++;
		if (footerScript.size() < scriptPointer)
			footerScript.add(script);
		else
			footerScript.set(scriptPointer - 1, script);
	}

	/**
	 * 提取堆栈中最后一项footerScript脚本。
	 * @return footerScript脚本。
	 */
	private String popScript() {
		scriptPointer--;
		return footerScript.get(scriptPointer);
	}

	/**
	 * 添加脚本至指定StringBuilder对象。如果添加之前已经存在脚本，则换行后添加脚本。
	 * 
	 * @param buf 需要被添加脚本的StringBuilder对象。
	 * @param script 添加的脚本。
	 */
	private void appendScript(StringBuilder buf, String script) {
		if (!StringUtil.isEmpty(script)) {
			if (buf.length() > 0)
				buf.append('\n');
			buf.append(script);
		}
	}

	/**
	 * 导入在importModules属性中指定的子模块列表。
	 * @param modules 导入的模块列表。
	 * @throws Exception 导入过程发生异常。
	 */
	private void importModules(String modules) throws Exception {
		JSONArray moduleArray = new JSONArray(modules);
		int i, j = moduleArray.length();
		for (i = 0; i < j; i++)
			execute(FileUtil.getModuleFile((String) moduleArray.opt(i)),
					Parser.RUN_MODULE, null);
	}

	/**
	 * 设置模块的js和css链接。
	 * @param configs 模块的配置项。
	 * @param theme 界面方案名称。
	 * @return 加载的库列表。0 未知， 1 Ext, 2 Touch, 3 BS。
	 */
	private boolean[] setLinks(JSONObject configs, String theme,
			String touchTheme) {
		ArrayList<String> cssArray = new ArrayList<String>(), jsArray = new ArrayList<String>();
		JSONArray cssLinks = null, jsLinks = null;
		String debugSuffix, value, cssLinksText, jsLinksText;
		String loadJS = getString(configs, "loadJS");
		String lang = (String) request.getAttribute("sys.useLang");
		int i, j, index;
		boolean libTypes[] = new boolean[4];

		if (Var.debug)
			debugSuffix = "-debug";
		else
			debugSuffix = "";
		request.setAttribute("debugSuffix", debugSuffix);
		cssLinksText = getString(configs, "cssLinks");
		jsLinksText = getString(configs, "jsLinks");
		if (!cssLinksText.isEmpty())
			cssLinks = new JSONArray(cssLinksText);
		if (!jsLinksText.isEmpty())
			jsLinks = new JSONArray(jsLinksText);
		if (loadJS.isEmpty())
			loadJS = "ext";
		jsArray.add(StringUtil.concat("wb/script/locale/wb-lang-", Str
				.optLanguage(lang), debugSuffix, ".js"));
		if (loadJS.indexOf("ext") != -1) {
			libTypes[1] = true;
			cssArray.add(StringUtil.concat("wb/libs/ext/resources/ext-theme-",
					theme, "/ext-theme-", theme, "-all", debugSuffix, ".css"));
			jsArray.add(StringUtil.concat("wb/libs/ext/ext-all", debugSuffix,
					".js"));
			jsArray.add(StringUtil.concat("wb/libs/ext/locale/ext-lang-", Str
					.optExtLanguage(lang), debugSuffix, ".js"));
		}
		if (loadJS.indexOf("touch") != -1) {
			libTypes[2] = true;
			cssArray.add(StringUtil.concat("wb/libs/touch/resources/css/",
					touchTheme, debugSuffix, ".css"));
			jsArray.add(StringUtil.concat("wb/libs/touch/locale/t-lang-", Str
					.optTouchLanguage(lang), debugSuffix, ".js"));
			jsArray.add(StringUtil.concat("wb/libs/touch/sencha-touch-all",
					debugSuffix, ".js"));
		}
		if (loadJS.indexOf("bootstrap") != -1) {
			libTypes[3] = true;
			cssArray.add(StringUtil.concat("wb/libs/bs/css/bootstrap",
					debugSuffix, ".css"));
			jsArray.add(StringUtil.concat("wb/libs/jquery/jquery", debugSuffix,
					".js"));
			jsArray.add(StringUtil.concat("wb/libs/bs/js/bootstrap",
					debugSuffix, ".js"));
		}
		if (loadJS.indexOf("jquery") != -1)
			jsArray.add("wb/libs/jquery/jquery" + debugSuffix + ".js");
		cssArray.add(StringUtil.concat("wb/css/style", debugSuffix, ".css"));
		jsArray.add(StringUtil.concat("wb/script/wb", debugSuffix, ".js"));
		if (cssLinks != null) {
			j = cssLinks.length();
			for (i = 0; i < j; i++) {
				value = cssLinks.getString(i);
				index = cssArray.indexOf(value);
				// 允许重新设置css加载顺序
				if (index != -1)
					cssArray.remove(index);
				cssArray.add(value);
			}
		}
		if (jsLinks != null) {
			j = jsLinks.length();
			for (i = 0; i < j; i++) {
				value = jsLinks.getString(i);
				index = jsArray.indexOf(value);
				// 允许重新设置js加载顺序
				if (index != -1)
					jsArray.remove(index);
				jsArray.add(value);
			}
		}
		for (String css : cssArray) {
			headerHtml
					.append("\n<link type=\"text/css\" rel=\"stylesheet\" href=\"");
			headerHtml.append(css);
			headerHtml.append("\">");
		}
		for (String js : jsArray) {
			headerHtml.append("\n<script type=\"text/javascript\" src=\"");
			headerHtml.append(js);
			headerHtml.append("\"></script>");
		}
		return libTypes;
	}

	/**
	 * 获取对象中指定名称的替换参数后的字符串值。
	 * @param object JSNObject对象。
	 * @param name 名称。
	 * @return 获取的值。如果值为空返回空字符串。
	 */
	private String getString(JSONObject object, String name) {
		String value = (String) object.opt(name);
		if (value == null)
			return "";
		else
			return WebUtil.replaceParams(request, value);
	}

	/**
	 * 获取对象中指定名称的替换参数后的布尔值。
	 * @param object JSNObject对象。
	 * @param name 名称。
	 * @param defaultValue 默认值。
	 * @return 获取的值。如果值为空返回默认值。
	 */
	private boolean getBool(JSONObject object, String name, boolean defaultValue) {
		String value = getString(object, name);
		if (value.isEmpty())
			return defaultValue;
		else
			return Boolean.parseBoolean(value);
	}

	/**
	 * 关闭存储在map中的对象。
	 * @param map 存储的map对象。
	 * @param isExcept 是否有异常，如果存在未提交事务且有异常将回滚事务否则提交事务。
	 */
	private void closeObjects(ConcurrentHashMap<String, Object> map,
			boolean isExcept) {
		Object object;
		Set<Entry<String, Object>> es = map.entrySet();
		ArrayList<Connection> connList = new ArrayList<Connection>();
		ArrayList<Statement> stList = new ArrayList<Statement>();

		for (Entry<String, Object> e : es) {
			object = e.getValue();
			if (object != null) {
				if (object instanceof ResultSet)
					DbUtil.close((ResultSet) object);
				else if (object instanceof Statement)
					stList.add((Statement) object);
				else if (object instanceof Connection)
					connList.add((Connection) object);
				else if (object instanceof InputStream)
					IOUtils.closeQuietly((InputStream) object);
				else if (object instanceof OutputStream)
					IOUtils.closeQuietly((OutputStream) object);
			}
		}
		for (Statement st : stList)
			DbUtil.close(st);
		for (Connection conn : connList) {
			if (isExcept)
				DbUtil.close(conn);
			else
				DbUtil.closeCommit(conn);
		}
	}

	/**
	 * 获取touch模式viewport控件的脚本。
	 * @param items 模块根控件列表。
	 * @param parentGeneral 模块控件general属性。
	 * @param normalType 是否是普通运行模式。
	 * @return viewport控件脚本，如果不存在viewport控件返回空串。
	 */
	private String getTouchViewport(JSONArray items, JSONObject parentGeneral,
			boolean normalType) throws Exception {
		if (items == null)
			return "";
		JSONObject meta = Controls.get("tviewport");
		StringBuilder script = new StringBuilder();
		ExtControl control;
		int i, j = items.length(), k = j - 1;
		JSONObject jo;

		for (i = 0; i < j; i++) {
			jo = ((JSONObject) items.opt(i));
			if ("tviewport".equals(jo.opt("type"))) {
				control = new ExtControl();
				control.normalMode = false;
				control.init(request, response, jo, meta, parentGeneral,
						i == k, normalType);
				control.create();
				script.append("\nviewport:");
				script.append(control.getHeaderScript());
				script.append(control.getFooterScript());
				script.append(',');
				return script.toString();
			}
		}
		return "";
	}
}
