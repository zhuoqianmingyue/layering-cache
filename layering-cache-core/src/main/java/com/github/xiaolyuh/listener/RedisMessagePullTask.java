package com.github.xiaolyuh.listener;

import com.github.xiaolyuh.manager.AbstractCacheManager;
import com.github.xiaolyuh.util.BeanFactory;
import com.github.xiaolyuh.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * redis消息拉模式
 *
 * @author yuhao.wang
 */
public class RedisMessagePullTask {
    private static final Logger log = LoggerFactory.getLogger(RedisMessagePullTask.class);

    /**
     * 定时任务线程池
     */
    private static final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(3, new NamedThreadFactory("layering-cache-pull-message"));

    /**
     * redis消息处理器
     */
    private RedisMessageService redisMessageService;

    public void init(AbstractCacheManager cacheManager) {
        redisMessageService = BeanFactory.getBean(RedisMessageService.class).init(cacheManager);

        // 1. 服务启动同步最新的偏移量
        BeanFactory.getBean(RedisMessageService.class).syncOffset();
        // 2. 启动PULL TASK
        startPullTask();
        // 3. 启动重置本地偏消息移量任务
        resetOffsetTask();
        // 4. 重连检测
        reconnectionTask();
    }

    /**
     * 启动PULL TASK
     */
    private void startPullTask() {
        executor.scheduleWithFixedDelay(() -> {
            try {
                redisMessageService.pullMessage();
            } catch (Exception e) {
                e.printStackTrace();
                log.error("layering-cache PULL 方式清楚一级缓存异常：{}", e.getMessage(), e);
            }
            //  初始时间间隔是30秒
        }, 5, 30, TimeUnit.SECONDS);
    }

    /**
     * 启动重置本地偏消息移量任务
     */
    private void resetOffsetTask() {

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 3);
        cal.set(Calendar.MINUTE, 0);
        long initialDelay = System.currentTimeMillis() - cal.getTimeInMillis();
        initialDelay = initialDelay > 0 ? initialDelay : 0;
        // 每天晚上凌晨3:00执行任务
        executor.scheduleWithFixedDelay(() -> {
            try {
                redisMessageService.resetOffset();
            } catch (Exception e) {
                e.printStackTrace();
                log.error("layering-cache 重置本地消息偏移量异常：{}", e.getMessage(), e);
            }
        }, initialDelay, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);

    }

    /**
     * 启动重连pub/sub检查
     */
    private void reconnectionTask() {
        executor.scheduleWithFixedDelay(() -> redisMessageService.reconnection(),
                5, 5, TimeUnit.SECONDS);
    }

}
