package com.iflytek.dep.admin.service.impl;

import cn.hutool.core.convert.impl.DateConverter;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.iflytek.dep.admin.constants.GlobalSendStateEnum;
import com.iflytek.dep.admin.dao.DataPackageSubMapper;
import com.iflytek.dep.admin.dao.MachineNodeMapper;
import com.iflytek.dep.admin.dao.MonitorDisplayMapper;
import com.iflytek.dep.admin.model.MachineNode;
import com.iflytek.dep.admin.model.dto.DataPackageDto;
import com.iflytek.dep.admin.model.dto.monitor.DataPackTransStatDto;
import com.iflytek.dep.admin.model.dto.monitor.HealthDto;
import com.iflytek.dep.admin.model.dto.monitor.RightChartDto;
import com.iflytek.dep.admin.model.dto.monitor.TrendStatDataDto;
import com.iflytek.dep.admin.model.vo.DataPackageVo;
import com.iflytek.dep.admin.model.vo.PackageVo;
import com.iflytek.dep.admin.model.vo.PageVo;
import com.iflytek.dep.admin.model.vo.monitor.*;
import com.iflytek.dep.admin.service.MonitorDisplayService;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author xiliu5
 * @version V1.0
 * @Package com.iflytek.dep.admin.service.impl
 * @Description:
 * @date 2019/2/28
 */
@Service
public class MonitorDisplayServiceImpl implements MonitorDisplayService {

    Logger logger = LoggerFactory.getLogger(MonitorDisplayServiceImpl.class);

    private static final String SEND_FAIL_TODAY = "SEND_FAIL_TODAY";
    private static final String SEND_FAIL_YESTERDAY = "SEND_FAIL_YESTERDAY";
    private static final String SEND_FAIL_LAST_WEEK = "SEND_FAIL_LAST_WEEK";
    private static final String RECV_TODAY = "RECV_TODAY";
    private static final String SUCC_TODAY = "SUCC_TODAY";
    private static final String SUCC_YESTERDAY = "SUCC_YESTERDAY";
    private static final String SUCC_LAST_WEEK = "SUCC_LAST_WEEK";

    private static final String STAT_COUNT = "STAT_COUNT";
    private static final String STAT_SUM = "STAT_SUM";

    @Autowired
    private MonitorDisplayMapper monitorDisplayMapper;

    @Autowired
    private MachineNodeMapper machineNodeMapper;

    @Autowired
    private DataPackageSubMapper dataPackageSubMapper;

    @Override
    public MonitorDisplayPackStatVo getPackStatResult(DataPackTransStatDto monitorViewDto) {
        MonitorDisplayPackStatVo monitorDisplayPackStatVo = new MonitorDisplayPackStatVo();

        List<Map<String, Object>> packStatMapList = monitorDisplayMapper.getPackStatResult(monitorViewDto);

        Map<String, Object> sendFailTodayMap = new HashMap<>();
        Map<String, Object> sendFailYesterdayMap = new HashMap<>();
        Map<String, Object> sendFailLastWeekMap = new HashMap<>();
        Map<String, Object> recvTodayMap = new HashMap<>();
        Map<String, Object> succTodayMap = new HashMap<>();
        Map<String, Object> succYesterdayMap = new HashMap<>();
        Map<String, Object> succLastWeekMap = new HashMap<>();

        for (Map<String, Object> aMap : packStatMapList) {
            String STAT_TYPE = (String) aMap.get("STAT_TYPE");
            switch (STAT_TYPE) {
                case SEND_FAIL_TODAY:
                    sendFailTodayMap = aMap;
                    break;
                case SEND_FAIL_YESTERDAY:
                    sendFailYesterdayMap = aMap;
                    break;
                case SEND_FAIL_LAST_WEEK:
                    sendFailLastWeekMap = aMap;
                    break;
                case RECV_TODAY:
                    recvTodayMap = aMap;
                    break;
                case SUCC_TODAY:
                    succTodayMap = aMap;
                    break;
                case SUCC_YESTERDAY:
                    succYesterdayMap = aMap;
                    break;
                case SUCC_LAST_WEEK:
                    succLastWeekMap = aMap;
                    break;
                default:
                    break;
            }
        }

        //1.统计今天发送数量、统计今天发送流量
        Integer sendNumToday = ((BigDecimal) sendFailTodayMap.get(STAT_COUNT)).intValue() + ((BigDecimal)succTodayMap.get(STAT_COUNT)).intValue();
        monitorDisplayPackStatVo.setSendNumToday(sendNumToday);
        monitorDisplayPackStatVo.setSendSizeToday(((BigDecimal) sendFailTodayMap.get(STAT_SUM)).add((BigDecimal)succTodayMap.get(STAT_SUM)));

        //2.统计发送数量的日环比
        Integer sendNumYesterday = ((BigDecimal) sendFailYesterdayMap.get(STAT_COUNT)).intValue() + ((BigDecimal)succYesterdayMap.get(STAT_COUNT)).intValue();
        monitorDisplayPackStatVo.setSendNumCompareYesterdayPrecent(sendNumYesterday==0 ? 0 : Double.valueOf(sendNumToday-sendNumYesterday)/sendNumYesterday*100);

        //3.统计发送数量的周同比
        Integer sendNumLastWeek = ((BigDecimal) sendFailLastWeekMap.get(STAT_COUNT)).intValue() + ((BigDecimal) succLastWeekMap.get(STAT_COUNT)).intValue();
        monitorDisplayPackStatVo.setSendNumCompareWeekPercent(sendNumLastWeek==0 ? 0 : Double.valueOf(sendNumToday-sendNumLastWeek)/sendNumLastWeek*100);

        //4.统计今天接收数量、今天接收流量
        monitorDisplayPackStatVo.setRecvNumToday(((BigDecimal) recvTodayMap.get(STAT_COUNT)).intValue());
        monitorDisplayPackStatVo.setRecvSizeToday(((BigDecimal) recvTodayMap.get(STAT_SUM)));

        //5.统计今天发送成功率、发送成功率的周同比、发送成功率的日环比
        Integer succTodayNum = ((BigDecimal) succTodayMap.get(STAT_COUNT)).intValue();
        Integer succYesterdayNum = ((BigDecimal) succYesterdayMap.get(STAT_COUNT)).intValue();
        Integer succLastWeekNum = ((BigDecimal) succLastWeekMap.get(STAT_COUNT)).intValue();
        Double sendSuccTodayPercent = sendNumToday==0 ? 0 : Double.valueOf(succTodayNum) / sendNumToday;
        Double sendSuccYesterdayPercent = sendNumYesterday==0 ? 0 : Double.valueOf(succYesterdayNum) / sendNumYesterday;
        Double sendSuccWeekPercent = sendNumLastWeek==0 ? 0 : Double.valueOf(succLastWeekNum) / sendNumLastWeek;
        monitorDisplayPackStatVo.setSendSuccPercent(sendSuccTodayPercent);
        monitorDisplayPackStatVo.setSendSuccCompareYesterdayPercent(sendSuccYesterdayPercent==0 ? 0 : Double.valueOf(sendSuccTodayPercent-sendSuccYesterdayPercent)/sendSuccYesterdayPercent*100);
        monitorDisplayPackStatVo.setSendSuccCompareWeekPercent(sendSuccWeekPercent==0 ? 0 : Double.valueOf(sendSuccTodayPercent-sendSuccWeekPercent)/sendSuccWeekPercent*100);

        return monitorDisplayPackStatVo;
    }

    @Override
    public PageVo<MonitorDisplayFrontHealthVo> getFrontEndHealthList(HealthDto monitorViewDto) {
        PageHelper.startPage(monitorViewDto.getCurrentPageNo(), monitorViewDto.getPageSize());
        List<MonitorDisplayFrontHealthVo> healthVoList = monitorDisplayMapper.getFrontEndHealthList(monitorViewDto);
        PageInfo<MonitorDisplayFrontHealthVo> pageInfo = new PageInfo<>(healthVoList);

        List<MonitorDisplayFrontHealthVo> resultList = pageInfo.getList();

        final int threadNum = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = new ThreadPoolExecutor(threadNum, threadNum, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        CountDownLatch countDownLatch = new CountDownLatch(resultList.size());
        resultList.forEach(frontHealthVo -> executorService.execute(() -> {
            try {
                MonitorDisplayFrontHealthVo tempVo = monitorDisplayMapper.getFrontEndHealthStatByNodeId(frontHealthVo.getNodeId());
                frontHealthVo.setSendNumFail(tempVo.getSendNumFail());
                frontHealthVo.setSendNumSucc(tempVo.getSendNumSucc());
                frontHealthVo.setSendSizeSending(tempVo.getSendSizeSending());
                frontHealthVo.setSendSizeSendSucc(tempVo.getSendSizeSendSucc());
                frontHealthVo.setSendSizeSendFail(tempVo.getSendSizeSendFail());
                frontHealthVo.setRecvSizeRecving(tempVo.getRecvSizeRecving());
                frontHealthVo.setRecvSizeRecvSucc(tempVo.getRecvSizeRecvSucc());
            } catch (Exception e) {
                logger.error("内部中断异常. ERROR_INFO:{}",e);
            } finally {
                countDownLatch.countDown();
            }
        }));

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            logger.error("内部中断异常. ERROR_INFO:{}",e);
            Thread.currentThread().interrupt();
        } finally {
            executorService.shutdown();
        }

        return new PageVo(monitorViewDto.getCurrentPageNo(), monitorViewDto.getPageSize(), (int) pageInfo.getTotal(), resultList);
    }

    @Override
    public Map<String, List<MonitorDisplayTrendVo>> getTrendStatData(TrendStatDataDto monitorViewDto) {
        Map<String, List<MonitorDisplayTrendVo>> resultMap = new HashMap<>();

        if (!StringUtils.isEmpty(monitorViewDto.getStartTime()) && !StringUtils.isEmpty(monitorViewDto.getEndTime())) {//按时期段筛选 格式 yyyy-MM-dd
            DateTime startDate = DateUtil.parse(monitorViewDto.getStartTime());
            DateTime endDate = DateUtil.parse(monitorViewDto.getEndTime());
            long days = (endDate.getTime() - startDate.getTime()) / (1000 * 3600 * 24);
            monitorViewDto.setDays(days);
            monitorViewDto.setTrendDateType(null);
        } else {
            monitorViewDto.setStartTime(null);
            monitorViewDto.setEndTime(null);
        }

        monitorViewDto.setSuccOrFail("succ");
        List<MonitorDisplayTrendVo> succList = monitorDisplayMapper.getTrendStatData(monitorViewDto);
        resultMap.put("succ", succList);

        if (monitorViewDto.getSendOrRecv().equals("send")) {//发送会有失败的情况，接收要么成功要么没收到，本节点不会出现接收失败的情况
            monitorViewDto.setSuccOrFail("fail");
            List<MonitorDisplayTrendVo> failList = monitorDisplayMapper.getTrendStatDataForSendFail(monitorViewDto);
            resultMap.put("fail", failList);
        }

        return resultMap;
    }

    @Override
    public List<MonitorDisplayTrendVo> getTransFlowList(RightChartDto monitorViewDto) {
        return monitorDisplayMapper.getTransFlowList(monitorViewDto);
    }

    @Override
    public Double getServerNormalPercent(RightChartDto monitorViewDto) {
        List<MonitorDisplayProbeResultVo> depServerProbeList = monitorDisplayMapper.getDepServerProbeResult(monitorViewDto);

        Integer cntNormal = 0;
        Integer cntNotNormal = 0;

        for (MonitorDisplayProbeResultVo probeResultVo : depServerProbeList) {
            if (probeResultVo.getProbeResult().equals("Y")) {
                cntNormal++;
            } else {
                cntNotNormal++;
            }
        }

        return (cntNormal+cntNotNormal)==0 ? 0 : Double.valueOf(cntNormal)/(cntNormal+cntNotNormal)*100;
    }

    @Override
    public List<Map<String, Object>> getPackStatResultForAllBranch(DataPackTransStatDto monitorViewDto) {
        return monitorDisplayMapper.getPackStatResultForAllBranch(monitorViewDto);
    }

    @Override
    public Map<String, Object> getTrendStatDataForCenter(TrendStatDataDto monitorViewDto) {
        Map<String, Object> resultMap = new HashMap<>();

        if (!StringUtils.isEmpty(monitorViewDto.getStartTime()) && !StringUtils.isEmpty(monitorViewDto.getEndTime())) {//按时期段筛选 格式 yyyy-MM-dd
            DateTime startDate = DateUtil.parse(monitorViewDto.getStartTime());
            DateTime endDate = DateUtil.parse(monitorViewDto.getEndTime());
            long days = (endDate.getTime() - startDate.getTime()) / (1000 * 3600 * 24);
            monitorViewDto.setDays(days);
            monitorViewDto.setTrendDateType(null);
        } else {
            monitorViewDto.setStartTime(null);
            monitorViewDto.setEndTime(null);
        }

        monitorViewDto.setSuccOrFail("succ");
        List<MonitorDisplayTrendVo> succList = monitorDisplayMapper.getTrendStatData(monitorViewDto);
        resultMap.put("succ", succList);

        if (monitorViewDto.getSendOrRecv().equals("send")) {//发送会有失败的情况，接收要么成功要么没收到，本节点不会出现接收失败的情况
            monitorViewDto.setSuccOrFail("fail");
            List<MonitorDisplayTrendVo> failList = monitorDisplayMapper.getTrendStatDataForSendFail(monitorViewDto);
            resultMap.put("fail", failList);
        }

        //查询数据包数量排名
        List<Map<String, Object>> topList = new ArrayList<>();
        if (monitorViewDto.getSendOrRecv().equals("send")) {
            topList = monitorDisplayMapper.getSendTopForCenter(monitorViewDto);
        } else {
            topList = monitorDisplayMapper.getRecvTopForCenter(monitorViewDto);
        }

        resultMap.put("topList", topList);

        return resultMap;
    }

    @Override
    public List<Map<String, Object>> getPackStatResultForCenter(DataPackTransStatDto monitorViewDto) {
        return monitorDisplayMapper.getPackStatResultForCenter(monitorViewDto);
    }

    @Override
    public List<Map<String, Object>> getTransTimeResult(DataPackTransStatDto monitorViewDto) {
        //统计传输耗时
        List<Map<String, Object>> transTimeMapList = monitorDisplayMapper.getTransTimeResult(monitorViewDto);

        int transTotal = 0;
        for (Map<String, Object> map : transTimeMapList) {
            transTotal += ((BigDecimal)map.get(STAT_COUNT)).intValue();
        }

        //计算百分比
        for (Map<String, Object> map : transTimeMapList) {
            if (transTotal > 0) {
                map.put("percent", ((BigDecimal) map.get(STAT_COUNT)).doubleValue()/transTotal*100);
            } else {
                map.put("percent", 0);
            }
        }

        return transTimeMapList;
    }

    @Override
    public List<MonitorDisplayPackStateVo> packStateList(DataPackageDto dataPackageDto) {
        return monitorDisplayMapper.packStateList(dataPackageDto);

    }

    @Override
    public List<MachineNode> listNodeByServerNodeId(String serverNodeId) {
        return machineNodeMapper.listNodeByServerNodeId(serverNodeId);
    }

    @Override
    public PageVo<DataPackageVo> listDataPackageForCenter(DataPackageDto dataPackageDto) {
        PageHelper.startPage(dataPackageDto.getCurrentPageNo(), dataPackageDto.getPageSize());
        List<DataPackageVo> dataPackageVos = monitorDisplayMapper.selectAllForMonitorCenter(dataPackageDto);
        if (!CollectionUtils.isEmpty(dataPackageVos)) {
            for (DataPackageVo dto : dataPackageVos) {
                if (null != dto.getGlobalStateDm() && null != GlobalSendStateEnum.getByStateCode(dto.getGlobalStateDm())) {
                    dto.setGlobalStateMc(GlobalSendStateEnum.getByStateCode(dto.getGlobalStateDm()).getStateName());
                }
                //查看是否拆包 0未拆包 1拆包
                List<PackageVo> listPackageSub = dataPackageSubMapper.listPackageSub(dto.getPackageId());
                if (!CollectionUtils.isEmpty(listPackageSub)) {
                    dto.setIsUnpack(1);
                }
            }
        }
        PageInfo<DataPackageVo> pageInfo = new PageInfo<>(dataPackageVos);
        return new PageVo(dataPackageDto.getCurrentPageNo(), dataPackageDto.getPageSize(), (int) pageInfo.getTotal(), pageInfo.getList());
    }

}