package com.iflytek.dep.server.ftp;

import com.github.drapostolos.rdp4j.*;
import com.github.drapostolos.rdp4j.spi.FileElement;
import com.iflytek.dep.common.utils.DateUtils;
import com.iflytek.dep.server.config.web.ApplicationContextRegister;
import com.iflytek.dep.server.constants.ActionType;
import com.iflytek.dep.server.constants.ExchangeNodeType;
import com.iflytek.dep.server.constants.PkgType;
import com.iflytek.dep.server.down.PkgGetterManger;
import com.iflytek.dep.server.ftp.core.FtpClientTemplate;
import com.iflytek.dep.server.section.FileDownloadLeafSection;
import com.iflytek.dep.server.section.FileDownloadMainSection;
import com.iflytek.dep.server.section.FileDownloadSection;
import com.iflytek.dep.server.section.SectionNodeBuilder;
import com.iflytek.dep.server.service.dataPack.SendAckService;
import com.iflytek.dep.server.service.dataPack.SendPackService;
import com.iflytek.dep.server.utils.FileConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentHashMap;


public class FtpListener implements DirectoryListener, IoErrorListener, InitialContentListener {

    private static Logger logger = LoggerFactory.getLogger(FtpListener.class);

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
        // 继承此类，在PkgFtpListener重写此方法
        logger.info( "Ftplistener ADD: " + event.getFileElement() );


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
