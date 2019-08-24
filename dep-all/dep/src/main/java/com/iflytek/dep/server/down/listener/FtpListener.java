package com.iflytek.dep.server.down.listener;

import com.github.drapostolos.rdp4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 注册文件变动监听
 * @author Kevin
 *
 */
public class FtpListener implements DirectoryListener, IoErrorListener, InitialContentListener {
	private static final Logger log = LoggerFactory.getLogger(FtpListener.class);
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
		log.info("Added: " + event.getFileElement());
		
	}

	@Override
	public void fileModified(FileModifiedEvent event) throws InterruptedException {
		log.info("Modified: " + event.getFileElement());
		
	}

	@Override
	public void fileRemoved(FileRemovedEvent arg0) throws InterruptedException {
		// TODO Auto-generated method stub
		
	}

}
