package com.iflytek.dep.admin.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.iflytek.dep.admin.dao.DepEtlJobRecordersMapper;
import com.iflytek.dep.admin.model.DepEtlJobRecorders;
import com.iflytek.dep.admin.model.dto.DepEtlJobRecordersDto;
import com.iflytek.dep.admin.service.DepEtlJobRecordersService;
import com.iflytek.dep.common.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DepEtlJobRecordersServiceImpl implements DepEtlJobRecordersService {
	private static Logger logger = LoggerFactory.getLogger(DepEtlJobRecordersServiceImpl.class);

	@Autowired
	DepEtlJobRecordersMapper depEtlJobRecordersMapper;

	@Override
	public Map<String, Object> getOneJobStatusNumber(String jobId) {
		Map result = new HashMap();
		DepEtlJobRecorders extraErrorNumber = new DepEtlJobRecorders();
		extraErrorNumber.setJobStatus(new BigDecimal(2));
		extraErrorNumber.setJobId(jobId);
		result.put("extraErrorNumber",depEtlJobRecordersMapper.selectStatusNumber(extraErrorNumber));


		DepEtlJobRecorders thisWeekTotalNumber = new DepEtlJobRecorders();
		result.put("totalNumber",depEtlJobRecordersMapper.selectStatusNumber(thisWeekTotalNumber));

		return result;
	}

	@Override
	public Map<String, Object> getJobStatusNumber() {
		Map result = new HashMap();
		DepEtlJobRecorders extraErrorNumber = new DepEtlJobRecorders();
		extraErrorNumber.setJobStatus(new BigDecimal(2));
		extraErrorNumber.setJobType(new BigDecimal(0));
		result.put("extraErrorNumber",depEtlJobRecordersMapper.selectStatusNumber(extraErrorNumber));

		DepEtlJobRecorders inputErrorNumber = new DepEtlJobRecorders();
		inputErrorNumber.setJobStatus(new BigDecimal(2));
		inputErrorNumber.setJobType(new BigDecimal(1));
		result.put("inputErrorNumber",depEtlJobRecordersMapper.selectStatusNumber(inputErrorNumber));

		DepEtlJobRecorders thisWeekTotalNumber = new DepEtlJobRecorders();
		thisWeekTotalNumber.setStartTime(DateUtils.getBeginDayOfWeek());

		thisWeekTotalNumber.setEndTime(DateUtils.getEndDayOfWeek());
		result.put("thisWeekTotalNumber",depEtlJobRecordersMapper.selectStatusNumber(thisWeekTotalNumber));

		return result;
	}

	@Override
	public PageInfo<DepEtlJobRecorders> getEtlJobRecorders(DepEtlJobRecordersDto param) {
		PageHelper.startPage(param.getCurrentPageNo(), param.getPageSize());
		List<DepEtlJobRecorders> result = depEtlJobRecordersMapper.getAllListByParam(param);
		PageInfo<DepEtlJobRecorders> pageInfo = new PageInfo<>(result);
		return pageInfo;
	}

	@Override
	public PageInfo<DepEtlJobRecorders> getEtlOneJobRecorders(DepEtlJobRecordersDto param) {
		PageHelper.startPage(param.getCurrentPageNo(), param.getPageSize());
		List<DepEtlJobRecorders> result = depEtlJobRecordersMapper.getOneJobListByParam(param);
		PageInfo<DepEtlJobRecorders> pageInfo = new PageInfo<>(result);
		return pageInfo;
	}
}
