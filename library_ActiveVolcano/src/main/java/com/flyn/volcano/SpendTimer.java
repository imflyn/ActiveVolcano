package com.flyn.volcano;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import android.os.SystemClock;

public class SpendTimer
{
    private final static int DEFAULT_TIMER_DURATION = 1500;
    private final static int DEFAULT_START_TIMER_DELAY = 200;

    private final TimerListener timerListener;
    private int bytesTotal = 0;
    private int bytesWritten = 0;

    private ScheduledExecutorService scheduler;
    private volatile boolean isScheduleing = true;
    private long timeStamp = 0;
    private long sizeStamp = 0;
    private int currentSpeed = 0;

    protected SpendTimer(int bytesTotal, TimerListener timerListener)
    {
        if (timerListener == null)
            throw new IllegalStateException("TimerListener can not be null.");

        if (bytesTotal < 0)
            this.bytesTotal = 0;
        else
            this.bytesTotal = bytesTotal;

        this.timerListener = timerListener;

    }

    protected void updateProgress(int count)
    {
        this.bytesWritten += count;
    }

    protected void start()
    {
        stop();
        synchronized (this)
        {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory()
            {
                @Override
                public Thread newThread(Runnable r)
                {
                    Thread thread = new Thread(r);
                    thread.setName("Volcano ComputeSpendTime ScheduleThread.");
                    return thread;
                }
            });

            final Runnable task = new Runnable()
            {
                private boolean first = true;

                @Override
                public void run()
                {
                    if (isScheduleing && !Thread.currentThread().isInterrupted())
                    {
                        long nowTime = SystemClock.uptimeMillis();

                        long spendTime = nowTime - timeStamp;
                        timeStamp = nowTime;

                        long getSize = bytesWritten - sizeStamp;
                        sizeStamp = bytesWritten;
                        if (spendTime > 0)
                            currentSpeed = (int) ((getSize / spendTime) / 1.024);

                        if (!first)
                            timerListener.onProgress(bytesWritten, bytesTotal, currentSpeed);
                        else
                            first = false;

                    } else
                    {
                        stop();
                    }
                }
            };

            this.scheduler.scheduleWithFixedDelay(task, DEFAULT_START_TIMER_DELAY, DEFAULT_TIMER_DURATION, TimeUnit.MICROSECONDS);
        }
    }

    protected synchronized void stop()
    {
        this.isScheduleing = false;
        if (this.scheduler != null)
        {
            this.scheduler.shutdown();
            this.scheduler = null;
        }
    }

    interface TimerListener
    {
        void onProgress(int bytesWritten, int bytesTotal, int speed);

    }
}
