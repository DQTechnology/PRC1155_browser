package com.platon.browser.v0152.analyzer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.platon.browser.dao.custommapper.CustomToken1155InventoryMapper;
import com.platon.browser.dao.entity.*;
import com.platon.browser.dao.mapper.Token1155InventoryMapper;
import com.platon.browser.elasticsearch.dto.ErcTx;
import com.platon.browser.service.erc.ErcServiceImpl;
import com.platon.browser.utils.AddressUtil;
import com.platon.browser.utils.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Erc1155,1155 token 库存服务
 */
@Slf4j
@Service
public class Erc1155TokenInventoryAnalyzer {

    @Resource
    private Token1155InventoryMapper token1155InventoryMapper;

    @Resource
    private CustomToken1155InventoryMapper customToken1155InventoryMapper;

    @Resource
    private ErcServiceImpl ercServiceImpl;


    /**
     * 解析Token库存
     * 整体逻辑
     * 1, 判断token是否记录过，如果记录或交易次数增加1
     * 2，未存在过则入库，然后交易次数+1
     */
    public void analyze(String txHash, List<ErcTx> txList, BigInteger blockNumber) {
        List<Token1155InventoryWithBLOBs> insertOrUpdate = new ArrayList<>();
        Date date = new Date();
        if (CollUtil.isNotEmpty(txList)) {
            txList.forEach(tx -> {
                String tokenAddress = tx.getContract();
                String tokenId = tx.getTokenId();
                // 校验tokenid长度是否符合入库标准
                if (CommonUtil.ofNullable(tokenId::length).orElse(0) > 128) {
                    // 仅打印日志而不能抛出异常来阻塞流程
                    log.warn("当前交易[{}]token[{}]不符合合约标准，tokenId[{}]过长，仅支持128位", txHash, tokenAddress, tokenId);
                } else {
                    // 1, 判断接收地址是否存在, 如果存在则对balance做增加动作
                    Token1155InventoryExample toExample = new Token1155InventoryExample();
                    toExample.createCriteria().andTokenAddressEqualTo(tokenAddress).andTokenIdEqualTo(tokenId);
                    //
                    List<Token1155InventoryWithBLOBs> toTokenInventoryWithBLOBs = token1155InventoryMapper.selectByExampleWithBLOBs(toExample);
                    //
                    Token1155InventoryWithBLOBs toTokenInventory = null;
                    // 不为空,交易次数+1
                    if (CollUtil.isNotEmpty(toTokenInventoryWithBLOBs) && toTokenInventoryWithBLOBs.size() == 1) {
                        toTokenInventory =  CollUtil.getFirst(toTokenInventoryWithBLOBs);
                    }
                    if (toTokenInventory == null) {
                        toTokenInventory = new Token1155InventoryWithBLOBs();
                        toTokenInventory.setTokenAddress(tokenAddress);
                        toTokenInventory.setTokenId(tokenId);
                        toTokenInventory.setCreateTime(date);
                        toTokenInventory.setTokenTxQty(1);
                        toTokenInventory.setRetryNum(0);
                        String tokenURI = ercServiceImpl.getToken1155URI(tokenAddress, new BigInteger(tokenId), blockNumber);
                        if (StrUtil.isNotBlank(tokenURI)) {
                            toTokenInventory.setTokenUrl(tokenURI);
                        } else {
                            log.warn("当前块高[{}]获取合约[{}]tokenId[{}]的tokenUrl为空，请联系管理员处理", blockNumber, tokenAddress, tokenId);
                        }
                    } else {
                        toTokenInventory.setTokenTxQty(toTokenInventory.getTokenTxQty() + 1);
                    }
                    toTokenInventory.setUpdateTime(date);
                    insertOrUpdate.add(toTokenInventory);
                }
            });

            if (CollUtil.isNotEmpty(insertOrUpdate)) {
                customToken1155InventoryMapper.batchInsertOrUpdateSelective(insertOrUpdate, Token1155Inventory.Column.values());
                log.info("当前交易[{}]添加erc1155库存[{}]笔成功", txHash, insertOrUpdate.size());
            }

        }
    }


}
