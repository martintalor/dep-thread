package com.iflytek.dep.server.section;

import com.google.gson.Gson;
import com.iflytek.dep.server.mapper.FTPConfigMapper;
import com.iflytek.dep.server.mapper.NodeAppBeanMapper;
import com.iflytek.dep.server.mapper.SectionStepRecordersMapper;
import com.iflytek.dep.server.model.NodeAppBean;
import com.iflytek.dep.server.service.dataPack.CreatePackService;
import com.iflytek.dep.server.service.threadPool.DepServerFtpFileBackService;
import com.iflytek.dep.server.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * @date 2019/4/17--17:00
 */
@Service
public class InToDatabaseSection   extends BaseSectionThreadPool implements Section {
    private static Logger logger = LoggerFactory.getLogger(InToDatabaseSection.class);
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

    @Autowired
    CreatePackService createPackService;
    public InToDatabaseSection() {
        synchronized (logger){
            if(fixedUpStreamThreadPool == null){
                fixedUpStreamThreadPool =  Executors.newFixedThreadPool(threadNumber,
                        new InToDatabaseSection.DepThreadFactory("InToDatabaseSection"));
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

                    //List<ConcurrentHashMap<String, Object>> param = (List<ConcurrentHashMap<String, Object>>) map.get("PARAM");
                    //Gson gson = new Gson();
                    //ConcurrentHashMap<String, Object> paramMap = gson.fromJson(gson.toJson(param.get(0)), ConcurrentHashMap.class);
                    //String packageId = String.valueOf(paramMap.get("PACKAGE_ID"));

                    try {
                        createPackService.notifyEtl(packageId);
                        sectionUtils.insertSectionStepRecorders(map, new BigDecimal(1), this.getClass() + "", jobId, totalSectionNumber,new BigDecimal(0));
                        SectionNode nextSectionNode = sectionNode.getNext();
                        if(nextSectionNode!= null){
                            nextSectionNode.getCurrent().doThreadAct(packageId,jobId,nextSectionNode,totalSectionNumber,map);
                        }
                    } catch (Exception e) {
                        result.setOk(false);
                        logger.error("--------etl接口调用失败", e);
                        e.printStackTrace();
                    }
//                    return result;

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

        List<ConcurrentHashMap<String, Object>> param = (List<ConcurrentHashMap<String, Object>>) map.get("PARAM");
        Gson gson = new Gson();
        ConcurrentHashMap<String, Object> paramMap = gson.fromJson(gson.toJson(param.get(0)), ConcurrentHashMap.class);
        String packageId = String.valueOf(paramMap.get("PACKAGE_ID"));

        try {
            createPackService.notifyEtl(packageId);
        } catch (Exception e) {
            result.setOk(false);
            logger.error("--------etl接口调用失败");
            e.printStackTrace();
        }
        return result;

    }

//    private boolean notifyEtl(String packageId) {
//        logger.info("------------------开始调用etl入库接口：{}",packageId);
//        String[] toApps = PackUtil.splitAppTo(packageId);
//        NodeAppBean nodeAppBean = null;
//        for (String itemApp : toApps) {
//            nodeAppBean = nodeAppBeanMapper.selectByPrimaryKey(itemApp);
//            if (FileConfigUtil.CURNODEID.equals(nodeAppBean.getNodeId())) {
//                break;
//            }
//        }
//        logger.info("----------------判断成功是某个nodeapp{}",nodeAppBean.getClass().toString());
//        try {
//            if (nodeAppBean != null) {
//                if (nodeAppBean.getCalUrl() != null) {
//                    logger.info("----------------------接口地址：{}",nodeAppBean.getCalUrl());
//                    logger.info("----------------------接口地址加参数：{}",nodeAppBean.getCalUrl() + environment.getProperty("packed.dir") +"/"+packageId.split("\\.")[0].replaceAll(CommonConstants.NAME.APPSPLIT,"") +"/unpack/" + packageId.split("\\.")[0].replaceAll(CommonConstants.NAME.APPSPLIT,""));
//                    ResponseBean forObject = restTemplate.getForObject(nodeAppBean.getCalUrl() + environment.getProperty("packed.dir") + "/" + packageId.split("\\.")[0].replaceAll(CommonConstants.NAME.APPSPLIT, "") + "/unpack/" + packageId.split("\\.")[0].replaceAll(CommonConstants.NAME.APPSPLIT, ""), ResponseBean.class);
//                }
//            }
//            logger.info("-------------------调用接口成功");
//            return true;
//        } catch (Exception e) {
//            logger.error("notify etl happend error [{}]，地址：{}", e,nodeAppBean.getCalUrl() + environment.getProperty("packed.dir") +"/"+packageId.split("\\.")[0].replaceAll(CommonConstants.NAME.APPSPLIT,"") +"/unpack/" + packageId.split("\\.")[0].replaceAll(CommonConstants.NAME.APPSPLIT,""));
//            return false;
//        }
//    }

}

