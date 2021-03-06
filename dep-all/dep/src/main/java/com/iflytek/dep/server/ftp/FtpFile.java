package com.iflytek.dep.server.ftp;

import com.github.drapostolos.rdp4j.spi.FileElement;
import org.apache.commons.net.ftp.FTPFile;

import java.io.IOException;

public class FtpFile implements FileElement {

	private final FTPFile file;
    private final String name;
    private final boolean isDirectory;
    
    public FtpFile(FTPFile file) {
        this.file = file;
        this.name = file.getName();
        this.isDirectory = file.isDirectory();
    }
    
    @Override
    public long lastModified() throws IOException {
        return file.getTimestamp().getTimeInMillis();
    }
    
    @Override
    public boolean isDirectory() {
        return isDirectory;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return file.toString();
    }
}
