package com.platon.browser.dao.custommapper;

import com.platon.browser.bean.CustomToken1155Inventory;
import com.platon.browser.bean.CustomToken721Inventory;
import com.platon.browser.bean.Erc1155ContractDestroyBalanceVO;
import com.platon.browser.dao.entity.Token1155Inventory;
import com.platon.browser.dao.entity.Token1155InventoryKey;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CustomToken1155InventoryMapper {

    int batchInsertOrUpdateSelective(@Param("list") List<Token1155Inventory> list, @Param("selective") Token1155Inventory.Column... selective);

    void burnAndDelTokenInventory(@Param("list") List<Token1155InventoryKey> list);

    CustomToken1155Inventory selectTokenInventory(Token1155InventoryKey key);

    List<Erc1155ContractDestroyBalanceVO> findErc1155ContractDestroyBalance(@Param("tokenAddress") String tokenAddress);



}