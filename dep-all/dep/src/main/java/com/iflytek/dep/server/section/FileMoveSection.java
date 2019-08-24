package com.iflytek.dep.server.section;

import com.google.gson.Gson;
import com.iflytek.dep.server.ftp.core.FtpClientTemplate;
import com.iflytek.dep.server.mapper.FTPConfigMapper;
import com.iflytek.dep.server.mapper.NodeAppBeanMapper;
import com.iflytek.dep.server.mapper.SectionStepRecordersMapper;
import com.iflytek.dep.server.model.FTPConfig;
import com.iflytek.dep.server.model.NodeAppBean;
import com.iflytek.dep.server.service.threadPool.DepServerFtpFileBackService;
import com.iflytek.dep.server.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FileMoveSection   extends BaseSectionThreadPool implements Section {
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
	public FileMoveSection() {
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
						try {
							if (packageId.startsWith("PKG")) {
								fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() + packageId, environment.getProperty("pkg.back.path") + environment.getProperty("node.id") + "/" + FileUtil.getTodayString() + "/", "BACK_" + packageId);
							} else {
//					fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() +packageId,environment.getProperty("ack.back.path")+FileUtil.getTodayString(),"BACK_"+packageId);
							}
							//截取掉包名后的zip或者z01等后缀
							packageId= packageId.split(CommonConstants.NAME.PACKAGE_FIX)[0];
							if(!Boolean.valueOf( environment.getProperty("is.center"))){
								List<String> subPackageList = sectionStepRecordersMapper.listPackageSub(packageId+".zip");
								for (String subItem : subPackageList) {
									fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() + subItem, environment.getProperty("pkg.back.path") + environment.getProperty("node.id") + "/" + FileUtil.getTodayString() + "/", "BACK_" + subItem);
								}
							}
							//			如果是目标节点才入库
//				if(FileConfigUtil.CURNODEID.equals(curNodeId)){

//				}
						} catch (Exception e) {
							//打印异常堆栈信息 -- modify by jzkan 20190506
							logger.error("[{}] happend error",this,getClass(),e);
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
				try {
				if (packageId.startsWith("PKG")) {
					fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() + packageId, environment.getProperty("pkg.back.path") + environment.getProperty("node.id") + "/" + FileUtil.getTodayString() + "/", "BACK_" + packageId);
				} else {
//					fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() +packageId,environment.getProperty("ack.back.path")+FileUtil.getTodayString(),"BACK_"+packageId);
				}

				if(!(true == Boolean.valueOf( environment.getProperty("is.center")))){
					List<String> subPackageList = sectionStepRecordersMapper.listPackageSub(packageId+".zip");
					for (String subItem : subPackageList) {
						fromFtpClientTemplate.moveFile(fTPConfig.getDataPackageFolderDown() + subItem, environment.getProperty("pkg.back.path") + environment.getProperty("node.id") + "/" + FileUtil.getTodayString() + "/", "BACK_" + subItem);
					}
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

	private void notifyEtl(String packageId){
		logger.info("------------------开始调用etl入库接口：{}",packageId);
		String[] toApps = PackUtil.splitAppTo(packageId);
		NodeAppBean nodeAppBean=null;
		for (String itemApp: toApps ) {
			nodeAppBean = nodeAppBeanMapper.selectByPrimaryKey(itemApp);
			if(FileConfigUtil.CURNODEID.equals(nodeAppBean.getNodeId())){
				break;
			}
		}
		logger.info("----------------判断成功是某个nodeapp{}",nodeAppBean.getClass().toString());
		try {
			if(nodeAppBean!=null){
				if(nodeAppBean.getCalUrl()!=null){
					logger.info("----------------------接口地址：{}",nodeAppBean.getCalUrl());
					logger.info("----------------------接口地址加参数：{}",nodeAppBean.getCalUrl() + environment.getProperty("packed.dir") +"/"+packageId.split("\\.")[0].replaceAll(CommonConstants.NAME.APPSPLIT,"") +"/unpack/" + packageId.split("\\.")[0].replaceAll(CommonConstants.NAME.APPSPLIT,""));
					ResponseBean forObject = restTemplate.getForObject(nodeAppBean.getCalUrl() + environment.getProperty("packed.dir") +"/"+packageId.split("\\.")[0].replaceAll(CommonConstants.NAME.APPSPLIT,"") +"/unpack/" + packageId.split("\\.")[0].replaceAll(CommonConstants.NAME.APPSPLIT,""), ResponseBean.class);
				}
			}
		} catch (Exception e) {
			logger.error("notify etl happend error [{}]",e);
		}
	}
	private int getFilePathSplit(String filePath) {
		logger.info(" find char / or \\  in path [{}] ", filePath);
		for (int i = filePath.length() - 1; i > 0; i--) {
			char charInfo = filePath.charAt(i);
			if (charInfo == '/' || charInfo == '\\') {
				return i;
			}
		}
		return 0;
	}
}
