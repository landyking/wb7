package com.wb.common;

import java.io.File;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.quartz.JobExecutionContext;

import com.wb.tool.Route;
import com.wb.util.FileUtil;
import com.wb.util.StringUtil;

/**
 * JavaScript脚本执行和缓存器。缓存首次访问并编译后的脚本对象，
 *便于下次对脚本访问时无需编译而直接运行。
 */

public class ScriptBuffer {
	/**
	 * JavaScript引擎管理器对象。
	 */
	private static ScriptEngineManager manager;
	/**
	 * JavaScript引擎对象。
	 */
	private static ScriptEngine engine;
	/**
	 * JavaScript可编译的引擎对象。
	 */
	private static Compilable compilable;
	/**
	 * 缓存编译后的JavaScript脚本的HashMap。
	 */
	private static ConcurrentHashMap<String, CompiledScript> buffer;

	/**
	 * 在Web请求上下文中运行指定编号的脚本对象。首次运行脚本将被编译并缓存编译后的实例。
	 * 
	 * @param id 脚本编号
	 * @param scriptText 脚本内容
	 * @param request Web上下文HttpServletRequest对象参数
	 * @param response Web上下文HttpServletResponse对象参数
	 * @param sourceURL 调试脚本时标识代码的文件路径
	 * @throws Exception 执行脚本发生错误
	 */
	public static void run(String id, String scriptText,
			HttpServletRequest request, HttpServletResponse response,
			String sourceURL) throws Exception {
		CompiledScript script = buffer.get(id);

		if (script == null) {
			// Wb.getApp仅返回JSON对象不影响性能
			if (StringUtil.isEmpty(sourceURL))
				script = compilable.compile(StringUtil.concat(
						"(function(){var app=Wb.getApp(request,response);",
						scriptText, "\n})();"));
			else
				script = compilable.compile(StringUtil.concat(
						"(function(){var app=Wb.getApp(request,response);",
						scriptText, "\n})();\n//# sourceURL=", sourceURL));
			buffer.put(id, script);
		}
		Bindings bindings = engine.createBindings();
		bindings.put("request", request);
		bindings.put("response", response);
		script.eval(bindings);
	}

	/**
	 * 运行服务器端脚本。运行后的脚本不会进行缓存。
	 * @param scriptText 脚本内容
	 * @throws Exception 执行脚本发生错误
	 */
	public static void run(String scriptText) throws Exception {
		CompiledScript script = compilable.compile(StringUtil.concat(
				"(function(){", scriptText, "\n})();"));
		script.eval();
	}

	/**
	 * 在计划任务中运行指定编号的脚本对象。首次运行脚本将被编译并缓存编译后的实例。
	 * 
	 * @param taskId 计划任务编号
	 * @param scriptText 脚本内容
	 * @param jobContext 任务上下文对象
	 * @throws Exception 执行脚本发生错误
	 */
	public static void run(String taskId, String scriptText,
			JobExecutionContext jobContext) throws Exception {
		CompiledScript script = buffer.get(taskId);

		if (script == null) {
			script = compilable.compile(StringUtil.concat("(function(){",
					scriptText, "\n})();"));
			buffer.put(taskId, script);
		}
		Bindings bindings = engine.createBindings();
		bindings.put("jobContext", jobContext);
		script.eval(bindings);
	}

	/**
	 * 在流程中运行指定编号的脚本对象。首次运行脚本将被编译并缓存编译后的实例。
	 * 
	 * @param scriptId 脚本编号
	 * @param scriptText 脚本内容
	 * @param route 路由对象
	 * @throws Exception 执行脚本发生错误
	 */
	public static Object run(String scriptId, String scriptText, Route route)
			throws Exception {
		CompiledScript script = buffer.get(scriptId);
		if (script == null) {
			script = compilable.compile(StringUtil.concat("(function(){",
					scriptText, "\n})();"));
			buffer.put(scriptId, script);
		}
		Bindings bindings = engine.createBindings();
		bindings.put("route", route);
		return script.eval(bindings);
	}

	/**
	 * 加载和初始化。
	 */
	public static synchronized void load() {
		try {
			manager = new ScriptEngineManager();
			engine = manager.getEngineByName("javascript");
			compilable = (Compilable) engine;
			buffer = new ConcurrentHashMap<String, CompiledScript>();
			loadUtils();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 删除缓存中指定编码前缀的所有脚本实例对象。
	 * @param id 脚本id编号的前缀
	 */
	public static void remove(String id) {
		Set<Entry<String, CompiledScript>> es = buffer.entrySet();
		String k;

		for (Entry<String, CompiledScript> e : es) {
			k = e.getKey();
			if (k.startsWith(id))
				buffer.remove(k);
		}
	}

	/**
	 * 加载全局工具类方法。
	 * @throws Exception 加载过程发生异常。
	 */
	private static void loadUtils() throws Exception {
		CompiledScript script;
		String text = FileUtil.readString(new File(Base.path,
				"wb/system/server.js"));
		text += "\n//# sourceURL=server.js";
		script = compilable.compile(text);
		Bindings bindings = engine.createBindings();
		script.eval(bindings);
		Set<Entry<String, Object>> es = bindings.entrySet();
		for (Entry<String, Object> e : es)
			manager.put(e.getKey(), e.getValue());
	}
}