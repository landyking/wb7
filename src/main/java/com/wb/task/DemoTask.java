package com.wb.task;

import java.util.Date;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.wb.util.DateUtil;

/**
 * 在系统控制台打印指定字符串任务。
 */
public class DemoTask implements Job {
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		System.out
				.println(DateUtil.format(new Date(), "hh:mm:ss") + ": 示例任务 3");
	}
}