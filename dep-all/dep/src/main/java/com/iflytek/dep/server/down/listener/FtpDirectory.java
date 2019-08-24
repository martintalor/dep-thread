package com.iflytek.dep.server.down.listener;

import com.github.drapostolos.rdp4j.spi.FileElement;
import com.github.drapostolos.rdp4j.spi.PolledDirectory;
import com.iflytek.dep.server.ftp.core.FtpClientTemplate;

import java.io.IOException;
import java.util.Set;

/**
 * 对被监控文件类型以ftp形式进行封装
 * @author Kevin
 *
 */
public class FtpDirectory implements PolledDirectory {
    
	private String workingDirectory;

    private FtpClientTemplate ftpClientTemplate;

    public FtpDirectory(String dir, FtpClientTemplate ftpClientTemplate) {
    	this.workingDirectory = dir;
    	this.ftpClientTemplate = ftpClientTemplate;
    }

    public Set<FileElement> listFiles() throws IOException {
        return ftpClientTemplate.listFiles(workingDirectory);
    }
}
