package com.iflytek.dep.server.section;

import com.google.gson.Gson;
import com.iflytek.dep.server.ftp.core.FtpClientTemplate;
import com.iflytek.dep.server.mapper.FTPConfigMapper;
import com.iflytek.dep.server.mapper.NodeAppBeanMapper;
import com.iflytek.dep.server.mapper.SectionStepRecordersMapper;
import com.iflytek.dep.server.model.FTPConfig;
import com.iflytek.dep.server.service.threadPool.DepServerFtpFileBackService;
import com.iflytek.dep.server.utils.CommonConstants;
import com.iflytek.dep.server.utils.FileUtil;
import com.iflytek.dep.server.utils.SectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 朱一帆
 * @version V1.0
 * @Package com.iflytek.dep.server.section
 * @Description:
 * @date 2019/5/23--8:45
 */
@Service
@Scope("prototype")
public class FileMoveBatchSection extends BaseSectionThreadPool implements Section {
    private static Logger logger = LoggerFactory.getLogger(FileMoveSection.class);
    private static ExecutorService fixedUpStreamThreadPool;
    public static AtomicInteger threadJobSize = new AtomicInteger(0);
    @Autowired
    private Environment environment;
    @Autowired
    FTPConfigMapper fTPConfigMapper;

    @Autowired
    DepServerFtpFileBackService depServerFtpFileBackService;

    @Autowired
    SectionStepRecordersMapper sectionStepRecordersMapper;

    @Autowired
    NodeAppBeanMapper nodeAppBeanMapper;

    @Autowired
    RestTemplate restTemplate;
    @Autowired
    SectionUtils sectionUtils;
    public FileMoveBatchSection() {
        synchronized (logger){
            if(fixedUpStreamThreadPool == null){
                fixedUpStreamThreadPool =  Executors.newFixedThreadPool(threadNumber,
                        new FileMoveSection.DepThreadFactory("FileMoveSection"));
            }
        }
    }

    @Override
    public SectionResult doThreadAct(final String packageId, String jobId, SectionNode sectionNode, BigDecimal totalSectionNumber, ConcurrentHashMap<String, Object> map) throws Exception {
        logger.info("[{}] job has [{}],package id [{}] in method ,jobId is [{}]",this.getClass(),threadJobSize.incrementAndGet(),packageId,jobId);
        fixedUpStreamThreadPool.submit(new Runnable() {

            public void run() {
                try {
                    logger.info("[{}] , package id [{}] running",this.getClass(),packageId);
                    sectionUtils.insertSectionStepRecorders(map, new BigDecimal(0), this.getClass() + "", jobId, totalSectionNumber,new BigDecimal(0));

                    SectionResult result = new SectionResult(true);
                    result.setMap(map);

                    String downFtpNodeId = (String) map.get("DOWN_FTP_NODE_ID");

                    FTPConfig fTPConfig = fTPConfigMapper.selectByNodeId(downFtpNodeId);

//			String curNodeId = (String) map.get("CUR_NODE_ID");
                    List<ConcurrentHashMap<String, Object>> param = (List<ConcurrentHashMap<String, Object>>) map.get("PARAM");
                    FtpClientTemplate fromFtpClientTemplate = FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(downFtpNodeId);
                    Gson gson = new Gson();
                    for (Object item : param) {
                        ConcurrentHashMap<String, Object> s = gson.fromJson(gson.toJson(item), ConcurrentHashMap.class);


                        String packageId = (String) s.get("PACKAGE_ID");
//                if(false == Boolean.valueOf( environment.getProperty("is.center"))){
//                    notifyEtl(packageId);
//                }
                        packageId = packageId.split(CommonConstants.NAME.PACKAGE_FIX)[0]+CommonConstants.NAME.ZIP;
                        try {
                            if (packageId.startsWith("PKG")) {
                                //批量移除先移除主包
                                fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() + packageId, environment.getProperty("pkg.back.path") + environment.getProperty("node.id") + "/" + FileUtil.getTodayString() + "/", "BACK_" + packageId);
                                //批量移除后查询出分包移除
                                List<String> subPackageList = sectionStepRecordersMapper.listPackageSub(packageId);
                                for (String subItem : subPackageList) {
                                    fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() + subItem, environment.getProperty("pkg.back.path") + environment.getProperty("node.id") + "/" + FileUtil.getTodayString() + "/", "BACK_" + subItem);
                                }
                            } else {
//					fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() +packageId,environment.getProperty("ack.back.path")+FileUtil.getTodayString(),"BACK_"+packageId);
                            }

                            //			如果是目标节点才入库
//				if(FileConfigUtil.CURNODEID.equals(curNodeId)){

//				}
                        } catch (Exception e) {
                            logger.error("happend error {}", e);
                        }
                    }


//					return result;
                    sectionUtils.insertSectionStepRecorders(map, new BigDecimal(1), this.getClass() + "", jobId, totalSectionNumber,new BigDecimal(0));
                    SectionNode nextSectionNode = sectionNode.getNext();
                    if(nextSectionNode!= null){
                        nextSectionNode.getCurrent().doThreadAct(packageId,jobId,nextSectionNode,totalSectionNumber,map);
                    }
                } catch (Exception e) {
                    logger.error("[{}] happend error",this.getClass(), e);
                } finally {
                    logger.info("[{}] job has [{}]",this.getClass(),threadJobSize.decrementAndGet());
                    logger.info("[{}] , package id [{}] run end ",this.getClass(),packageId);
                }
            }});
        return null;
    }
    @Override
    public SectionResult doAct(ConcurrentHashMap<String, Object> map) throws Exception {
        SectionResult result = new SectionResult(true);
        result.setMap(map);

        String downFtpNodeId = (String) map.get("DOWN_FTP_NODE_ID");

        FTPConfig fTPConfig = fTPConfigMapper.selectByNodeId(downFtpNodeId);

//			String curNodeId = (String) map.get("CUR_NODE_ID");
        List<ConcurrentHashMap<String, Object>> param = (List<ConcurrentHashMap<String, Object>>) map.get("PARAM");
        FtpClientTemplate fromFtpClientTemplate = FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(downFtpNodeId);
        Gson gson = new Gson();
        for (Object item : param) {
            ConcurrentHashMap<String, Object> s = gson.fromJson(gson.toJson(item), ConcurrentHashMap.class);


            String packageId = (String) s.get("PACKAGE_ID");
//                if(false == Boolean.valueOf( environment.getProperty("is.center"))){
//                    notifyEtl(packageId);
//                }
            packageId = packageId.split(CommonConstants.NAME.PACKAGE_FIX)[0]+CommonConstants.NAME.ZIP;
            try {
                if (packageId.startsWith("PKG")) {
                    //批量移除先移除主包
                    fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() + packageId, environment.getProperty("pkg.back.path") + environment.getProperty("node.id") + "/" + FileUtil.getTodayString() + "/", "BACK_" + packageId);
                    //批量移除后查询出分包移除
                    List<String> subPackageList = sectionStepRecordersMapper.listPackageSub(packageId);
                    for (String subItem : subPackageList) {
                        fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() + subItem, environment.getProperty("pkg.back.path") + environment.getProperty("node.id") + "/" + FileUtil.getTodayString() + "/", "BACK_" + subItem);
                    }
                } else {
//					fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() +packageId,environment.getProperty("ack.back.path")+FileUtil.getTodayString(),"BACK_"+packageId);
                }

                //			如果是目标节点才入库
//				if(FileConfigUtil.CURNODEID.equals(curNodeId)){

//				}
            } catch (Exception e) {
                logger.error("happend error {}", e);
            }
        }


        //参数准备

//			String ftpNodeId = (String) map.get("NODE_ID");
//
//			FtpClientTemplate fromFtpClientTemplate = FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(curNodeId);
//
//			//
//			String backPath = environment.getProperty("ftp.file.back.path");
//			String todayString = FileUtil.getTodayString();
//
//			List<String> fromBackFileList = (List) map.get("BACK_FILE_LIST");
//			for (int i = 0; fromBackFileList != null && i < fromBackFileList.size(); i++) {
//				String filePath = fromBackFileList.get(i);
//				String fromDir = null;
//				String fromFileName = null;
//				String toDir = backPath + todayString;
//				if (filePath.lastIndexOf("\\") < 0 && filePath.lastIndexOf("/") < 0) {
//					fromFileName = backPath + todayString + "/" + filePath;
//					fromDir = "/";
//
//				} else {
//					fromDir = filePath.substring(0, getFilePathSplit(filePath) + 1);
//					fromFileName = filePath.substring(getFilePathSplit(filePath));
//				}
//
//				logger.info("from dir[{}] ,fromFileName [{}], toDir[{}] ,toFileName [{}]", fromDir, fromFileName, toDir, fromFileName);
//				depServerFtpFileBackService.backFtpFile(fromFtpClientTemplate, toFtpClientTemplate,
//						fromDir, fromFileName, toDir, fromFileName);
//			}


        return result;
    }
}
