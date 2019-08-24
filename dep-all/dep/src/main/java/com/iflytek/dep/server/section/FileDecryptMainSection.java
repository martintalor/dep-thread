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
public class FileDecryptMainSection  extends BaseSectionThreadPool implements Section, Status {
    private final static Logger logger = LoggerFactory.getLogger(FileDecryptMainSection.class);
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

    public FileDecryptMainSection() {
        synchronized (logger){
            if(fixedUpStreamThreadPool == null){
                fixedUpStreamThreadPool =  Executors.newFixedThreadPool(threadNumber,
                        new FileDecryptMainSection.DepThreadFactory("FileDecryptMainSection"));
            }
        }
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
                    //根据包名截取去那里的appID
                    String[] appIdTo = PackUtil.splitAppTo(packageId);
                    //到新的节点找到去往目的地其对应的nodeId
                    List<String> toNodeId = new ArrayList<String>();
                    for (String app : appIdTo) {
                        NodeAppBean nodeApp = nodeAppBeanMapper.selectByPrimaryKey(app);
                        toNodeId.add(nodeApp.getNodeId());
                    }
                    //参数准备
                    ConcurrentHashMap<String, Object> map1 = new ConcurrentHashMap<String, Object>();
                    //在解密之前把解密中状态插入
                    for (String app : toNodeId) {
                        ConcurrentHashMap<String, Object> zip = new ConcurrentHashMap<String, Object>();
                        map1.put("NODE_ID", FileConfigUtil.CURNODEID);
                        map1.put("PACKAGE_ID", packageId);
                        map1.put("OPERATE_STATE_DM", CommonConstants.OPERATESTATE.ZXJIEMZ);
                        map1.put("TO_NODE_ID", app);
                        //插入数据扭转进程表和更新状态表
                        //插入数据包当前状态表和数据包流水状态表
                        upStatusService.updateCurState(map1);
                    }
                    //操作开始时间
                    BigDecimal start = new BigDecimal(System.currentTimeMillis());
                    //解密此路径加密文件
                    String operaState = "";// 操作状态
                    String sendState = "";// 流转状态
                    try {
                        createPackService.decryptZip(packPath);
                        operaState = CommonConstants.OPERATESTATE.ZXJIEM; // 解密成功
                        //删除加密文件并更改解密文件名称
                        PackUtil.renameAndDeleteZip(packPath);
                    } catch (DecryptException e) {
                        sendState = RecvSendStateEnum.FAIL.getStateCode();
                        logger.error("解密异常：" + packPath);
                    }
                    //操作结束时间
                    BigDecimal end = new BigDecimal(System.currentTimeMillis());
                    //计算耗时
                    BigDecimal spendTime = end.subtract(start).divide(new BigDecimal(1000), 0, BigDecimal.ROUND_UP);

                    //参数准备
                    List<ConcurrentHashMap<String, Object>> param = new ArrayList<ConcurrentHashMap<String, Object>>();
                    //插入主包和子包表
                    upStatusService.insertPageAndPageSub(file);
                    //若是中心节点
                    for (String app : toNodeId) {
                        ConcurrentHashMap<String, Object> zip = new ConcurrentHashMap<String, Object>();
                        map1.put("NODE_ID", FileConfigUtil.CURNODEID);
                        map1.put("PACKAGE_ID", packageId);
                        map1.put("OPERATE_STATE_DM", operaState);
                        map1.put("SEND_STATE_DM", sendState);
                        map1.put("TO_NODE_ID", app);
                        map1.put("SPEND_TIME",spendTime);
                        //插入数据扭转进程表和更新状态表
                        //插入数据包当前状态表和数据包流水状态表
                        upStatusService.updateCurState(map1);
                        //构造下个section需要参数
                        zip.put("FILE_PATH", file.getAbsolutePath());
                        zip.put("TO_NODE_ID", app);
                        zip.put("PACKAGE_ID", packageId);
                        param.add(zip);
                    }

                    map.put("CUR_NODE_ID", FileConfigUtil.CURNODEID);
                    map.put("PARAM", param);
                    map.put("MARK", CommonConstants.STATE.SELF);
                    map.put("FILE_PATH", file.getAbsolutePath());
                    //验证有效性并返回结果
                    // boolean valid = PackUtil.isValid(packPath);
                    boolean valid = true;

                    // 如果流转状态异常
                    if (RecvSendStateEnum.FAIL.getStateCode().equals(sendState)) {
                        valid = false;
                        //throw new BusinessErrorException(ExceptionState.DECRYPT.getCode(),ExceptionState.DECRYPT.getName()+ packPath);
                    }


//                    return SectionResult(valid, map);
                    if(valid){
                        sectionUtils.insertSectionStepRecorders(map, new BigDecimal(1), this.getClass() + "", jobId, totalSectionNumber,new BigDecimal(0));
                        SectionNode nextSectionNode = sectionNode.getNext();
                        if(nextSectionNode!= null){
                            nextSectionNode.getCurrent().doThreadAct(packageId,jobId,nextSectionNode,totalSectionNumber,map);
                        }
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
    public void update(String pkgId, PkgStatus status) {
        // update pkg status here

    }
    @Override
    public SectionResult doAct(ConcurrentHashMap<String, Object> map) throws Exception {
        //获取加密包的路径
        String packPath = String.valueOf(map.get("PACK_PATH"));
        File file = new File(packPath);
        String pagePath = file.getParent();
        //获取包名
        String packageId = file.getName();
        //根据包名截取去那里的appID
        String[] appIdTo = PackUtil.splitAppTo(packageId);
        //到新的节点找到去往目的地其对应的nodeId
        List<String> toNodeId = new ArrayList<String>();
        for (String app : appIdTo) {
            NodeAppBean nodeApp = nodeAppBeanMapper.selectByPrimaryKey(app);
            toNodeId.add(nodeApp.getNodeId());
        }
        //参数准备
        ConcurrentHashMap<String, Object> map1 = new ConcurrentHashMap<String, Object>();
        //在解密之前把解密中状态插入
        for (String app : toNodeId) {
            ConcurrentHashMap<String, Object> zip = new ConcurrentHashMap<String, Object>();
            map1.put("NODE_ID", FileConfigUtil.CURNODEID);
            map1.put("PACKAGE_ID", packageId);
            map1.put("OPERATE_STATE_DM", CommonConstants.OPERATESTATE.ZXJIEMZ);
            map1.put("TO_NODE_ID", app);
            //插入数据扭转进程表和更新状态表
            //插入数据包当前状态表和数据包流水状态表
            upStatusService.updateCurState(map1);
        }
        //操作开始时间
        BigDecimal start = new BigDecimal(System.currentTimeMillis());
        //解密此路径加密文件
        String operaState = "";// 操作状态
        String sendState = "";// 流转状态
        try {
            createPackService.decryptZip(packPath);
            operaState = CommonConstants.OPERATESTATE.ZXJIEM; // 解密成功
            //删除加密文件并更改解密文件名称
            PackUtil.renameAndDeleteZip(packPath);
        } catch (DecryptException e) {
            sendState = RecvSendStateEnum.FAIL.getStateCode();
            logger.error("解密异常：" + packPath);
        }
        //操作结束时间
        BigDecimal end = new BigDecimal(System.currentTimeMillis());
        //计算耗时
        BigDecimal spendTime = end.subtract(start).divide(new BigDecimal(1000), 0, BigDecimal.ROUND_UP);

        //参数准备
        List<ConcurrentHashMap<String, Object>> param = new ArrayList<ConcurrentHashMap<String, Object>>();
        //插入主包和子包表
        upStatusService.insertPageAndPageSub(file);
        //若是中心节点
        for (String app : toNodeId) {
            ConcurrentHashMap<String, Object> zip = new ConcurrentHashMap<String, Object>();
            map1.put("NODE_ID", FileConfigUtil.CURNODEID);
            map1.put("PACKAGE_ID", packageId);
            map1.put("OPERATE_STATE_DM", operaState);
            map1.put("SEND_STATE_DM", sendState);
            map1.put("TO_NODE_ID", app);
            map1.put("SPEND_TIME",spendTime);
            //插入数据扭转进程表和更新状态表
            //插入数据包当前状态表和数据包流水状态表
            upStatusService.updateCurState(map1);
            //构造下个section需要参数
            zip.put("FILE_PATH", file.getAbsolutePath());
            zip.put("TO_NODE_ID", app);
            zip.put("PACKAGE_ID", packageId);
            param.add(zip);
        }

        map.put("CUR_NODE_ID", FileConfigUtil.CURNODEID);
        map.put("PARAM", param);
        map.put("MARK", CommonConstants.STATE.SELF);
        map.put("FILE_PATH", file.getAbsolutePath());
        //验证有效性并返回结果
        // boolean valid = PackUtil.isValid(packPath);
        boolean valid = true;

        // 如果流转状态异常
        if (RecvSendStateEnum.FAIL.getStateCode().equals(sendState)) {
            valid = false;
            //throw new BusinessErrorException(ExceptionState.DECRYPT.getCode(),ExceptionState.DECRYPT.getName()+ packPath);
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
