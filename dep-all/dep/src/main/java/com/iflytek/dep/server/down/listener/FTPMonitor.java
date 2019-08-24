package com.iflytek.dep.server.down.listener;

import com.github.drapostolos.rdp4j.DirectoryPoller;
import com.github.drapostolos.rdp4j.spi.PolledDirectory;
import com.iflytek.dep.server.ftp.core.FtpClientTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 设置定时监控任务
 * @author Kevin
 *
 */
public class FTPMonitor {
	
	private String ftpDir;
	private int pollingInterval = 10;
	private FtpClientTemplate ftpClientTemplate;
	
	public FTPMonitor(String ftpDir, int pollingInterval, FtpClientTemplate ftpClientTemplate) {
		this.ftpDir = ftpDir;
		this.pollingInterval = pollingInterval;
		this.ftpClientTemplate = ftpClientTemplate;
	}
	
	public void execute() {
		PolledDirectory polledDirectory = new FtpDirectory(this.ftpDir, this.ftpClientTemplate);
		DirectoryPoller.newBuilder()
                .addPolledDirectory(polledDirectory)
                .addListener(new FtpListener())
                .enableFileAddedEventsForInitialContent()
                .setPollingInterval(this.pollingInterval, TimeUnit.SECONDS)
                .start();
	}

}
