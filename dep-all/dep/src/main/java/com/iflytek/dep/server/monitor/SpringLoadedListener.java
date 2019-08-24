package com.iflytek.dep.server.monitor;


import com.iflytek.dep.server.ftp.core.FtpClientTemplate;
import com.iflytek.dep.server.model.FTPConfig;
import com.iflytek.dep.server.service.dataPack.FTPService;
import com.iflytek.dep.server.utils.FileConfigUtil;
import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 初始化参考
 *
 * @author Kevin
 */
@Component
@DependsOn("com.iflytek.dep.server.config.web.ApplicationContextRegister")
public class SpringLoadedListener implements ApplicationListener<ContextRefreshedEvent> {

	private static final Logger logger = LoggerFactory.getLogger(SpringLoadedListener.class);

	@Autowired
	private FTPService ftpService;

//	@Override
//	public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
//		logger.info("Monitor ftp dir starting...");
//		List<FTPConfig> configs = ftpService.selectByServerNodeId(FileConfigUtil.SERVER_NODE_ID);
//		try {
//			if (!CollectionUtils.isEmpty(configs)) {
//				for (FTPConfig config : configs) {
//					String nodeId = config.getNodeId();
//					// 缓存ftp配置信息
//					FtpClientTemplate.FTP_CONFIG.put(config.getNodeId(), config);
//					// 缓存FtpClientTemplate
//					if (!FtpClientTemplate.FTP_CLIENT_TEMPLATE.containsKey(nodeId)) {
//						//
//						FtpClientTemplate.FTP_CLIENT_TEMPLATE.put(nodeId, new FtpClientTemplate(nodeId));
//					}
//
//					logger.info(config.toString());
//					logger.info(FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(nodeId).getFtpClientConfig().toString());
//
//					// 初始化文件夹
//					logger.info( "init mkdir:" + nodeId );
//					initFtpDir( FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(nodeId), config );
//					logger.info( "init mkdir end:" + nodeId );
//				}
//			}
//			startMonitor(configs);
//		} catch (Exception e) {
//			logger.error(e.getMessage(), e);
//			throw e;
//		}
//
//	}

	private void startMonitor(List<FTPConfig> configs) {
		for(FTPConfig config: configs) {
			MonitorStarter starter = new MonitorStarter(config);
			Thread t = new Thread(starter);
			t.start();
		}
		logger.info("Monitor ftp dir done..."+ configs.size());
	}


	/**
	 *@描述 初始化文件夹
	 *@参数  [ftpClientTemplate, config]
	 *@返回值  void
	 *@创建人  姚伟-weiyao2
	 *@创建时间  2019/4/22
	 *@修改人和其它信息
	 */
	private void initFtpDir(FtpClientTemplate ftpClientTemplate, FTPConfig config ) {

		// 创建FTP
		FTPClient ftpClient = ftpClientTemplate.getFtpClient();

		// 创建ack上传目录
		String ackDir = config.getAckPackageFolderDown();
		ftpClientTemplate.makeDirectory( ftpClient ,ackDir);

		// 创建ack上传目录
		String ackUpDir = config.getAckPackageFolderUp();
		ftpClientTemplate.makeDirectory( ftpClient, ackUpDir);

		// 创建pkg下载目录
		String pkgDir = config.getDataPackageFolderDown();
		ftpClientTemplate.makeDirectory( ftpClient, pkgDir);

		// 创建pkg上传目录
		String upPkgDir = config.getDataPackageFolderUp();
		ftpClientTemplate.makeDirectory( ftpClient, upPkgDir);

		// 创建tmp目录
		String tmpDir = config.getTmpPackageFolder();
		ftpClientTemplate.makeDirectory( ftpClient, tmpDir);

		// 销毁FTP
		ftpClientTemplate.destroyFtp(ftpClient);
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {

		logger.info("Monitor ftp dir starting...");
		List<FTPConfig> configs = ftpService.selectByServerNodeId(FileConfigUtil.SERVER_NODE_ID);
		try {
			if (!CollectionUtils.isEmpty(configs)) {
				for (FTPConfig config : configs) {
					String nodeId = config.getNodeId();
					// 缓存ftp配置信息
					FtpClientTemplate.FTP_CONFIG.put(config.getNodeId(), config);
					// 缓存FtpClientTemplate
					if (!FtpClientTemplate.FTP_CLIENT_TEMPLATE.containsKey(nodeId)) {
						//
						FtpClientTemplate.FTP_CLIENT_TEMPLATE.put(nodeId, new FtpClientTemplate(nodeId));
					}

					logger.info(config.toString());
					logger.info(FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(nodeId).getFtpClientConfig().toString());

					// 初始化文件夹
					logger.info( "init mkdir:" + nodeId );
					initFtpDir( FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(nodeId), config );
					logger.info( "init mkdir end:" + nodeId );
				}
			}
			startMonitor(configs);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}

	}
}
