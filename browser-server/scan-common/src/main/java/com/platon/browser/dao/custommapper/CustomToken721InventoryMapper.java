package com.platon.browser.dao.custommapper;

import com.platon.browser.bean.CustomToken721Inventory;
import com.platon.browser.bean.Erc721ContractDestroyBalanceVO;
import com.platon.browser.dao.entity.Token721Inventory;
import com.platon.browser.dao.entity.Token721InventoryKey;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CustomToken721InventoryMapper {

    int batchInsertOrUpdateSelective(@Param("list") List<Token721Inventory> list, @Param("selective") Token721Inventory.Column... selective);

    void burnAndDelTokenInventory(@Param("list") List<Token721InventoryKey> list);

    CustomToken721Inventory selectTokenInventory(Token721InventoryKey key);

    List<Erc721ContractDestroyBalanceVO> findErc721ContractDestroyBalance(@Param("tokenAddress") String tokenAddress);



}