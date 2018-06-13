package com.wb.tool;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.Date;
import java.util.Properties;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerUtils;
import org.quartz.impl.StdSchedulerFactory;

import com.wb.common.ScriptBuffer;
import com.wb.common.Var;
import com.wb.task.ScriptProxy;
import com.wb.util.DbUtil;
import com.wb.util.StringUtil;

public class TaskManager {
	public static Scheduler scheduler;

	/**
	 * 把指定参数的任务加载到任务引擎中。如果相同id的任务已经存在，则重新加载该任务。
	 * @param taskId 任务id
	 * @param taskName 任务名称
	 * @param intervalType 周期类型
	 * @param intervalExpress 周期表达式
	 * @param className 执行的Java类
	 * @param serverScript 执行的服务器端脚本
	 * @param beginDate 开始时间
	 * @param endDate 结束时间s
	 * @throws Exception 加载任务发生异常
	 */
	public static void loadTask(String taskId, String taskName,
			int intervalType, String intervalExpress, String className,
			String serverScript, Date beginDate, Date endDate) throws Exception {
		JobDetail job;
		String express[];
		Trigger trigger = null;

		// 如果指定id的任务已经存在，删除后重新加载
		deleteTask(taskId);
		if (StringUtil.isEmpty(className)) {
			job = new JobDetail(taskId, Scheduler.DEFAULT_GROUP,
					ScriptProxy.class);
			JobDataMap dataMap = job.getJobDataMap();
			dataMap.put("job.id", "job." + taskId);
			dataMap.put("job.serverScript", serverScript);
		} else
			job = new JobDetail(taskId, Scheduler.DEFAULT_GROUP, Class
					.forName(className));
		job.setDescription(taskName);
		express = StringUtil.split(intervalExpress, ":");
		switch (intervalType) {
		case 0:
			trigger = TriggerUtils.makeSecondlyTrigger(Integer
					.parseInt(express[0]));
			break;
		case 1:
			trigger = TriggerUtils.makeMinutelyTrigger(Integer
					.parseInt(express[0]));
			break;
		case 2:
			trigger = TriggerUtils.makeHourlyTrigger(Integer
					.parseInt(express[0]));
			break;
		case 3:
			trigger = TriggerUtils.makeDailyTrigger(Integer
					.parseInt(express[0]), Integer.parseInt(express[1]));
			break;
		case 4:
			trigger = TriggerUtils.makeWeeklyTrigger(Integer
					.parseInt(express[0]), Integer.parseInt(express[1]),
					Integer.parseInt(express[2]));
			break;
		case 5:
			trigger = TriggerUtils.makeMonthlyTrigger(Integer
					.parseInt(express[0]), Integer.parseInt(express[1]),
					Integer.parseInt(express[2]));
			break;
		}
		trigger.setName(taskId);
		if (beginDate != null)
			trigger.setStartTime(beginDate);
		if (endDate != null)
			trigger.setEndTime(endDate);
		if (scheduler != null)
			scheduler.scheduleJob(job, trigger);
	}

	/**
	 * 删除指定id的任务，如果任务不存在，则该方法无任何效果。
	 * @param taskId 任务id
	 */
	public static void deleteTask(String taskId) throws Exception {
		if (scheduler != null)
			if (scheduler.getJobDetail(taskId, Scheduler.DEFAULT_GROUP) != null)
				scheduler.deleteJob(taskId, Scheduler.DEFAULT_GROUP);
		ScriptBuffer.remove("job." + taskId);
	}

	/**
	 * 开始计划任务引擎，加载所有已经配置的计划任务。如果任务已经加载，则重新加载该任务。
	 * @throws Exception
	 */
	public static synchronized void start() throws Exception {
		if (!Var.getBool("sys.task.enabled"))
			return;
		if (scheduler == null) {
			StdSchedulerFactory factory = new StdSchedulerFactory();
			Properties props = new Properties();
			props.put(StdSchedulerFactory.PROP_THREAD_POOL_CLASS,
					"org.quartz.simpl.SimpleThreadPool");
			props.put("org.quartz.threadPool.threadCount", Var
					.getString("sys.task.threadCount"));
			factory.initialize(props);
			scheduler = factory.getScheduler();
			scheduler.start();
		} else if (scheduler.isStarted())
			return;
		Connection conn = null;
		Statement st = null;
		ResultSet rs = null;

		try {
			conn = DbUtil.getConnection();
			st = conn.createStatement();
			rs = st.executeQuery("select * from WB_TASK");
			while (rs.next()) {
				// 停止的任务
				if (rs.getInt("STATUS") == 0)
					continue;
				loadTask(rs.getString("TASK_ID"), rs.getString("TASK_NAME"), rs
						.getInt("INTERVAL_TYPE"), rs
						.getString("INTERVAL_EXPRESS"), rs
						.getString("CLASS_NAME"), (String) DbUtil.getObject(rs,
						"SERVER_SCRIPT", Types.NCLOB), rs
						.getTimestamp("BEGIN_DATE"), rs
						.getTimestamp("END_DATE"));
			}
		} finally {
			DbUtil.close(rs);
			DbUtil.close(st);
			DbUtil.close(conn);
		}
	}

	/**
	 * 停止计划任务引擎。
	 * @throws Exception
	 */
	public static synchronized void stop() throws Exception {
		if (!Var.getBool("sys.task.enabled"))
			return;
		if (scheduler == null || scheduler.isShutdown())
			return;
		scheduler.shutdown();
		scheduler = null;
		Thread.sleep(Var.getInt("sys.task.stopDelay"));
	}
}
