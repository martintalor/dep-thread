package com.iflytek.dep.server.ftp.listener;

import com.github.drapostolos.rdp4j.*;
import com.github.drapostolos.rdp4j.spi.FileElement;
import com.iflytek.dep.server.config.web.ApplicationContextRegister;
import com.iflytek.dep.server.constants.ExchangeNodeType;
import com.iflytek.dep.server.down.PkgGetterManger;
import com.iflytek.dep.server.ftp.FtpDirectory;
import com.iflytek.dep.server.ftp.FtpListener;
import com.iflytek.dep.server.ftp.core.FtpClientTemplate;
import com.iflytek.dep.server.service.dataPack.SendPackService;
import com.iflytek.dep.server.utils.FileConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;


public class PkgFtpListener extends FtpListener {

    private static Logger logger = LoggerFactory.getLogger(PkgFtpListener.class);

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
        logger.info( Thread.currentThread().getName()+ " fileAdded PkgFtpListener INIT fileName="  +  event.getFileElement().getName() );

        FileElement element = event.getFileElement();
//
        // 传递参数
        ConcurrentHashMap<String,Object> paramMap = new  ConcurrentHashMap<String,Object>();

        String fileName = element.getName();

        //  不监控包含tmp的文件
        if ( fileName.toUpperCase().contains("TMP") ) {
            logger.debug( "contains tmp="  +fileName );
            return;
        }

        if (!fileName.startsWith("PKG")) {
            return;
        }

        FtpDirectory directory = (FtpDirectory) event.getPolledDirectory();
        FtpClientTemplate ftpClientTemplate = directory.getFtpClientTemplate();
        String curNodeId = ftpClientTemplate.getFtpClientConfig().getNodeId();

        paramMap.put("PACKAGE_ID",fileName);// 数据包名
        paramMap.put("NODE_ID",curNodeId);// 当前FTP节点

        logger.info( Thread.currentThread().getName() + " fileAdded PKG START fileName="  + fileName + " ,FTP_IP:" + ftpClientTemplate.getFtpClientConfig().getHost() + "，NODE_ID:" + curNodeId  );

		PkgGetterManger pkgGetterManger = ApplicationContextRegister.getApplicationContext().getBean(PkgGetterManger.class);

        // 监控pkg包、下载、生成ack包

        // 中心节点下载
        if (FileConfigUtil.ISCENTER) {

            pkgGetterManger.downLoadPackage(ExchangeNodeType.MAIN,fileName,null,null,paramMap);
        } else {

            pkgGetterManger.downLoadPackage(ExchangeNodeType.LEAF,fileName,null,null,paramMap);

        }

        logger.info( Thread.currentThread().getName() + " fileAdded PKG SUCCESS fileName="  + fileName + ", FTP_IP:" + ftpClientTemplate.getFtpClientConfig().getHost() + "，NODE_ID:" + curNodeId );

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
