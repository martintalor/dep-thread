package com.iflytek.dep.server.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author 姚伟-weiyao2
 * @version V1.0
 * @Package com.iflytek.dep.server.utils
 * @Description:
 * @date 2019/3/8--13:47
 */
public class FileUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);


    /**
     * @描述 创建文件
     * @参数 [filePath]
     * @返回值 java.lang.Boolean 返回成功失败
     * @创建人 姚伟-weiyao2
     * @创建时间 2019/3/18
     * @修改人和其它信息
     */
    public static Boolean touchFile(String filePath) {

        Boolean result = false;
        File ackFile = new File(filePath);
        if (!ackFile.exists()) {
            try {
                result = ackFile.createNewFile();
                logger.info("文件创建成功！FILE:" + ackFile);
            } catch (IOException e) {
                logger.error("文件创建失败！FILE:" + ackFile);
            }
        } else {
            result = true;
            logger.info("文件已存在！FILE:" + ackFile);
        }
        return result;
    }

    /**
     * @描述 创建文件夹, 全路径
     * @参数 [dirPath] 传入文件夹路径
     * @返回值 java.io.File
     * @创建人 姚伟-weiyao2
     * @创建时间 2019/3/15
     * @修改人和其它信息
     */
    public static boolean mkdirs(String dirPaths) {
        File ackDir = new File(dirPaths);
        Boolean result = false;
        if (!ackDir.exists()) {
            result = ackDir.mkdirs();
//            logger.info("文件夹创建成功！FILE:" + ackDir);
        } else {
            result = true;
//            logger.info("文件夹已存在！FILE:" + ackDir);
        }

        return result;
    }

    /**
     * 删除文件夹（包含其中文件） add by jzkan 20190513
     * @param folderPath
     * @return
     */
    public static void delFolder(String folderPath) {
        try {
            delAllFile(folderPath); //删除完里面所有内容
            String filePath = folderPath;
            File myFilePath = new File(filePath);
            if (myFilePath.exists()) {
                myFilePath.delete(); //删除空文件夹
            }
        } catch (Exception e) {
            logger.error("删除文件夹失败！",e);
        }
    }

    public static boolean delAllFile(String path) {
        boolean flag = false;
        File file = new File(path);
        if (!file.exists()) {
            return flag;
        }
        if (!file.isDirectory()) {
            return flag;
        }
        String[] tempList = file.list();
        File temp = null;
        for (int i = 0; i < tempList.length; i++) {
            if (path.endsWith(File.separator)) {
                temp = new File(path + tempList[i]);
            } else {
                temp = new File(path + File.separator + tempList[i]);
            }
            if (temp.isFile()) {
                temp.delete();
            }
            if (temp.isDirectory()) {
                delAllFile(path + "/" + tempList[i]);//先删除文件夹里面的文件
                delFolder(path + "/" + tempList[i]);//再删除空文件夹
                flag = true;
            }
        }
        return flag;
    }

    public static boolean mkdir(String dirPath) {
        File ackDir = new File(dirPath);
        Boolean result = false;
        if (!ackDir.exists()) {
            result = ackDir.mkdir();
            logger.info("文件夹创建成功！FILE:" + ackDir);
        } else {
            logger.info("文件夹已存在！FILE:" + ackDir);
        }

        return result;
    }

    public static void selFileList(String filePath, List fileList) {

        File file = new File(filePath);
        File[] tempList = file.listFiles();
        System.out.println("该目录下对象个数：" + tempList.length);
        for (int i = 0; i < tempList.length; i++) {
            if (tempList[i].isFile()) {
                System.out.println("文     件：" + tempList[i]);
                fileList.add(tempList[i].getAbsolutePath());
            }
            if (tempList[i].isDirectory()) {
                System.out.println("文件夹：" + tempList[i]);
                selFileList(tempList[i].getAbsolutePath(), fileList);
            }
        }
    }

    public static String getTodayString() {
        try {
            Date now = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
            String todayString = dateFormat.format(now);
            return todayString;
        } catch (Exception ex) {
            logger.error("get today happend error", ex);
        }
        return null;
    }

    /**
     *@描述  copyZip
     *@参数  [oldPath, newPath]
     *@返回值  boolean
     *@创建人  朱一帆
     *@创建时间  2019/4/16
     *@修改人和其它信息
     */
    public static boolean copyZip(String oldPath, String newPath) {
        try {
            FileInputStream in = new FileInputStream(oldPath);
            //FileOutputStream中的文件不存在，将自动新建文件
            OutputStream out = new FileOutputStream(newPath);

            byte[] buff = new byte[1024];
            long beginTime = System.currentTimeMillis();
            int b;
            while ((b = in.read(buff)) != -1) {
                out.write(buff, 0, b);
            }
            long endTime = System.currentTimeMillis();
            System.out.println("运行时长为: " + (endTime - beginTime) + "毫秒");
            out.flush();
            in.close();
            out.close();
            System.out.println("正常运行！");
            return true;
        } catch (IOException e) {
            logger.error("复制文件出错！！",e);
            e.printStackTrace();
            return false;
        }
    }

}
