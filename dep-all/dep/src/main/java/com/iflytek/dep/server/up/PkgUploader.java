package com.iflytek.dep.server.up;

import com.iflytek.dep.server.mapper.SectionStepRecordersMapper;
import com.iflytek.dep.server.model.SectionStepRecorders;
import com.iflytek.dep.server.section.SectionNode;
import com.iflytek.dep.server.section.SectionResult;
import com.iflytek.dep.server.service.threadPool.DepServerAnalyCommonService;
import com.iflytek.dep.server.utils.LogSectionInfo;
import com.iflytek.dep.server.utils.SectionUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Section任务执行调度
 *
 * @author Kevin
 */
@Service
@Scope("singleton")
public class PkgUploader {
	private final static Logger logger = LoggerFactory.getLogger(DepServerAnalyCommonService.class);

	@Value("${core.section.thread.number:2}")
	private Integer threadNumber;
	@Autowired
	SectionUtils sectionUtils;
	@Autowired
	SectionStepRecordersMapper sectionStepRecordersMapper;

	class DepThreadFactory implements ThreadFactory {

		private int counter;
		private String name;

		public DepThreadFactory(String name) {
			counter = 0;
			this.name = name;
		}

		@Override
		public Thread newThread(Runnable run) {
			Thread t = new Thread(run, name + "-Thread-" + counter);
			counter++;
			return t;
		}
	}
	private ExecutorService fixedUpStreamThreadPool = Executors.newFixedThreadPool(1,
			new PkgUploader.DepThreadFactory("PkgUploader"));

	//当前上载任务数量,(相对于服务来说)
	private static AtomicInteger upJobNumber = new AtomicInteger(0);


	/**
	 * 数据包下载、数据包解密、数据包合并、数据包、加密、上传FTP
	 *
	 * @param jobId
	 */
	public void upload(final String jobId, SectionNode sectionNode, ConcurrentHashMap<String, Object> outParam) {

		final Future<?> fu = fixedUpStreamThreadPool.submit(new Runnable() {

			public void run() {
				List<SectionStepRecorders> sectionStepRecorderList = sectionStepRecordersMapper.getByPackageId(jobId);

				BigDecimal totalSectionNumber = SectionUtils.getTotalSectionLength(sectionNode);
				LogSectionInfo.loggerSectionChain(sectionNode);
				ConcurrentHashMap<String, Object> param = new ConcurrentHashMap();
				param.putAll(outParam);
				SectionNode sectionNodeInner = SectionUtils.checkSetionStep(sectionStepRecorderList, sectionNode, param);
				String packageId = String.valueOf(param.get("PACKAGE_ID"));
				logger.info("[{}] package id（from param） : {}",this.getClass(),packageId);
				try {
					if(sectionNodeInner != null) {
						//修改param传参为有PARAM信息的参数：outParam->param -- modify by jzkan 20190506
						sectionNodeInner.getCurrent().doThreadAct(jobId, jobId, sectionNodeInner, totalSectionNumber, param);
					} else {
						logger.info("package:{} is deal success,unwanted retry!",jobId);
					}
				}catch (Exception e){
					logger.error("[{}] happend error",this.getClass(),e);
				}
//				while (sectionNodeInner != null && sectionNodeInner.getCurrent() != null) {
//
//					for (String key : param.keySet()) {
//						logger.info("key= " + key + " and value= " + Arrays.asList(param.get(key)));
//					}
//					logger.info("do ack [{}] ,param: [{}]", sectionNodeInner.getCurrent().getClass(), ToStringBuilder.reflectionToString(param,
//							ToStringStyle.SHORT_PREFIX_STYLE));
//
//
//					try {
//
//						sectionUtils.insertSectionStepRecorders(param, new BigDecimal(0), sectionNodeInner.getCurrent().getClass() + "", jobId,totalSectionNumber,new BigDecimal(1));
//						SectionResult result = sectionNodeInner.getCurrent().doAct(param);
//						if (result == null || !result.isOk()) {
//							logger.error("do section {} happend error, result info {},", sectionNodeInner.getCurrent().getClass(), result);
//							return;
//						}
//						sectionUtils.insertSectionStepRecorders(result.getMap(), new BigDecimal(1), sectionNodeInner.getCurrent().getClass() + "", jobId,totalSectionNumber,new BigDecimal(1));
//
//						param = result.getMap();
//					} catch (Exception e) {
//						logger.error("doAct happend error............{}", e);
//						return;
//					}
//					sectionNodeInner = sectionNodeInner.getNext();
//				}
			}
		});
	}
}
