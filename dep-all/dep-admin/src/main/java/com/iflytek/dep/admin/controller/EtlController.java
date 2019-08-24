package com.iflytek.dep.admin.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.iflytek.dep.admin.model.DepEtlJobRecorders;
import com.iflytek.dep.admin.model.dto.DepEtlJobRecordersDto;
import com.iflytek.dep.admin.service.DepEtlJobRecordersService;
import com.iflytek.dep.common.utils.ResponseBean;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@Api(value = "ETL管理接口", tags = {"ETL管理接口"})
@RestController
@RequestMapping("/etl")
public class EtlController {
	@Autowired
	DepEtlJobRecordersService depEtlJobRecordersService;

	@ApiOperation(value = "job执行情况统计接口", notes = "job执行情况统计接口")
	@GetMapping("/getRunData")
	public ResponseBean<Map> getRunData(){
		ResponseBean<Map> result = new ResponseBean<>();
		result.setRows(depEtlJobRecordersService.getJobStatusNumber());
		return result;
	}

	@ApiOperation(value = "单个job执行情况统计接口", notes = "job执行情况统计接口")
	@GetMapping("/getOneJobRunData/{jobId}")
	public ResponseBean<Map> getOneJobRunData(@PathVariable("jobId") String jobId){
		ResponseBean<Map> result = new ResponseBean<>();
		result.setRows(depEtlJobRecordersService.getOneJobStatusNumber(jobId));
		return result;
	}


	@ApiOperation(value = "job执行详情接口", notes = "job执行详情接口")
	@PostMapping("/getRunRecorders")
	public ResponseBean<PageInfo<DepEtlJobRecorders>> getJobRecorders(@RequestBody DepEtlJobRecordersDto param){
		ResponseBean<PageInfo<DepEtlJobRecorders>> result = new ResponseBean<PageInfo<DepEtlJobRecorders>>();
		result.setRows(depEtlJobRecordersService.getEtlJobRecorders(param));
		return result;
	}
	@ApiOperation(value = "job ID执行详情接口", notes = "job ID执行详情接口")
	@PostMapping("/getJobRecorders")
	public ResponseBean<PageInfo<DepEtlJobRecorders>> getJobIdRecorders(@RequestBody DepEtlJobRecordersDto param){
		ResponseBean<PageInfo<DepEtlJobRecorders>> result = new ResponseBean<PageInfo<DepEtlJobRecorders>>();
		result.setRows(depEtlJobRecordersService.getEtlOneJobRecorders(param));
		return result;
	}
}
