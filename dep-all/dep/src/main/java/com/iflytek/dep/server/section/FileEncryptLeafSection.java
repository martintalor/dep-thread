package com.iflytek.dep.server.section;


import com.google.gson.Gson;
import com.iflytek.dep.common.security.EncryptException;
import com.iflytek.dep.server.constants.ExceptionState;
import com.iflytek.dep.server.constants.PkgStatus;
import com.iflytek.dep.server.constants.RecvSendStateEnum;
import com.iflytek.dep.server.mapper.DataNodeProcessBeanMapper;
import com.iflytek.dep.server.mapper.NodeAppBeanMapper;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Scope("prototype")
public class FileEncryptLeafSection   extends BaseSectionThreadPool implements Section, Status {
    private static Logger logger = LoggerFactory.getLogger(FileEncryptLeafSection.class);
    private static ExecutorService fixedUpStreamThreadPool;
    public static AtomicInteger threadJobSize = new AtomicInteger(0);

    @Autowired
    CreatePackService createPackService;
    @Autowired
    UpStatusService upStatusService;
    @Autowired
    DataNodeProcessBeanMapper dataNodeProcessBeanMapper;
    @Autowired
    NodeAppBeanMapper nodeAppBeanMapper;
    @Autowired
    SectionUtils sectionUtils;


    public FileEncryptLeafSection() {
        synchronized (logger){
            if(fixedUpStreamThreadPool == null){
                fixedUpStreamThreadPool =  Executors.newFixedThreadPool(threadNumber,
                        new FileEncryptLeafSection.DepThreadFactory("FileEncryptLeafSection"));
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

                    //得到参数
                    List<ConcurrentHashMap<String, Object>> param = (List<ConcurrentHashMap<String, Object>>) map.get("PARAM");
                    List<String> toNodeId = (List<String>) map.get("TO_NODE_ID");
                    //先得到后期需要存放的list
                    List<ConcurrentHashMap<String, Object>> param1 = new ArrayList<ConcurrentHashMap<String, Object>>();
                    //得到加密方式
                    String mark = null;
                    Object state = map.get("MARK");
                    if (state == null) {
                        mark = CommonConstants.STATE.SELF;
                    } else {
                        mark = String.valueOf(state);
                    }
                    final String MARK = mark;
                    //循环之外就用CountDownLatch控制线程的执行
                    CountDownLatch countDownLatch = new CountDownLatch(param.size());
                    //循环param里的路径有一个就开启一个新的线程来处理加密这个事件
                    for (Object item : param) {
                        Gson gson = new Gson();
                        ConcurrentHashMap<String, Object> s = gson.fromJson(gson.toJson(item), ConcurrentHashMap.class);

                        //参数准备
                        String filePath = String.valueOf(s.get("FILE_PATH"));
                        String packageId = String.valueOf(s.get("PACKAGE_ID"));
                        //往当前状态和操作状态表里插入数据
                        ConcurrentHashMap<String, Object> map2 = new ConcurrentHashMap<String, Object>();
                        toNodeId.forEach(s1 -> {
                            //循环更新数据
                            map2.put("NODE_ID", String.valueOf(map.get("CUR_NODE_ID")));
                            map2.put("OPERATE_STATE_DM", CommonConstants.OPERATESTATE.JIAMZ);
                            map2.put("PACKAGE_ID", packageId);
                            map2.put("TO_NODE_ID", s1);
                            upStatusService.updateCurState(map2);
                        });
                        File file = new File(filePath);
                        String parentDir = file.getParent();
                        new Thread(() -> {
                            //如果是子节点
                            //判断用原本系统加密方式，还是用ck预留扩展的加密方式
                            if (CommonConstants.STATE.SELF.equals(MARK)) {
                                String publickey = FileConfigUtil.PUBLICKEY;
                                //创建存放加密文件的文件夹
                                PackUtil.makeDir(parentDir + CommonConstants.NAME.FILESPLIT + "ZSE01");
                                //操作开始时间
                                BigDecimal start = new BigDecimal(System.currentTimeMillis());
                                String operaState = "";// 操作状态
                                String sendState = "";// 流转状态
                                //加密
                                try {
                                    createPackService.encryptZip(filePath, publickey, "ZSE01");
                                    operaState = CommonConstants.OPERATESTATE.JIAM;
                                    //删除原没加密压缩包
                                    PackUtil.deleteZip(filePath);
                                } catch (EncryptException e) {
                                    sendState = RecvSendStateEnum.FAIL.getStateCode();
                                    //将异常带出去处理
                                    map.put("flag", false);
                                    logger.error(ExceptionState.ENCRYPT.getCode() + ExceptionState.ENCRYPT.getName() + filePath);
                                }
                                try {
                                    //操作结束时间
                                    BigDecimal end = new BigDecimal(System.currentTimeMillis());
                                    //计算耗时
                                    BigDecimal spendTime = end.subtract(start).divide(new BigDecimal(1000), 0, BigDecimal.ROUND_UP);
                                    String finalOperaState = operaState;
                                    String finalSendState = sendState;
                                    toNodeId.forEach(s1 -> {
                                        //循环更新数据
                                        map2.put("NODE_ID", String.valueOf(map.get("CUR_NODE_ID")));
                                        map2.put("OPERATE_STATE_DM", finalOperaState);
                                        map2.put("SEND_STATE_DM", finalSendState);
                                        map2.put("PACKAGE_ID", packageId);
                                        map2.put("TO_NODE_ID", s1);
                                        map2.put("SPEND_TIME",spendTime);
                                        upStatusService.updateCurState(map2);
                                    });
                                    s.put("FILE_PATH", parentDir + CommonConstants.NAME.FILESPLIT + "ZSE01" + CommonConstants.NAME.FILESPLIT + packageId);
                                    s.put("TO_NODE_ID", toNodeId.get(0));
                                    s.put("NODE_ID", String.valueOf(map.get("CUR_NODE_ID")));
                                    //构造传给下个节点的参数
                                    param1.add(s);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    //执行成功一次计数器减一
                                    countDownLatch.countDown();
                                }

                            }
                            if (CommonConstants.STATE.CA.equals(MARK)) {

                                //CA预留位置
                            }
                        }).start();
                    }
//        param.forEach(s -> {
//        });
                    //如果上边俩线程没执行完就要等待其执行
                    countDownLatch.await();
                    map.put("PARAM", param1);
                    if (map.get("flag") == null) {
//                        return SectionResult(true, map);
                    } else {
//                        return SectionResult((boolean) map.get("flag"), map);
                    }
                    if(map.get("flag") == null||true == (boolean) map.get("flag")){
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
        //得到参数
        List<ConcurrentHashMap<String, Object>> param = (List<ConcurrentHashMap<String, Object>>) map.get("PARAM");
        List<String> toNodeId = (List<String>) map.get("TO_NODE_ID");
        //先得到后期需要存放的list
        List<ConcurrentHashMap<String, Object>> param1 = new ArrayList<ConcurrentHashMap<String, Object>>();
        //得到加密方式
        String mark = null;
        Object state = map.get("MARK");
        if (state == null) {
            mark = CommonConstants.STATE.SELF;
        } else {
            mark = String.valueOf(state);
        }
        final String MARK = mark;
        //循环之外就用CountDownLatch控制线程的执行
        CountDownLatch countDownLatch = new CountDownLatch(param.size());
        //循环param里的路径有一个就开启一个新的线程来处理加密这个事件
        for (Object item : param) {
            Gson gson = new Gson();
            ConcurrentHashMap<String, Object> s = gson.fromJson(gson.toJson(item), ConcurrentHashMap.class);

            //参数准备
            String filePath = String.valueOf(s.get("FILE_PATH"));
            String packageId = String.valueOf(s.get("PACKAGE_ID"));
            //往当前状态和操作状态表里插入数据
            ConcurrentHashMap<String, Object> map2 = new ConcurrentHashMap<String, Object>();
            toNodeId.forEach(s1 -> {
                //循环更新数据
                map2.put("NODE_ID", String.valueOf(map.get("CUR_NODE_ID")));
                map2.put("OPERATE_STATE_DM", CommonConstants.OPERATESTATE.JIAMZ);
                map2.put("PACKAGE_ID", packageId);
                map2.put("TO_NODE_ID", s1);
                upStatusService.updateCurState(map2);
            });
            File file = new File(filePath);
            String parentDir = file.getParent();
            new Thread(() -> {
                //如果是子节点
                //判断用原本系统加密方式，还是用ck预留扩展的加密方式
                if (CommonConstants.STATE.SELF.equals(MARK)) {
                    String publickey = FileConfigUtil.PUBLICKEY;
                    //创建存放加密文件的文件夹
                    PackUtil.makeDir(parentDir + CommonConstants.NAME.FILESPLIT + "ZSE01");
                    //操作开始时间
                    BigDecimal start = new BigDecimal(System.currentTimeMillis());
                    String operaState = "";// 操作状态
                    String sendState = "";// 流转状态
                    //加密
                    try {
                        createPackService.encryptZip(filePath, publickey, "ZSE01");
                        operaState = CommonConstants.OPERATESTATE.JIAM;
                        //删除原没加密压缩包
                        PackUtil.deleteZip(filePath);
                    } catch (EncryptException e) {
                        sendState = RecvSendStateEnum.FAIL.getStateCode();
                        //将异常带出去处理
                        map.put("flag", false);
                        logger.error(ExceptionState.ENCRYPT.getCode() + ExceptionState.ENCRYPT.getName() + filePath);
                    }
                    try {
                        //操作结束时间
                        BigDecimal end = new BigDecimal(System.currentTimeMillis());
                        //计算耗时
                        BigDecimal spendTime = end.subtract(start).divide(new BigDecimal(1000), 0, BigDecimal.ROUND_UP);
                        String finalOperaState = operaState;
                        String finalSendState = sendState;
                        toNodeId.forEach(s1 -> {
                            //循环更新数据
                            map2.put("NODE_ID", String.valueOf(map.get("CUR_NODE_ID")));
                            map2.put("OPERATE_STATE_DM", finalOperaState);
                            map2.put("SEND_STATE_DM", finalSendState);
                            map2.put("PACKAGE_ID", packageId);
                            map2.put("TO_NODE_ID", s1);
                            map2.put("SPEND_TIME",spendTime);
                            upStatusService.updateCurState(map2);
                        });
                        s.put("FILE_PATH", parentDir + CommonConstants.NAME.FILESPLIT + "ZSE01" + CommonConstants.NAME.FILESPLIT + packageId);
                        s.put("TO_NODE_ID", toNodeId.get(0));
                        s.put("NODE_ID", String.valueOf(map.get("CUR_NODE_ID")));
                        //构造传给下个节点的参数
                        param1.add(s);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        //执行成功一次计数器减一
                        countDownLatch.countDown();
                    }

                }
                if (CommonConstants.STATE.CA.equals(MARK)) {

                    //CA预留位置
                }
            }).start();
        }
//        param.forEach(s -> {
//        });
        //如果上边俩线程没执行完就要等待其执行
        countDownLatch.await();
        map.put("PARAM", param1);
        if (map.get("flag") == null) {
            return SectionResult(true, map);
        } else {
            return SectionResult((boolean) map.get("flag"), map);
        }

    }

    private SectionResult SectionResult(boolean b, ConcurrentHashMap<String, Object> result) {
        // TODO Auto-generated method stub
        SectionResult sectionResult = new SectionResult(b);
        sectionResult.setMap(result);
        return sectionResult;
    }

}
