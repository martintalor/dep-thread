package com.iflytek.dep.server.service.dataPack;

import com.iflytek.dep.common.exception.BusinessErrorException;
import com.iflytek.dep.common.utils.DateUtils;
import com.iflytek.dep.server.ftp.core.FtpClientTemplate;
import com.iflytek.dep.server.mapper.NodeAppBeanMapper;
import com.iflytek.dep.server.mapper.NodeLinkBeanMapper;
import com.iflytek.dep.server.model.FTPConfig;
import com.iflytek.dep.server.model.NodeAppBean;
import com.iflytek.dep.server.model.NodeLinkBean;
import com.iflytek.dep.server.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.apache.commons.net.ftp.FTPClient;

import java.io.File;
import java.sql.SQLException;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author 姚伟-weiyao2
 * @version V1.0
 * @Package com.iflytek.dep.server.service.dataPack
 * @Description: ack生成及发送
 * @date 2019/3/20
 */
@Service
public class SendAckService {

    private static Logger logger = LoggerFactory.getLogger(SendAckService.class);


    @Autowired
    Environment environment;

    @Autowired
    UpStatusService upStatusService;

    @Autowired
    NodeLinkBeanMapper nodeLinkBeanMapper;

    @Autowired
    NodeAppBeanMapper nodeAppBeanMapper;



    /**
     *@描述 生成ack
     * 如果ack在当前区域内则不生成ack
     *@参数  [map]
     *@返回值  void
     *@创建人  姚伟-weiyao2
     *@创建时间  2019/3/8
     *@修改人和其它信息
     */
    @Async("ackAsyncServiceExecutor")
    public void createUpAck(ConcurrentHashMap<String, Object> map) {

        String ackId = "";// 数据包名
        try {
            ackId = (String) map.get("PACKAGE_ID");// 数据包名

            logger.info(Thread.currentThread().getName() + " ACK包生成、上传开始！pkgId...{}",ackId);

            // 生成上传ack
            createUpAckCatch(map);

            logger.info(Thread.currentThread().getName() + " ACK包生成、上传成功！pkgId...{}",ackId);
        } catch (Exception e) {
            logger.error("ACK包生成、上传失败！ackId...{},ERROR{}",ackId,e);
        }

    }

    /**
     *@描述 生成ack，3次重试
     *@参数  [map]
     *@返回值  void
     *@创建人  姚伟-weiyao2
     *@创建时间  2019/4/15
     *@修改人和其它信息
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 5000l, multiplier = 2))
    public void createUpAckCatch(ConcurrentHashMap<String, Object> map) throws Exception {
        // ACK_fromId_toId_packageId
        // fromId = 当前NODE_ID

        String packageId = (String) map.get("PACKAGE_ID");// 数据包名
        String nodeId = (String) map.get("NODE_ID");// 待更新状态的节点
        String sendStateDm = (String) map.get("SEND_STATE_DM");// 流转状态
        String operateStateDm = (String) map.get("OPERATE_STATE_DM");// 操作状态
        String toNodeId = (String) map.get("TO_NODE_ID");// 目标节点
        Date createTime = (Date) map.get("CREATE_TIME");// 创建时间
        Date updateTime = (Date) map.get("UPDATE_TIME");// 修改时间
        String processId = (String) map.get("PROCESS_ID");// 进程id
        String curNodeId = FileConfigUtil.CURNODEID;// 当前节点
        String ackName = "";

        String appIdFrom = PackUtil.splitAppFrom(packageId);// 源节点
        NodeAppBean nodeAppBean = nodeAppBeanMapper.selectByPrimaryKey(appIdFrom);

        //  1.如果是源节点并且，不生成ACk
        if ( curNodeId.equals( nodeAppBean.getNodeId() )  ) {
            logger.debug("源节点，不生成上传ACK！！nodeId：" + nodeId);
            return;
        }

        // 防止两条下划线之间有空数据
        if ( StringUtil.isEmpty(sendStateDm) ) {
            sendStateDm = "99";
        }

        if ( StringUtil.isEmpty(operateStateDm) ) {
            operateStateDm = "99";
        }

        Date curDate = new Date();// 当前时间
        createTime = createTime == null ? curDate : createTime;
        updateTime = updateTime == null ? curDate : updateTime;

        // 2、创建ackName
        String ackFix = CommonConstants.NAME.ACK_FIX;
        ackName = "ACK" + ackFix + nodeId
                + ackFix + toNodeId
                + ackFix + sendStateDm
                + ackFix + operateStateDm
                + ackFix + packageId
                + ackFix + createTime.getTime()
                + ackFix + updateTime.getTime()
                + ackFix + processId
                + ackFix + DateUtils.getTodaySN();// 年月日

//        logger.info( ackName );


        // 3、获取ftp
        // 反向发送,根据packageId、toNodeId，将nodeId作为rightNodeId，反向查询要上传的FTP
        String mainPackageId = packageId.split("\\.")[0] + CommonConstants.NAME.ZIP;
        NodeLinkBean linkBean = new NodeLinkBean();
        linkBean.setToNodeId(toNodeId);
        linkBean.setPackageId(mainPackageId);
        linkBean.setRightNodeId(curNodeId);
        linkBean = nodeLinkBeanMapper.getLinkByRightNode(linkBean);
        if (linkBean == null) {
            logger.error("ack没找到返回链路！ackId:" + ackName);
            return;
        }
        String nextNodeId = linkBean.getLeftNodeId();

        // 根据nodeId查找FtpClientTemplate
        FtpClientTemplate ftpClientTemplate = FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(nextNodeId);
        FTPConfig config = FtpClientTemplate.FTP_CONFIG.get(nextNodeId);
        if ( !ftpClientTemplate.getFtpClientConfig().getHost().equals(config.getFtpIp()) ) {
            logger.error("FTP连接有误！");
            return;
        }
        // 单通网闸不做ack返回
        String netBrakeType = config.getNetBrakeType();
        if (!StringUtils.isEmpty(netBrakeType) && netBrakeType.equals(CommonConstants.NET_BRAKE_TYPE.SINGLE_NET_BRAKE)) {
            return;
        }
        // 4、创建ack包并得到待上传文件夹
        // FTP的上传路径 叶子节点："/up/ack/"；中心节点“/down/ack/”;
//        String upDir = config.getAckPackageFolderUp() + DateUtils.getTodaySN() ;
        // FTP只能监听根目录，所以不能存在日期文件夹下
        String upDir = config.getAckPackageFolderUp()  ;
        String upPath = upDir + "/" + ackName ;

        // ack包存放路径
        String localDir =  FileConfigUtil.ACKDIR + File.separatorChar + DateUtils.getTodaySN() ;
        String localPath = localDir +  File.separatorChar + ackName;

        // 创建本地ACK文件夹
        FileUtil.mkdirs(localDir);
        // 创建本地ACK文件
        Boolean isFile = FileUtil.touchFile(localPath);

        if (!isFile) {
            logger.error("ACK包生成失败，localPath：" + localPath);
            return;
        }

        // 屏蔽调ack上传信息 by yaowei 20190404
        // 连接FTP上传文件到
//        logger.info("ACK包上传开始！上传文件：" + localPath);

        // 5、上传文件
        // 创建FTP远程的上传文件夹
//        ftpClientTemplate.makeDirectory(upDir);
        boolean bool = ftpClientTemplate.uploadFileReset(localPath,ackName, upDir);
//        boolean bool = ftpClientTemplate.uploadFile( new File(localPath), upPath);

        // 6、提示上传成功、失败
        if ( bool ) {
            logger.info("ACK包上传成功！upDir{},ackName:{}" , upDir, ackName);
        } else {
            logger.error("ACK包上传失败！本地文件路径：" + localPath);
        }

    }


    /**
     *@描述 调用parseAckCatch方法，做catch处理，ack失败不影响主业务流程
     *@参数  [map]
     *@返回值  java.util.concurrent.ConcurrentHashMap<java.lang.String,java.lang.Object>
     *@创建人  姚伟-weiyao2
     *@创建时间  2019/3/11
     *@修改人和其它信息
     */
    @Async("parseAckAsyncServiceExecutor")
    public void parseAck(ConcurrentHashMap<String, Object> map) {

        ConcurrentHashMap<String, Object> resultMap = new ConcurrentHashMap<String, Object>();
        String ackId = "";
        String ftpNodeId = "";
        try {
            ackId = (String) map.get("PACKAGE_ID");// 监听ack包名

            logger.info(Thread.currentThread().getName() + " ACK包解析开始！ackId...{}",ackId);

            ftpNodeId = (String)map.get("NODE_ID");// 当前FTP
            parseAckCatch(map);// 解析ack

            logger.info(Thread.currentThread().getName() + " ACK包解析完成！ackId...{}",ackId);

            moveAck(ackId,ftpNodeId);// 解析成功后移除ack

            logger.info(Thread.currentThread().getName() + " ACK包move结束！ackId...{}",ackId);
        } catch (Exception e) {
            logger.info(Thread.currentThread().getName() + "ACK包解析失败！ ackid{}, ftpNodeId{}, ERROR{}",ackId,ftpNodeId,e);
        }

    }

    /**
     *@描述 解析ack包含义,入库数据包状态
     *     解析ack包，调用upStatusByAckService.updateCurState
     *@参数  [map]
     *@返回值  java.util.concurrent.ConcurrentHashMap<java.lang.String,java.lang.Object>
     *@创建人  姚伟-weiyao2
     *@创建时间  2019/3/11
     *@修改人和其它信息
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 5000l, multiplier = 2))
    public void parseAckCatch(ConcurrentHashMap<String, Object> map) throws Exception {

        ConcurrentHashMap<String, Object> resultMap = new ConcurrentHashMap<String, Object>();
        String ackId = (String) map.get("PACKAGE_ID");// 监听ack包名
//        String nodeId = (String)map.get("NODE_ID");// 当前FTP

        String[] ackIds = ackId.split(CommonConstants.NAME.ACK_FIX);

        if (ackIds.length < 10) {
           throw new BusinessErrorException("ack包格式不正确，无法解析ackId：{}", ackId);
        }

        String nodeId = ackIds[1];//
        String toNodeId = ackIds[2];// 目标节点
        String sendStateDm = ackIds[3];// 流转状态
        String operateStateDm = ackIds[4];// 操作状态
        String packageId = ackIds[5];
        String createTime = ackIds[6];// 创建时间
        String updateTime = ackIds[7];// 修改时间
        String processId = ackIds[8];// 进程id
        String dateSN = ackIds[9];// 更新日期

        //            date = DateUtils.dateParse(updateTime,DateUtils.DATE_PATTERN_D);
        Date createDate = new Date( Long.valueOf(createTime));
        Date updateDate = new Date( Long.valueOf(updateTime));

        if ("99".equals(sendStateDm)) {
            sendStateDm = "";
        }

        if ("99".equals(operateStateDm)) {
            operateStateDm = "";
        }
        map.put("ACK_FLAG", "TRUE");// ack包修改状态标记
        map.put("ACK_ID", ackId);// ack包ID
        map.put("PACKAGE_ID", packageId);
        map.put("NODE_ID", nodeId);
        map.put("TO_NODE_ID", toNodeId);
        map.put("SEND_STATE_DM", sendStateDm);
        map.put("OPERATE_STATE_DM", operateStateDm);
        map.put("CREATE_TIME", createDate );// 创建时间
        map.put("UPDATE_TIME", updateDate );// 修改时间
        map.put("PROCESS_ID", processId );// 进程节点

        // 根据ack带回状态修改
        upStatusService.updateAckState(map);

        // 如果是非起始节点，则需要一直上传
//        createUpAck(map);

        // 解析之后移走
    }

    public void moveAck(FTPClient ftpClient, String ackId, String ftpNodeId) {

        ConcurrentHashMap<String, Object> resultMap = new ConcurrentHashMap<String, Object>();
        try {
            FtpClientTemplate ftpClientTemplate = FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(ftpNodeId);
            FTPConfig config = FtpClientTemplate.FTP_CONFIG.get(ftpNodeId);

            // ack备份目录
            String ackBack = environment.getProperty("ack.back.path") + "/" + FileConfigUtil.CURNODEID + "/";

            // ack监听目录
            String upDir = config.getAckPackageFolderDown();

            // ack解析后放到别的文件夹下
            String ackMoveDir = ackBack + DateUtils.getTodaySN() ;

            // 移动ack包路径
            ftpClientTemplate.moveFile(ftpClient, upDir + ackId, ackMoveDir, ackId);

        } catch (Exception e) {
            logger.error("ACK包转移失败！{}",e);
        }

    }

    /**
     *@描述 移除ACK
     *@参数  [ackId]
     *@参数  [ftpNodeId]
     *@返回值  java.util.concurrent.ConcurrentHashMap<java.lang.String,java.lang.Object>
     *@创建人  姚伟-weiyao2
     *@创建时间  2019/3/21
     *@修改人和其它信息
     */
    public void moveAck(String ackId, String ftpNodeId) {

        ConcurrentHashMap<String, Object> resultMap = new ConcurrentHashMap<String, Object>();
        try {
            FtpClientTemplate ftpClientTemplate = FtpClientTemplate.FTP_CLIENT_TEMPLATE.get(ftpNodeId);
            FTPConfig config = FtpClientTemplate.FTP_CONFIG.get(ftpNodeId);

            // ack备份目录
            String ackBack = environment.getProperty("ack.back.path") + "/" + FileConfigUtil.CURNODEID + "/";

            // ack监听目录
            String upDir = config.getAckPackageFolderDown();

            // ack解析后放到别的文件夹下
            String ackMoveDir = ackBack + DateUtils.getTodaySN() ;

            // 移动ack包路径
            ftpClientTemplate.moveFile(upDir + ackId, ackMoveDir, ackId);

        } catch (Exception e) {
            logger.error("ACK包转移失败！{}",e);
        }

    }


}
