package com.iflytek.dep.server.monitor;

import com.iflytek.dep.server.ftp.FTPMonitor;
import com.iflytek.dep.server.ftp.core.FtpClientTemplate;
import com.iflytek.dep.server.ftp.listener.AckFtpListener;
import com.iflytek.dep.server.ftp.listener.PkgFtpListener;
import com.iflytek.dep.server.model.FTPConfig;
import com.iflytek.dep.server.utils.FileConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author yftao
 * @version V1.0
 * @Package com.iflytek.dep.server.monitor
 * @Description:
 * @date 2019/4/20--17:32
 */
public class MonitorStarter implements Runnable {

    private FTPConfig config;

    public MonitorStarter(FTPConfig config) {
        this.config = config;
    }
    private static final Logger logger = LoggerFactory.getLogger(MonitorStarter.class);


    @Override
    public void run() {

        logger.info("FTP 监听创建");

        // 获取当前ftp
        String nodeId = config.getNodeId();

        //----------------------------------------ACK监听-------------------------
        // 创建ack监听目录
        String ackDir = config.getAckPackageFolderDown();

        // ack监听启动
        FTPMonitor moniorAck = new FTPMonitor(ackDir,
                FileConfigUtil.FTP_POLLING_INTERVAL, FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(nodeId));
        moniorAck.execute( new AckFtpListener() );

        //----------------------------------------pkg监听-------------------------
        // 创建pkg监听目录
        String pkgDir = config.getDataPackageFolderDown();

        FTPMonitor moniorPkg = new FTPMonitor(pkgDir,
                FileConfigUtil.FTP_POLLING_INTERVAL, FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(nodeId));
        // pkg监听启动
        moniorPkg.execute( new PkgFtpListener() );

        logger.info("FTP 监听创建 end");

    }
}