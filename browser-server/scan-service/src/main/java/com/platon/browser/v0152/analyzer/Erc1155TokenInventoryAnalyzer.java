package com.platon.browser.v0152.analyzer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.platon.browser.dao.custommapper.CustomToken1155InventoryMapper;
import com.platon.browser.dao.entity.Token1155InventoryKey;
import com.platon.browser.dao.entity.Token1155Inventory;
import com.platon.browser.dao.mapper.Token1155InventoryMapper;
import com.platon.browser.elasticsearch.dto.ErcTx;
import com.platon.browser.enums.ErcTypeEnum;
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


    private Token1155InventoryKey makeTokenInventoryKey(String tokenAddress, String tokenId, String owner) {
        Token1155InventoryKey key = new Token1155InventoryKey();
        key.setTokenAddress(tokenAddress);
        key.setTokenId(tokenId);
        key.setOwner(owner);
        return key;
    }

    /**
     * 更新拥有者和token的数量
     *
     * @param tokenInventory
     */
    private void updateTokenOwnerAndTokenTxQty(Token1155Inventory tokenInventory) {
        if (tokenInventory.getTokenOwnerTxQty() == null) {
            tokenInventory.setTokenOwnerTxQty(1);
        } else {
            tokenInventory.setTokenOwnerTxQty(tokenInventory.getTokenOwnerTxQty() + 1);
        }

        //
        if (tokenInventory.getTokenTxQty() == null) {
            tokenInventory.setTokenTxQty(1);
        } else {
            tokenInventory.setTokenTxQty(tokenInventory.getTokenTxQty() + 1);
        }

    }

    /**
     * 解析Token库存
     */
    public void analyze(String txHash, List<ErcTx> txList) {
        List<Token1155Inventory> insertOrUpdate = new ArrayList<>();
        List<Token1155InventoryKey> delTokenInventory = new ArrayList<>();
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

                    // 1, 判断接受地址是否存在, 如果存在则对balance做增加动作
                    if (StrUtil.isNotBlank(tx.getTo()) && !AddressUtil.isAddrZero(tx.getTo())) {
                        BigInteger valueBN = new BigInteger(tx.getValue());
                        Token1155InventoryKey toKey = makeTokenInventoryKey(tokenAddress, tokenId, tx.getTo());
                        Token1155Inventory toTokenInventory = token1155InventoryMapper.selectByPrimaryKey(toKey);
                        if (toTokenInventory == null) {
                            toTokenInventory = new Token1155Inventory();
                            toTokenInventory.setTokenAddress(toKey.getTokenAddress());
                            toTokenInventory.setTokenId(toKey.getTokenId());
                            toTokenInventory.setCreateTime(date);
                            toTokenInventory.setOwner(toKey.getOwner());
                            toTokenInventory.setBalance(tx.getValue());
                        } else {
                            // 发起方和接收方不是同一个地址,则增加接收方数量
                            if (!tx.getFrom().equals(tx.getTo())) {
                                BigInteger toBalance = new BigInteger(toTokenInventory.getBalance());
                                //  增加发送的数量
                                toBalance = toBalance.add(valueBN);
                                toTokenInventory.setBalance(toBalance.toString());
                            }
                        }
                        updateTokenOwnerAndTokenTxQty(toTokenInventory);
                        toTokenInventory.setUpdateTime(date);
                        insertOrUpdate.add(toTokenInventory);
                        // 更新接收方的交易次数
                    }
                    // 更新发送方的库存
                    Token1155InventoryKey fromKey = makeTokenInventoryKey(tokenAddress, tokenId, tx.getFrom());
                    Token1155Inventory fromTokenInventory = token1155InventoryMapper.selectByPrimaryKey(fromKey);
                    // 发送方存在,则减去发送方数量
                    if (fromTokenInventory != null) {
                        BigInteger valueBN = new BigInteger(tx.getValue());
                        BigInteger fromBalance = new BigInteger(fromTokenInventory.getBalance());
                        fromBalance = fromBalance.subtract(valueBN);
                        // 如果发送方的数量小等于0则删除
                        if (fromBalance.compareTo(BigInteger.ZERO) <= 0) {
                            delTokenInventory.add(fromKey);
                        } else {
                            // 更新发送方的交易次数
                            updateTokenOwnerAndTokenTxQty(fromTokenInventory);
                            fromTokenInventory.setUpdateTime(date);
                            insertOrUpdate.add(fromTokenInventory);
                        }
                    }
                }
            });

            if (CollUtil.isNotEmpty(insertOrUpdate)) {
                customToken1155InventoryMapper.batchInsertOrUpdateSelective(insertOrUpdate, Token1155Inventory.Column.values());
                log.info("当前交易[{}]添加erc1155库存[{}]笔成功", txHash, insertOrUpdate.size());
            }

            if (CollUtil.isNotEmpty(delTokenInventory)) {
                customToken1155InventoryMapper.burnAndDelTokenInventory(delTokenInventory);
                log.info("当前交易[{}]删除erc1155库存[{}]笔成功", txHash, delTokenInventory.size());
            }
        }
    }

}
