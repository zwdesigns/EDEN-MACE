package com.stylefeng.guns.modular.dist.controller;

import com.stylefeng.guns.common.annotion.DataSource;
import com.stylefeng.guns.common.constant.DSEnum;
import com.stylefeng.guns.common.constant.dist.AccountTypeStatus;
import com.stylefeng.guns.common.constant.tips.DistResult;
import com.stylefeng.guns.common.exception.BizExceptionEnum;
import com.stylefeng.guns.common.exception.BussinessException;
import com.stylefeng.guns.common.persistence.dao.DisMemberAmountMapper;
import com.stylefeng.guns.common.persistence.dao.DisMemberInfoMapper;
import com.stylefeng.guns.common.persistence.model.DisMemberAmount;
import com.stylefeng.guns.common.persistence.model.DisMemberInfo;
import com.stylefeng.guns.common.persistence.model.User;
import com.stylefeng.guns.modular.dist.http.request.SubordinateReq;
import com.stylefeng.guns.modular.dist.http.response.SubordinateResp;
import com.stylefeng.guns.modular.dist.service.IDisMemberInfoService;
import com.stylefeng.guns.modular.dist.service.IDisProfitRecordService;
import com.stylefeng.guns.modular.dist.service.IDisWithdrawRecordService;
import com.stylefeng.guns.modular.dist.util.Jwt;
import com.stylefeng.guns.modular.dist.vo.DisMemberInfoVo;
import com.stylefeng.guns.modular.dist.vo.DisProfitRecordVo;
import com.stylefeng.guns.modular.dist.vo.DisWithdrawVo;
import com.stylefeng.guns.modular.system.dao.UserMgrDao;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统对外开放接口
 */
@Controller
@RequestMapping("/api/v1/")
public class OpenController  {

    @Resource
    DisMemberInfoMapper disMemberInfoMapper;

    @Resource
    DisMemberAmountMapper disMemberAmountMapper;

    @Autowired
    IDisMemberInfoService disMemberInfoService;

    @Resource
    UserMgrDao userMgrDao;

    @Autowired
    IDisProfitRecordService disProfitRecordService;

    @Autowired
    IDisWithdrawRecordService disWithdrawRecordService;


    @Value("${dist.jwt.secret}")
    private  String secret;
    @Value("${dist.jwt.account}")
    private  String account;
    @Value("${dist.jwt.isUse}")
    private  boolean jwtUse;


    /**
     * 用户登录获取相关用户信息
     * @param userId
     * @return
     */
    @GetMapping("/getUserInfo")
    @DataSource(name = DSEnum.DATA_SOURCE_BIZ)
    @ResponseBody
    public DistResult getUserInfo(String userId){

        DisMemberInfo memberInfoParam=new DisMemberInfo();
        memberInfoParam.setDisUserId(userId);
        DisMemberInfo  memberInfo=disMemberInfoMapper.selectOne(memberInfoParam);

        DisMemberAmount disMemberAmountParam=new DisMemberAmount();
        disMemberAmountParam.setDisUserId(userId);
        DisMemberAmount disMemberAmount=disMemberAmountMapper.selectOne(disMemberAmountParam);

        Map<String ,Object> map=new HashMap<>();
        if(memberInfo!=null){
            map.put("member",memberInfo);
            map.put("amount",disMemberAmount);
            DisMemberInfo sss= (DisMemberInfo) map.get("member");
            return DistResult.success(map);
        }else{
            return DistResult.failure("没有此用户");
        }
    }
    /**
     * 邀请会员，调用此接口
     */
    @PostMapping(value = "/memberAdd")
    @ResponseBody
    @ApiOperation(value = "新增会员", notes = "")
    public DistResult add(@RequestBody DisMemberInfoVo memberInfoVo) throws Exception {

        if(!isAccountVer(memberInfoVo.getSecret())){
            return DistResult.failure("非法访问");
        }
        String superPaltId="";
        if(StringUtils.isNotEmpty(memberInfoVo.getDisModelId())){
            DisMemberInfo param= disMemberInfoService.selectListByUserId(memberInfoVo.getDisModelId());
            if(param==null){
                return DistResult.failure("邀请用户不存在");
            }else {
                superPaltId=param.getDisPlatSuper();
            }
        }
        User user=userMgrDao.getByAccount(superPaltId);
        if(user==null){
            return DistResult.failure("代理商不存在");
        }

        DisMemberInfo param= disMemberInfoService.selectListByUserId(memberInfoVo.getDisUserId());
        if(param!=null){
            return DistResult.failure("用户已存在");
        }
        DisMemberInfo memberInfo=new DisMemberInfo();
        BeanUtils.copyProperties(memberInfoVo,memberInfo);
        memberInfo.setDisUserType("0");
        memberInfo.setDisPlatSuper(memberInfoVo.getDisPlatSuper());
        memberInfo.setDisPlatLevel(Integer.parseInt(user.getLevel()));
        memberInfo.setDisPlatFullIndex(user.getFullindex());
        memberInfo.setDisPlatformId(user.getFullindex().split("\\.")[1]);
        memberInfo.setType("0");
        disMemberInfoService.save(memberInfo);
        return DistResult.success();
    }

    /**
     * 会员交易后调用此接口
     */
    @PostMapping(value = "/trade")
    @ResponseBody
    @ApiOperation(value = "新增交易奖励", notes = "此接口是用于交易奖励的接口，及关注有相关分润的数据")
    public DistResult add(@RequestBody DisProfitRecordVo disProfitRecordVo) throws Exception {
        if(!isAccountVer(disProfitRecordVo.getSecret())) {
           return   DistResult.failure("非法访问！");
        }
        // 查询交易人是否存在
        DisMemberInfo memberInfo=disMemberInfoService.selectListByUserId(disProfitRecordVo.getDisSetUserId());
        if(memberInfo==null){
           return  DistResult.failure("用户不存在！");
        }
        disProfitRecordVo.setAccountType(AccountTypeStatus.ZERO_STATUS.getStatus());
        disProfitRecordVo.setDisPlatformId(memberInfo.getDisPlatformId());
        disProfitRecordService.save(disProfitRecordVo);
        return DistResult.success(disProfitRecordVo);
    }
    /**
     * 会员升级后调用此接口，相关联的数据会产生分润
     */
    @PostMapping(value = "/upgrade")
    @ResponseBody
    @ApiOperation(value = "升级奖励", notes = "此接口是用于升级奖励的接口")
    public DistResult upgrade(@RequestBody DisProfitRecordVo disProfitRecordVo) throws Exception {
        if(!isAccountVer(disProfitRecordVo.getSecret())) {
            return   DistResult.failure("非法访问！");
        }
        //根据直属上级查询到所属平台id
        DisMemberInfo memberInfo=disMemberInfoService.selectListByUserId(disProfitRecordVo.getDisSetUserId());
        if(memberInfo==null){
            DistResult.failure("用户不存在！");
        }
        if(disProfitRecordVo.getUpgradeLevel()==null){
            return DistResult.failure("请提供要升级的等级");
        }
        disProfitRecordVo.setDisPlatformId(memberInfo.getDisPlatformId());
        disProfitRecordVo.setAccountType(AccountTypeStatus.ONE_STATUS.getStatus());
        disProfitRecordService.save(disProfitRecordVo);

        memberInfo.setDisUserType(disProfitRecordVo.getUpgradeLevel());
        disMemberInfoService.updateLevel(memberInfo);
        return DistResult.success(disProfitRecordVo);
    }

    /**
     *  用户查询他发展的会员
     */
    @PostMapping(value = "/subordinate")
    @ResponseBody
    @ApiOperation(value = "会员直属下级会员", notes = "此接口是查询会员直属下级会员")
    public DistResult subordinate(@RequestBody  SubordinateReq subordinateReq) {
        if(!isAccountVer(subordinateReq.getSecret())) {
            return   DistResult.failure("非法访问！");
        }
        DisMemberInfo memberInfo=disMemberInfoService.selectListByUserId(subordinateReq.getMemberId());
        if(memberInfo==null){
            return DistResult.failure("用户不存在");
        }
        List<SubordinateResp> list = disMemberInfoService.getSubordinateInfo(subordinateReq);
        return DistResult.success(list);
    }

    /**
     * 用户提现，针对用户界面对某一账户进行提现
     * @param withdrawVo
     * @return
     */
    @PostMapping(value = "/withdraw")
    @ResponseBody
    @ApiOperation(value = "新增提现接口", notes = "此接口是用用户提现")
    public DistResult withdraw(@RequestBody DisWithdrawVo withdrawVo) {
        if(!isAccountVer(withdrawVo.getSecret())) {
            return   DistResult.failure("非法访问！");
        }
        DisMemberInfo memberInfo=disMemberInfoService.selectListByUserId(withdrawVo.getUserId());
        if(memberInfo==null){
            return DistResult.failure("用户不存在");
        }
        if(isAccountVer(withdrawVo.getSecret())) {
            disWithdrawRecordService.withdrawMoney(withdrawVo.getUserId(),withdrawVo.getAmount(),withdrawVo.getAccountType());
        }else {
            return DistResult.failure("非法访问");
        }
        return DistResult.success();
    }

    /**
     * 获取调用接口授权码
     * @return
     */
    @GetMapping("/getSign")
    @ResponseBody
    @ApiOperation(value = "获取secret", notes = "获取secret")
    public DistResult getSign() {
        String key= Jwt.sign(account,secret,30L * 24L * 3600L * 1000L);
        return DistResult.success(key);
    }
    public Boolean isAccountVer(String thirdSecret){
        if(!jwtUse){
            return true;
        }
        String acc= Jwt.unsign(thirdSecret,secret,String.class);
        if(acc==null){
            return false;
        }
        if(acc.equals(account)){
            return true;
        }else {
            return  false;
        }

    }
}