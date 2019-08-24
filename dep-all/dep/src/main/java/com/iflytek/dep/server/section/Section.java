package com.iflytek.dep.server.section;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定义抽象任务节
 *
 * @author Kevin
 */
public interface Section {

    public SectionResult doAct(ConcurrentHashMap<String, Object> map) throws Exception;

    public SectionResult doThreadAct(final String packageId,final String jobId, SectionNode sectionNode, BigDecimal totalSectionNumber ,ConcurrentHashMap<String, Object> map) throws Exception;



}
