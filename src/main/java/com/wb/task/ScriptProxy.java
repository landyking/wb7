package com.wb.task;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.wb.common.ScriptBuffer;
import com.wb.common.Var;
import com.wb.util.DateUtil;
import com.wb.util.LogUtil;
import com.wb.util.StringUtil;
import com.wb.util.SysUtil;

/**
 * 用于执行服务器端脚本任务的代理。
 */
public class ScriptProxy implements Job {
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		long start = System.currentTimeMillis();
		String jobDesc = context.getJobDetail().getDescription();

		try {
			if (Var.taskLog)
				LogUtil.info("Start job " + jobDesc);
			JobDataMap dataMap = context.getJobDetail().getJobDataMap();
			String serverScript = dataMap.getString("job.serverScript");
			if (!StringUtil.isEmpty(serverScript))
				ScriptBuffer.run(dataMap.getString("job.id"), serverScript,
						context);
			if (Var.taskLog)
				LogUtil.info(StringUtil.concat("Finish job ", jobDesc, " in ",
						DateUtil.format(System.currentTimeMillis() - start)));
		} catch (Throwable e) {
			if (Var.taskLog)
				LogUtil.error(StringUtil.concat("Execute job ", jobDesc,
						" failed with error ", SysUtil.getRootError(e), " in ",
						DateUtil.format(System.currentTimeMillis() - start)));
			if (Var.printError)
				throw new RuntimeException(e);
		}
	}
}