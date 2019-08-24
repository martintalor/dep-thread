package com.iflytek.dep.server.ftp.listener;

import com.github.drapostolos.rdp4j.*;
import com.github.drapostolos.rdp4j.spi.FileElement;
import com.iflytek.dep.server.config.web.ApplicationContextRegister;
import com.iflytek.dep.server.constants.ExchangeNodeType;
import com.iflytek.dep.server.down.PkgGetterManger;
import com.iflytek.dep.server.ftp.FtpDirectory;
import com.iflytek.dep.server.ftp.FtpListener;
import com.iflytek.dep.server.ftp.core.FtpClientTemplate;
import com.iflytek.dep.server.section.FileDownloadLeafSection;
import com.iflytek.dep.server.section.FileDownloadMainSection;
import com.iflytek.dep.server.service.dataPack.SendAckService;
import com.iflytek.dep.server.service.dataPack.SendPackService;
import com.iflytek.dep.server.utils.FileConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;


public class AckFtpListener extends FtpListener {

    private static Logger logger = LoggerFactory.getLogger(AckFtpListener.class);

    @Override
	public void initialContent(InitialContentEvent arg0) throws InterruptedException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void ioErrorCeased(IoErrorCeasedEvent arg0) throws InterruptedException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void ioErrorRaised(IoErrorRaisedEvent arg0) throws InterruptedException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fileAdded(FileAddedEvent event) throws InterruptedException {
//		System.out.println(DateUtils.sysdate() + " Added: " + event.getFileElement());
        logger.info( Thread.currentThread().getName()+ " fileAdded AckFtpListener INIT fileName="  +  event.getFileElement().getName() );

        FileElement element = event.getFileElement();
//
        SendAckService sendAckService = (SendAckService)ApplicationContextRegister.getApplicationContext().getBean(SendAckService.class);

        // 传递参数
        ConcurrentHashMap<String,Object> paramMap = new  ConcurrentHashMap<String,Object>();

        String fileName = element.getName();

        //  不监控后缀tmp的文件
        if ( fileName.toUpperCase().endsWith("TMP") ) {
            logger.debug( "endsWith tmp="  +fileName );
            return;
        }

        //  不监控包含tmp的文件
        if ( fileName.toUpperCase().contains("TMP") ) {
            logger.debug( "contains tmp="  +fileName );
            return;
        }

        if (!fileName.startsWith("ACK")) {
            return;
        }

         FtpDirectory directory = (FtpDirectory) event.getPolledDirectory();
        FtpClientTemplate ftpClientTemplate = directory.getFtpClientTemplate();
        String curNodeId = ftpClientTemplate.getFtpClientConfig().getNodeId();

        paramMap.put("PACKAGE_ID",fileName);// 数据包名
        paramMap.put("NODE_ID",curNodeId);// 当前FTP节点

        logger.info( Thread.currentThread().getName() + " fileAdded ACK START fileName="  + fileName + " ,FTP_IP:" + ftpClientTemplate.getFtpClientConfig().getHost() + "，NODE_ID:" + curNodeId  );

        // 监控ack包,返回节点状态
        sendAckService.parseAck(paramMap);
        logger.info( Thread.currentThread().getName() + " fileAdded ACK SUCCESS fileName="  + fileName + ", FTP_IP:" + ftpClientTemplate.getFtpClientConfig().getHost() + "，NODE_ID:" + curNodeId );
	}

	@Override
	public void fileModified(FileModifiedEvent event) throws InterruptedException {
		System.out.println("Modified: " + event.getFileElement());
		
	}

	@Override
	public void fileRemoved(FileRemovedEvent arg0) throws InterruptedException {
		// TODO Auto-generated method stub
		
	}


}
