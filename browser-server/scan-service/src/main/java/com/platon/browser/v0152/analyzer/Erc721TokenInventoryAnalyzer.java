package com.platon.browser.v0152.analyzer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import com.platon.browser.dao.custommapper.CustomToken721InventoryMapper;
import com.platon.browser.dao.entity.Token721Inventory;
import com.platon.browser.dao.entity.Token721InventoryKey;
import com.platon.browser.dao.entity.TokenInventoryExample;
import com.platon.browser.dao.mapper.Token721InventoryMapper;
import com.platon.browser.elasticsearch.dto.ErcTx;
import com.platon.browser.service.erc.ErcServiceImpl;
import com.platon.browser.utils.AddressUtil;
import com.platon.browser.utils.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Erc721 token 库存服务
 */
@Slf4j
@Service
public class Erc721TokenInventoryAnalyzer {

    @Resource
    private Token721InventoryMapper token721InventoryMapper;

    @Resource
    private CustomToken721InventoryMapper customToken721InventoryMapper;

    @Resource
    private ErcServiceImpl ercServiceImpl;

    /**
     * 解析Token库存
     */
    public void analyze(String txHash, List<ErcTx> txList) {
        List<Token721Inventory> insertOrUpdate = new ArrayList<>();
        List<Token721InventoryKey> delTokenInventory = new ArrayList<>();
        Date date = new Date();
        if (CollUtil.isNotEmpty(txList)) {
            txList.forEach(tx -> {
                String tokenAddress = tx.getContract();
                String tokenId = tx.getTokenId();
                // 校验tokenid长度是否符合入库标准
                if (CommonUtil.ofNullable(() -> tokenId.length()).orElse(0) > 128) {
                    // 仅打印日志而不能抛出异常来阻塞流程
                    log.warn("当前交易[{}]token[{}]不符合合约标准，tokenId[{}]过长，仅支持128位", txHash, tokenAddress, tokenId);
                } else {
                    TokenInventoryExample example = new TokenInventoryExample();
                    example.createCriteria().andTokenAddressEqualTo(tokenAddress).andTokenIdEqualTo(tokenId);
                    List<Token721Inventory> tokenInventoryList = token721InventoryMapper.selectByExample(example);
                    Token721Inventory tokenInventory;
                    // 不为空，交易次数加1
                    if (CollUtil.isNotEmpty(tokenInventoryList) && tokenInventoryList.size() == 1) {
                        tokenInventory = CollUtil.getFirst(tokenInventoryList);
                        tokenInventory.setTokenTxQty(tokenInventory.getTokenTxQty() + 1);
                    } else {
                        // 为空，则新建对象
                        tokenInventory = new Token721Inventory();
                        tokenInventory.setTokenAddress(tokenAddress);
                        tokenInventory.setTokenId(tokenId);
                        tokenInventory.setCreateTime(date);
                        tokenInventory.setTokenTxQty(1);
                    }

                    if (tx.getTo().equalsIgnoreCase(tokenInventory.getOwner())) {
                        int tokenOwnerTxQty = tokenInventory.getTokenOwnerTxQty() == null ? 0 : tokenInventory.getTokenOwnerTxQty();
                        tokenInventory.setTokenOwnerTxQty(tokenOwnerTxQty + 1);
                    } else {
                        tokenInventory.setTokenOwnerTxQty(1);
                    }
                    tokenInventory.setOwner(tx.getTo());
                    tokenInventory.setUpdateTime(date);
                    insertOrUpdate.add(tokenInventory);
                    // 如果合约交易当中，to地址是0地址的话，需要清除TokenInventory记录
                    if (StrUtil.isNotBlank(tx.getTo()) && AddressUtil.isAddrZero(tx.getTo())) {
                        Token721InventoryKey tokenInventoryKey = new Token721InventoryKey();
                        tokenInventoryKey.setTokenId(tx.getTokenId());
                        tokenInventoryKey.setTokenAddress(tx.getContract());
                        delTokenInventory.add(tokenInventoryKey);
                    }
                }
            });
            if (CollUtil.isNotEmpty(insertOrUpdate)) {
                customToken721InventoryMapper.batchInsertOrUpdateSelective(insertOrUpdate, Token721Inventory.Column.values());
                log.info("当前交易[{}]添加erc721库存[{}]笔成功", txHash, insertOrUpdate.size());
            }
            if (CollUtil.isNotEmpty(delTokenInventory)) {
                customToken721InventoryMapper.burnAndDelTokenInventory(delTokenInventory);
                log.info("当前交易[{}]删除erc721库存[{}]笔成功", txHash, delTokenInventory.size());
            }
        }
    }

}