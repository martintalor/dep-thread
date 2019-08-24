package com.iflytek.dep.server.section;


import com.iflytek.dep.common.pack.FileUtil;
import com.iflytek.dep.server.constants.PkgStatus;
import com.iflytek.dep.server.constants.RecvSendStateEnum;
import com.iflytek.dep.server.mapper.DataPackBeanMapper;
import com.iflytek.dep.server.mapper.DataPackSubBeanMapper;
import com.iflytek.dep.server.mapper.NodeAppBeanMapper;
import com.iflytek.dep.server.model.NodeAppBean;
import com.iflytek.dep.server.service.dataPack.CreatePackService;
import com.iflytek.dep.server.service.dataPack.UpStatusService;
import com.iflytek.dep.server.utils.*;
import net.lingala.zip4j.exception.ZipException;
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
public class FilePackSection   extends BaseSectionThreadPool implements Section, Status {
    private static ExecutorService fixedUpStreamThreadPool;
    public static AtomicInteger threadJobSize = new AtomicInteger(0);
    @Autowired
    CreatePackService createPackService;
    @Autowired
    UpStatusService upStatusService;
    @Autowired
    DataPackBeanMapper dataPackBeanMapper;
    @Autowired
    DataPackSubBeanMapper dataPackSubBeanMapper;
    @Autowired
    NodeAppBeanMapper nodeAppBeanMapper;
    @Autowired
    SectionUtils sectionUtils;

    private final static Logger logger = LoggerFactory.getLogger(FilePackSection.class);
    public FilePackSection() {
        synchronized (logger){
            if(fixedUpStreamThreadPool == null){
                fixedUpStreamThreadPool =  Executors.newFixedThreadPool(threadNumber,
                        new FilePackSection.DepThreadFactory("FilePackSection"));
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

                    // 先清空
                    String packagePath = String.valueOf(map.get("PACKAGE_PATH"));
                    if (StringUtil.isNotEmpty(packagePath)) {
                        FileUtil.delAllFile(packagePath);
                    }
                    //获取需要参数
                    String packDirPath = String.valueOf(map.get("PACK_DIR_PATH"));
                    String fileName = String.valueOf(map.get("FILE_NAME"));
                    String appIdFrom = PackUtil.splitAppFrom(fileName);
                    String appIdToS = PackUtil.splitAppTos(fileName);
                    String[] appIdTo = PackUtil.splitAppTo(fileName);
                    //打包之前先在主包表中插入虚拟数据
                    upStatusService.insertPage(fileName);
                    //通过包名生成链路信息
                    map.put("PACKAGE_ID", fileName + CommonConstants.NAME.ZIP);
                    //查询需要的appid对应的当前的nodeid
                    NodeAppBean nodeAppBean = nodeAppBeanMapper.selectByPrimaryKey(appIdFrom);
                    String nodeID = nodeAppBean.getNodeId();
                    map.put("APP_ID_FROM", appIdFrom);
                    //到新的节点就创建链路并且找到去往目的地其对应的nodeId
                    List<String> toNodeId = new ArrayList<String>();
                    for (String app : appIdTo) {
                        map.put("APP_ID_TO", app);
                        ConcurrentHashMap nodeLink = upStatusService.createNodeLink(map);
                        NodeAppBean nodeApp = nodeAppBeanMapper.selectByPrimaryKey(app);
                        toNodeId.add(nodeApp.getNodeId());
                    }
                    //在打包前先插入打包中状态
                    ConcurrentHashMap<String, Object> map2 = new ConcurrentHashMap<String, Object>();
                    toNodeId.forEach(s -> {
                        map2.put("NODE_ID", nodeID);
                        map2.put("PACKAGE_ID", fileName + CommonConstants.NAME.ZIP);
                        map2.put("OPERATE_STATE_DM", CommonConstants.OPERATESTATE.YSZ);
                        map2.put("TO_NODE_ID", s);
                        //插入数据扭转进程表和更新状态表
                        //插入数据包当前状态表和数据包流水状态表
                        upStatusService.updateCurState(map2);
                    });
                    //构造传输参数
                    List<ConcurrentHashMap<String, Object>> param = new ArrayList<ConcurrentHashMap<String, Object>>();
                    //需要持久化参数准备
                    ConcurrentHashMap<String, Object> map1 = new ConcurrentHashMap<String, Object>();
                    //压缩
                    //操作开始时间
                    BigDecimal start = new BigDecimal(System.currentTimeMillis());
                    String path = null;
                    String operaState = "";// 操作状态
                    String sendState = "";// 流转状态
                    try {
                        path = createPackService.toZip(packDirPath, fileName);
                        operaState = CommonConstants.OPERATESTATE.YS;
                    } catch (ZipException e) {
                        path = FileConfigUtil.PACKEDDIR + CommonConstants.NAME.FILESPLIT + fileName;
                        sendState = RecvSendStateEnum.FAIL.getStateCode();
                        logger.error("打包异常：" + path);
                    }
                    //操作结束时间
                    BigDecimal end = new BigDecimal(System.currentTimeMillis());
                    //计算耗时
                    BigDecimal spendTime = end.subtract(start).divide(new BigDecimal(1000), 0, BigDecimal.ROUND_UP);
                    File oldFile = new File(path);
                    //通过全路径得到压缩包文件夹
                    String pagePath = oldFile.getParent();
                    File newFile = new File(pagePath);
                    //遍历此文件夹将文件信息入库
                    for (File file : newFile.listFiles()) {
                        ConcurrentHashMap<String, Object> zip = new ConcurrentHashMap<String, Object>();
                        upStatusService.insertPageAndPageSubStart(file, packDirPath);
                        String pageId = file.getName();
                        String filePath = file.getAbsolutePath();
                        String finalOperaState = operaState;
                        String finalSendState = sendState;
                        toNodeId.forEach(s -> {
                            map1.put("NODE_ID", nodeID);
                            map1.put("PACKAGE_ID", pageId);
                            map1.put("OPERATE_STATE_DM", finalOperaState);
                            map1.put("SEND_STATE_DM", finalSendState);
                            map1.put("TO_NODE_ID", s);
                            map1.put("SPEND_TIME", spendTime);
                            //插入数据扭转进程表和更新状态表
                            //插入数据包当前状态表和数据包流水状态表
                            upStatusService.updateCurState(map1);
                        });
                        //构造下个section需要参数
                        zip.put("FILE_PATH", filePath);
                        zip.put("PACKAGE_ID", pageId);
                        param.add(zip);
                    }
                    //下个section需要的参数
                    ConcurrentHashMap<String, Object> resultMap = new ConcurrentHashMap<String, Object>();
                    resultMap.put("TO_NODE_ID", toNodeId);
                    resultMap.put("CUR_NODE_ID", nodeID);
                    resultMap.put("PARAM", param);
                    resultMap.put("MARK", CommonConstants.STATE.SELF);
                    //验证有效性并返回结果
                    boolean valid = PackUtil.isValid(path);
                    if(!valid){
                        logger.info("[{}]:此包主包损坏，请手动重新处理！",fileName);
                    }
                    // 如果流转状态异常
                    if (RecvSendStateEnum.FAIL.getStateCode().equals(sendState)) {
                        valid = false;
                    }

//                    return SectionResult(valid, resultMap);
                    if(valid){
                        sectionUtils.insertSectionStepRecorders(map, new BigDecimal(1), this.getClass() + "", jobId, totalSectionNumber,new BigDecimal(0));
                        SectionNode nextSectionNode = sectionNode.getNext();
                        if(nextSectionNode!= null){
                            nextSectionNode.getCurrent().doThreadAct(packageId,jobId,nextSectionNode,totalSectionNumber,resultMap);
                        }
                    }

                }catch (Exception e) {
                    logger.error("[{}] happend error",this.getClass(), e);
                } finally {
                    logger.info("[{}] job has [{}]",this.getClass(),threadJobSize.decrementAndGet());
                    logger.info("[{}] , package id [{}] run end ",this.getClass(),packageId);
                }
            }});
        return null;
    }
    @Override
    public void update(String pkgId, PkgStatus status) {
        // update pkg status here

    }

    @Override
    public SectionResult doAct(ConcurrentHashMap<String, Object> map) throws Exception {
        // 先清空
        String packagePath = String.valueOf(map.get("PACKAGE_PATH"));
        if (StringUtil.isNotEmpty(packagePath)) {
            FileUtil.delAllFile(packagePath);
        }
        //获取需要参数
        String packDirPath = String.valueOf(map.get("PACK_DIR_PATH"));
        String fileName = String.valueOf(map.get("FILE_NAME"));
        String appIdFrom = PackUtil.splitAppFrom(fileName);
        String appIdToS = PackUtil.splitAppTos(fileName);
        String[] appIdTo = PackUtil.splitAppTo(fileName);
        //打包之前先在主包表中插入虚拟数据
        upStatusService.insertPage(fileName);
        //通过包名生成链路信息
        map.put("PACKAGE_ID", fileName + CommonConstants.NAME.ZIP);
        //查询需要的appid对应的当前的nodeid
        NodeAppBean nodeAppBean = nodeAppBeanMapper.selectByPrimaryKey(appIdFrom);
        String nodeID = nodeAppBean.getNodeId();
        map.put("APP_ID_FROM", appIdFrom);
        //到新的节点就创建链路并且找到去往目的地其对应的nodeId
        List<String> toNodeId = new ArrayList<String>();
        for (String app : appIdTo) {
            map.put("APP_ID_TO", app);
            ConcurrentHashMap nodeLink = upStatusService.createNodeLink(map);
            NodeAppBean nodeApp = nodeAppBeanMapper.selectByPrimaryKey(app);
            toNodeId.add(nodeApp.getNodeId());
        }
        //在打包前先插入打包中状态
        ConcurrentHashMap<String, Object> map2 = new ConcurrentHashMap<String, Object>();
        toNodeId.forEach(s -> {
            map2.put("NODE_ID", nodeID);
            map2.put("PACKAGE_ID", fileName + CommonConstants.NAME.ZIP);
            map2.put("OPERATE_STATE_DM", CommonConstants.OPERATESTATE.YSZ);
            map2.put("TO_NODE_ID", s);
            //插入数据扭转进程表和更新状态表
            //插入数据包当前状态表和数据包流水状态表
            upStatusService.updateCurState(map2);
        });
        //构造传输参数
        List<ConcurrentHashMap<String, Object>> param = new ArrayList<ConcurrentHashMap<String, Object>>();
        //需要持久化参数准备
        ConcurrentHashMap<String, Object> map1 = new ConcurrentHashMap<String, Object>();
        //压缩
        //操作开始时间
        BigDecimal start = new BigDecimal(System.currentTimeMillis());
        String path = null;
        String operaState = "";// 操作状态
        String sendState = "";// 流转状态
        try {
            path = createPackService.toZip(packDirPath, fileName);
            operaState = CommonConstants.OPERATESTATE.YS;
        } catch (ZipException e) {
            path = FileConfigUtil.PACKEDDIR + CommonConstants.NAME.FILESPLIT + fileName;
            sendState = RecvSendStateEnum.FAIL.getStateCode();
            logger.error("打包异常：" + path);
        }
        //操作结束时间
        BigDecimal end = new BigDecimal(System.currentTimeMillis());
        //计算耗时
        BigDecimal spendTime = end.subtract(start).divide(new BigDecimal(1000), 0, BigDecimal.ROUND_UP);
        File oldFile = new File(path);
        //通过全路径得到压缩包文件夹
        String pagePath = oldFile.getParent();
        File newFile = new File(pagePath);
        //遍历此文件夹将文件信息入库
        for (File file : newFile.listFiles()) {
            ConcurrentHashMap<String, Object> zip = new ConcurrentHashMap<String, Object>();
            upStatusService.insertPageAndPageSubStart(file, packDirPath);
            String pageId = file.getName();
            String filePath = file.getAbsolutePath();
            String finalOperaState = operaState;
            String finalSendState = sendState;
            toNodeId.forEach(s -> {
                map1.put("NODE_ID", nodeID);
                map1.put("PACKAGE_ID", pageId);
                map1.put("OPERATE_STATE_DM", finalOperaState);
                map1.put("SEND_STATE_DM", finalSendState);
                map1.put("TO_NODE_ID", s);
                map1.put("SPEND_TIME", spendTime);
                //插入数据扭转进程表和更新状态表
                //插入数据包当前状态表和数据包流水状态表
                upStatusService.updateCurState(map1);
            });
            //构造下个section需要参数
            zip.put("FILE_PATH", filePath);
            zip.put("PACKAGE_ID", pageId);
            param.add(zip);
        }
        //下个section需要的参数
        ConcurrentHashMap<String, Object> resultMap = new ConcurrentHashMap<String, Object>();
        resultMap.put("TO_NODE_ID", toNodeId);
        resultMap.put("CUR_NODE_ID", nodeID);
        resultMap.put("PARAM", param);
        resultMap.put("MARK", CommonConstants.STATE.SELF);
        //验证有效性并返回结果
        boolean valid = PackUtil.isValid(path);
        if(!valid){
            logger.info("[{}]:此包主包损坏，请手动重新处理！",fileName);
        }
        // 如果流转状态异常
        if (RecvSendStateEnum.FAIL.getStateCode().equals(sendState)) {
            valid = false;
        }

        return SectionResult(valid, resultMap);
    }

    private SectionResult SectionResult(boolean b, ConcurrentHashMap<String, Object> result) {
        // TODO Auto-generated method stub
        SectionResult sectionResult = new SectionResult(b);
        sectionResult.setMap(result);
        return sectionResult;
    }

}
