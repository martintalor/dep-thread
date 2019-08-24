package com.iflytek.dep.server.section;


import com.iflytek.dep.common.security.DecryptException;
import com.iflytek.dep.server.constants.PkgStatus;
import com.iflytek.dep.server.constants.RecvSendStateEnum;
import com.iflytek.dep.server.mapper.DataPackBeanMapper;
import com.iflytek.dep.server.mapper.DataPackSubBeanMapper;
import com.iflytek.dep.server.mapper.NodeAppBeanMapper;
import com.iflytek.dep.server.mapper.NodeLinkBeanMapper;
import com.iflytek.dep.server.model.NodeAppBean;
import com.iflytek.dep.server.service.dataPack.CreatePackService;
import com.iflytek.dep.server.service.dataPack.UpStatusService;
import com.iflytek.dep.server.utils.CommonConstants;
import com.iflytek.dep.server.utils.FileConfigUtil;
import com.iflytek.dep.server.utils.PackUtil;
import com.iflytek.dep.server.utils.SectionUtils;
import net.lingala.zip4j.core.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Scope("prototype")
public class FileDecryptLeafSection extends BaseSectionThreadPool implements Section, Status {
    private static ExecutorService fixedUpStreamThreadPool;
    public static AtomicInteger threadJobSize = new AtomicInteger(0);
    @Autowired
    CreatePackService createPackService;
    @Autowired
    UpStatusService upStatusService;
    @Autowired
    NodeLinkBeanMapper nodeLinkBeanMapper;
    @Autowired
    DataPackBeanMapper dataPackBeanMapper;
    @Autowired
    DataPackSubBeanMapper dataPackSubBeanMapper;
    @Autowired
    NodeAppBeanMapper nodeAppBeanMapper;
    @Autowired
    SectionUtils sectionUtils;

    private final static Logger logger = LoggerFactory.getLogger(FileDecryptLeafSection.class);


	public FileDecryptLeafSection() {
        synchronized (logger){
            if(fixedUpStreamThreadPool == null){
                fixedUpStreamThreadPool =  Executors.newFixedThreadPool(threadNumber,
                        new FileDecryptLeafSection.DepThreadFactory("FileDecryptLeafSection"));
            }
        }
	}

	@Override
    public void update(String pkgId, PkgStatus status) {
        // update pkg status here

    }
	@Override
	public SectionResult doThreadAct(final String packageId,String jobId, SectionNode sectionNode, BigDecimal totalSectionNumber, ConcurrentHashMap<String, Object> map) throws Exception {
        logger.info("[{}] job has [{}],package id [{}] in method ,jobId is [{}]",this.getClass(),threadJobSize.incrementAndGet(),packageId,jobId);
		fixedUpStreamThreadPool.submit(new Runnable() {

			public void run() {
				try {
                    logger.info("[{}] , package id [{}] running",this.getClass(),packageId);
                    sectionUtils.insertSectionStepRecorders(map, new BigDecimal(0), this.getClass() + "", jobId, totalSectionNumber,new BigDecimal(0));

                    //获取加密包的路径
                        String packPath = String.valueOf(map.get("PACK_PATH"));
                        File file = new File(packPath);
                        String pagePath = file.getParent();
                        //获取包名
                        String packageId = file.getName();
                        //拼接解密后文件夹路径
                        String parentPath = pagePath + CommonConstants.NAME.FILESPLIT + "unpack";
                        //拼接解密后文件全路径
                        String newPath = parentPath + CommonConstants.NAME.FILESPLIT + packageId;
                        //根据包名截取去那里的appID
                        String[] appIdTo = PackUtil.splitAppTo(packageId);
                        //到新的节点找到去往目的地其对应的nodeId
                        List<String> toNodeId = new ArrayList<String>();
                        for (String app : appIdTo) {
                            NodeAppBean nodeApp = nodeAppBeanMapper.selectByPrimaryKey(app);
                            toNodeId.add(nodeApp.getNodeId());
                        }
                        //准备参数
                        ConcurrentHashMap<String, Object> map1 = new ConcurrentHashMap<String, Object>();
                        map1.put("NODE_ID", FileConfigUtil.CURNODEID);
                        map1.put("PACKAGE_ID", packageId);
                        map1.put("OPERATE_STATE_DM", CommonConstants.OPERATESTATE.JIEMIZ);
                        map1.put("TO_NODE_ID", FileConfigUtil.CURNODEID);
                        //插入数据扭转进程表和更新状态表
                        //插入数据包当前状态表和数据包流水状态表
                        upStatusService.updateCurState(map1);
                        //操作开始时间
                        BigDecimal start = new BigDecimal(System.currentTimeMillis());
                        //解密此路径加密文件
                        String operaState = "";// 操作状态
                        String sendState = "";// 流转状态
                        try {
                            //创建存放解密文件的文件夹
                            PackUtil.makeDir(parentPath);
                            //解密
                            createPackService.decryptLeafZip(packPath);
                            operaState = CommonConstants.OPERATESTATE.JIEMI; // 解密成功
                            //删除加密文件并更改解密文件名称
                            PackUtil.renameAndDeleteLeafZip(packPath);
                        } catch (DecryptException e) {
                            sendState = RecvSendStateEnum.FAIL.getStateCode();
                            logger.error("解密异常：" + "解密前文件：" + packPath + "解密后文件位置：" + newPath);
                        }

                        //操作结束时间
                        BigDecimal end = new BigDecimal(System.currentTimeMillis());
                        //计算耗时
                        BigDecimal spendTime = end.subtract(start).divide(new BigDecimal(1000), 0, BigDecimal.ROUND_UP);

                        //参数准备
                        List<ConcurrentHashMap<String, Object>> param = new ArrayList<ConcurrentHashMap<String, Object>>();

                        //插入主包和子包表
                        File newzip = new File(newPath);
                        upStatusService.insertPageAndPageSub(newzip);

                        //非中心节点时
                        ConcurrentHashMap<String, Object> zip = new ConcurrentHashMap<String, Object>();
                        map1.put("NODE_ID", FileConfigUtil.CURNODEID);
                        map1.put("PACKAGE_ID", packageId);
                        map1.put("OPERATE_STATE_DM", operaState);
                        map1.put("SEND_STATE_DM", sendState);
                        map1.put("TO_NODE_ID", FileConfigUtil.CURNODEID);
                        map1.put("SPEND_TIME",spendTime);
                        //插入数据扭转进程表和更新状态表
                        //插入数据包当前状态表和数据包流水状态表
                        upStatusService.updateCurState(map1);
                        //构造下个section需要参数
                        zip.put("FILE_PATH", newzip.getAbsolutePath());
                        zip.put("PACKAGE_ID", packageId);
                        param.add(zip);


                        map.put("CUR_NODE_ID", FileConfigUtil.CURNODEID);
                        map.put("PARAM", param);
                        map.put("MARK", CommonConstants.STATE.SELF);

                        //验证包完整性并返回结果
//        boolean valid;
//        boolean flag = PackUtil.isValid(newPath.split("\\.")[0] + CommonConstants.NAME.ZIP);
//        if (flag) {
//            ZipFile zipFile = new ZipFile(newPath.split("\\.")[0] + CommonConstants.NAME.ZIP);
//            //第一时间设置编码格式
//            zipFile.setFileNameCharset("UTF-8");
//            if (zipFile.getSplitZipFiles().size() == new File(parentPath).listFiles().length) {
//                valid=true;
//            }else{
//                valid=false;
//            }
//        } else {
//            valid = false;
//        }
                        boolean valid = PackUtil.isValid(newPath.split(CommonConstants.NAME.PACKAGE_FIX)[0] + CommonConstants.NAME.ZIP);
                        if (valid) {
                            ZipFile zipFile = new ZipFile(newPath.split(CommonConstants.NAME.PACKAGE_FIX)[0] + CommonConstants.NAME.ZIP);
                            //第一时间设置编码格式
                            zipFile.setFileNameCharset("UTF-8");

                            valid = (zipFile.getSplitZipFiles().size() == new File(parentPath).listFiles().length);
                        }
                        // 如果流转状态异常
                        if (RecvSendStateEnum.FAIL.getStateCode().equals(sendState)) {
                            valid = false;
                            //throw new BusinessErrorException(ExceptionState.DECRYPT.getCode(), ExceptionState.DECRYPT.getName() + packPath);
                        }

//                        return SectionResult(valid, map);
                    sectionUtils.insertSectionStepRecorders(map, new BigDecimal(1), this.getClass() + "", jobId, totalSectionNumber,new BigDecimal(0));
                    SectionNode nextSectionNode = sectionNode.getNext();
                    if(nextSectionNode!= null && valid){
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
        //获取加密包的路径
        String packPath = String.valueOf(map.get("PACK_PATH"));
        File file = new File(packPath);
        String pagePath = file.getParent();
        //获取包名
        String packageId = file.getName();
        //拼接解密后文件夹路径
        String parentPath = pagePath + CommonConstants.NAME.FILESPLIT + "unpack";
        //拼接解密后文件全路径
        String newPath = parentPath + CommonConstants.NAME.FILESPLIT + packageId;
        //根据包名截取去那里的appID
        String[] appIdTo = PackUtil.splitAppTo(packageId);
        //到新的节点找到去往目的地其对应的nodeId
        List<String> toNodeId = new ArrayList<String>();
        for (String app : appIdTo) {
            NodeAppBean nodeApp = nodeAppBeanMapper.selectByPrimaryKey(app);
            toNodeId.add(nodeApp.getNodeId());
        }
        //准备参数
        ConcurrentHashMap<String, Object> map1 = new ConcurrentHashMap<String, Object>();
        map1.put("NODE_ID", FileConfigUtil.CURNODEID);
        map1.put("PACKAGE_ID", packageId);
        map1.put("OPERATE_STATE_DM", CommonConstants.OPERATESTATE.JIEMIZ);
        map1.put("TO_NODE_ID", FileConfigUtil.CURNODEID);
        //插入数据扭转进程表和更新状态表
        //插入数据包当前状态表和数据包流水状态表
        upStatusService.updateCurState(map1);
        //操作开始时间
        BigDecimal start = new BigDecimal(System.currentTimeMillis());
        //解密此路径加密文件
        String operaState = "";// 操作状态
        String sendState = "";// 流转状态
        try {
            //创建存放解密文件的文件夹
            PackUtil.makeDir(parentPath);
            //解密
            createPackService.decryptLeafZip(packPath);
            operaState = CommonConstants.OPERATESTATE.JIEMI; // 解密成功
            //删除加密文件并更改解密文件名称
            PackUtil.renameAndDeleteLeafZip(packPath);
        } catch (DecryptException e) {
            sendState = RecvSendStateEnum.FAIL.getStateCode();
            logger.error("解密异常：" + "解密前文件：" + packPath + "解密后文件位置：" + newPath);
        }

        //操作结束时间
        BigDecimal end = new BigDecimal(System.currentTimeMillis());
        //计算耗时
        BigDecimal spendTime = end.subtract(start).divide(new BigDecimal(1000), 0, BigDecimal.ROUND_UP);

        //参数准备
        List<ConcurrentHashMap<String, Object>> param = new ArrayList<ConcurrentHashMap<String, Object>>();

        //插入主包和子包表
        File newzip = new File(newPath);
        upStatusService.insertPageAndPageSub(newzip);

        //非中心节点时
        ConcurrentHashMap<String, Object> zip = new ConcurrentHashMap<String, Object>();
        map1.put("NODE_ID", FileConfigUtil.CURNODEID);
        map1.put("PACKAGE_ID", packageId);
        map1.put("OPERATE_STATE_DM", operaState);
        map1.put("SEND_STATE_DM", sendState);
        map1.put("TO_NODE_ID", FileConfigUtil.CURNODEID);
        map1.put("SPEND_TIME",spendTime);
        //插入数据扭转进程表和更新状态表
        //插入数据包当前状态表和数据包流水状态表
        upStatusService.updateCurState(map1);
        //构造下个section需要参数
        zip.put("FILE_PATH", newzip.getAbsolutePath());
        zip.put("PACKAGE_ID", packageId);
        param.add(zip);


        map.put("CUR_NODE_ID", FileConfigUtil.CURNODEID);
        map.put("PARAM", param);
        map.put("MARK", CommonConstants.STATE.SELF);

        //验证包完整性并返回结果
//        boolean valid;
//        boolean flag = PackUtil.isValid(newPath.split("\\.")[0] + CommonConstants.NAME.ZIP);
//        if (flag) {
//            ZipFile zipFile = new ZipFile(newPath.split("\\.")[0] + CommonConstants.NAME.ZIP);
//            //第一时间设置编码格式
//            zipFile.setFileNameCharset("UTF-8");
//            if (zipFile.getSplitZipFiles().size() == new File(parentPath).listFiles().length) {
//                valid=true;
//            }else{
//                valid=false;
//            }
//        } else {
//            valid = false;
//        }
        boolean valid = PackUtil.isValid(newPath.split(CommonConstants.NAME.PACKAGE_FIX)[0] + CommonConstants.NAME.ZIP);
        if (valid) {
            ZipFile zipFile = new ZipFile(newPath.split(CommonConstants.NAME.PACKAGE_FIX)[0] + CommonConstants.NAME.ZIP);
            //第一时间设置编码格式
            zipFile.setFileNameCharset("UTF-8");

            valid = (zipFile.getSplitZipFiles().size() == new File(parentPath).listFiles().length);
        }
        // 如果流转状态异常
        if (RecvSendStateEnum.FAIL.getStateCode().equals(sendState)) {
            valid = false;
            //throw new BusinessErrorException(ExceptionState.DECRYPT.getCode(), ExceptionState.DECRYPT.getName() + packPath);
        }

        return SectionResult(valid, map);

    }

    private SectionResult SectionResult(boolean b, ConcurrentHashMap<String, Object> result) {
        // TODO Auto-generated method stub
        SectionResult sectionResult = new SectionResult(b);
        sectionResult.setMap(result);
        return sectionResult;
    }

}
