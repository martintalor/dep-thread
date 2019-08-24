package com.iflytek.dep.server.section;


import com.iflytek.dep.server.constants.PkgStatus;
import com.iflytek.dep.server.service.dataPack.SendPackService;
import com.iflytek.dep.server.utils.SectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 姚伟-weiyao2
 * @version V1.0
 * @Description: 数据包下载-叶子节点
 * @date 2019/2/22
 */
@Service
public class FileDownloadLeafSection extends BaseSectionThreadPool  implements Section, Status {
	private static Logger logger = LoggerFactory.getLogger(FileDownloadLeafSection.class);
	private static ExecutorService fixedUpStreamThreadPool;
	public static AtomicInteger threadJobSize = new AtomicInteger(0);
	@Autowired
	SendPackService sendPackService;
	@Autowired
	SectionUtils sectionUtils;
	public FileDownloadLeafSection() {
		synchronized (logger){
			if(fixedUpStreamThreadPool == null){
				fixedUpStreamThreadPool =  Executors.newFixedThreadPool(threadNumber,
						new FileDownloadLeafSection.DepThreadFactory("FileDownloadLeafSection"));
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

					sendPackService.downPackLeaf(map);

//					return SectionResult(true,map);
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
	public void update(String pkgId, PkgStatus status) {
		// update pkg status here
		
	}

	@Override
	public SectionResult doAct(ConcurrentHashMap<String, Object> map) throws Exception {

		sendPackService.downPackLeaf(map);

		return SectionResult(true,map);
	}

	private SectionResult SectionResult(boolean b, ConcurrentHashMap<String, Object> result) {
		// TODO Auto-generated method stub
		SectionResult sectionResult = new SectionResult(b);
		sectionResult.setMap(result);
		return sectionResult;
	}

}
