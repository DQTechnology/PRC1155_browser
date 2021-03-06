//package com.platon.browser.v0150.service;
//
//import com.platon.browser.AgentTestBase;
//import com.platon.browser.cache.AddressCache;
//import com.platon.browser.dao.entity.Delegation;
//import com.platon.browser.dao.entity.Node;
//import com.platon.browser.dao.entity.Staking;
//import com.platon.browser.dao.mapper.DelegationMapper;
//import com.platon.browser.dao.mapper.NodeMapper;
//import com.platon.browser.dao.mapper.StakingMapper;
//import com.platon.browser.bean.CustomStaking;
//import com.platon.browser.exception.BlockNumberException;
//import com.platon.browser.v0150.V0150Config;
//import com.platon.browser.v0150.bean.AdjustParam;
//import com.platon.browser.v0150.dao.StakingDelegateBalanceAdjustmentMapper;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.Spy;
//import org.mockito.junit.MockitoJUnitRunner;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.io.File;
//import java.math.BigDecimal;
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.when;
//
///**
// *
// **/
//@Slf4j
//@RunWith(MockitoJUnitRunner.Silent.class)
//public class AdjustServiceTest extends AgentTestBase {
//    @Mock
//    private DelegationMapper delegationMapper;
//    @Mock
//    private StakingMapper stakingMapper;
//    @Mock
//    private NodeMapper nodeMapper;
//    @Mock
//    private StakingDelegateBalanceAdjustmentMapper stakingDelegateBalanceAdjustmentMapper;
//    @Mock
//    private V0150Config v0150Config;
//    private String adjustLogFile = System.getProperty("user.dir")+ File.separator+"adjust.log";
//    @Spy
//    @InjectMocks
//    private StakingDelegateBalanceAdjustmentService target;
//    @Mock
//    private AddressCache addressCache;
//
//    private Node node;
//    private Staking staking;
//    private Delegation delegation;
//    private List<AdjustParam> delegateAdjustParamList = new ArrayList<>();
//    private List<AdjustParam> stakingAdjustParamList = new ArrayList<>();
//
//    @Before
//    public void setup(){
//        ReflectionTestUtils.setField(target,"chainConfig", blockChainConfig);
//        when(v0150Config.getAdjustLogFilePath()).thenReturn(adjustLogFile);
//        ReflectionTestUtils.invokeMethod(target,"init");
//        node = nodeList.get(0);
//        staking = stakingList.get(0);
//        staking.setStakingHes(BigDecimal.ONE);
//        staking.setStakingLocked(BigDecimal.ONE);
//        staking.setStakingReduction(BigDecimal.valueOf(2));
//        delegation = delegationList.get(0);
//        delegation.setDelegateHes(BigDecimal.ONE);
//        delegation.setDelegateLocked(BigDecimal.ONE);
//        delegation.setDelegateReleased(BigDecimal.valueOf(2));
//
//        adjustParamList.forEach(param->{
//            if("staking".equals(param.getOptType())){
//                stakingAdjustParamList.add(param);
//            }
//            if("delegate".equals(param.getOptType())){
//                delegateAdjustParamList.add(param);
//            }
//        });
//
//
//        when(nodeMapper.selectByPrimaryKey(any())).thenReturn(node);
//        when(stakingMapper.selectByPrimaryKey(any())).thenReturn(staking);
//        when(delegationMapper.selectByPrimaryKey(any())).thenReturn(delegation);
//    }
//
//    private void adjustDelegate() throws BlockNumberException {
//        String adjustMsg = target.adjust(adjustParamList);
//        Assert.assertFalse(adjustMsg.contains("??????"));
//
//        //????????????
//        delegation.setDelegateHes(BigDecimal.valueOf(0.5));
//        adjustMsg = target.adjust(delegateAdjustParamList);
//        Assert.assertTrue(adjustMsg.contains("???????????????????????????[???????????????"));
//
//        delegation.setDelegateHes(BigDecimal.ONE);
//        delegation.setDelegateLocked(BigDecimal.valueOf(0.5));
//        adjustMsg = target.adjust(delegateAdjustParamList);
//        Assert.assertTrue(adjustMsg.contains("???????????????????????????[???????????????"));
//
//        delegation.setDelegateHes(BigDecimal.valueOf(0.5));
//        delegation.setDelegateLocked(BigDecimal.valueOf(0.5));
//        adjustMsg = target.adjust(delegateAdjustParamList);
//        Assert.assertTrue(adjustMsg.contains("???????????????????????????[???????????????"));
//        Assert.assertTrue(adjustMsg.contains("???????????????????????????[???????????????"));
//
//        delegation.setDelegateHes(BigDecimal.TEN);
//        delegation.setDelegateLocked(BigDecimal.TEN);
//        adjustMsg = target.adjust(delegateAdjustParamList);
//        Assert.assertFalse(adjustMsg.contains("??????"));
//
//        //????????????
//        staking.setStakingHes(BigDecimal.valueOf(0.5));
//        adjustMsg = target.adjust(stakingAdjustParamList);
//        Assert.assertTrue(adjustMsg.contains("???????????????????????????[???????????????"));
//
//        staking.setStakingHes(BigDecimal.ONE);
//        staking.setStakingLocked(BigDecimal.valueOf(0.5));
//        adjustMsg = target.adjust(stakingAdjustParamList);
//        Assert.assertTrue(adjustMsg.contains("???????????????????????????[???????????????"));
//
//        staking.setStakingHes(BigDecimal.valueOf(0.5));
//        staking.setStakingLocked(BigDecimal.valueOf(0.5));
//        adjustMsg = target.adjust(stakingAdjustParamList);
//        Assert.assertTrue(adjustMsg.contains("???????????????????????????[???????????????"));
//        Assert.assertTrue(adjustMsg.contains("???????????????????????????[???????????????"));
//
//        staking.setStakingHes(BigDecimal.TEN);
//        staking.setStakingLocked(BigDecimal.TEN);
//        adjustMsg = target.adjust(stakingAdjustParamList);
//        Assert.assertFalse(adjustMsg.contains("??????"));
//    }
//
//    private void adjustStaking() throws BlockNumberException {
//        String adjustMsg = target.adjust(adjustParamList);
//        Assert.assertFalse(adjustMsg.contains("??????"));
//
//        //????????????
//        delegation.setDelegateReleased(BigDecimal.valueOf(0.5));
//        adjustMsg = target.adjust(delegateAdjustParamList);
//        Assert.assertTrue(adjustMsg.contains("???????????????????????????[???????????????"));
//
//        delegation.setDelegateReleased(BigDecimal.TEN);
//        adjustMsg = target.adjust(delegateAdjustParamList);
//        Assert.assertFalse(adjustMsg.contains("??????"));
//
//        //????????????
//        staking.setStakingReduction(BigDecimal.valueOf(0.5));
//        adjustMsg = target.adjust(stakingAdjustParamList);
//        Assert.assertTrue(adjustMsg.contains("???????????????????????????[???????????????"));
//
//        staking.setStakingReduction(BigDecimal.TEN);
//        adjustMsg = target.adjust(stakingAdjustParamList);
//        Assert.assertFalse(adjustMsg.contains("??????"));
//    }
//
//    @Test
//    public void test() throws Exception {
//        // **********************??????????????????**********************
//        staking.setStatus(CustomStaking.StatusEnum.CANDIDATE.getCode());
//        adjustDelegate();
//        // **********************??????????????????**********************
//        staking.setStatus(CustomStaking.StatusEnum.LOCKED.getCode());
//        adjustDelegate();
//
//        // **********************??????????????????**********************
//        staking.setStatus(CustomStaking.StatusEnum.EXITING.getCode());
//        adjustStaking();
//        // **********************??????????????????**********************
//        staking.setStatus(CustomStaking.StatusEnum.EXITED.getCode());
//        adjustStaking();
//    }
//}
