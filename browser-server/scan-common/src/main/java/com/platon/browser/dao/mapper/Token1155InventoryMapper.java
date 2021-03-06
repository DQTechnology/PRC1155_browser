package com.platon.browser.dao.mapper;

import com.github.pagehelper.Page;
import com.platon.browser.dao.entity.*;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface Token1155InventoryMapper {
    long countByExample(TokenInventoryExample example);

    int deleteByExample(TokenInventoryExample example);

    int deleteByPrimaryKey(Token1155InventoryKey key);

    int insert(Token1155Inventory record);

    int insertSelective(Token1155Inventory record);

    Page<Token1155InventoryWithBLOBs> selectByExampleWithBLOBs(Token1155InventoryExample example);

    Page<Token1155Inventory> selectByExample(Token1155InventoryExample example);

    Token1155Inventory selectByPrimaryKey(Token1155InventoryKey key);

    int updateByExampleSelective(@Param("record") Token1155Inventory record, @Param("example") Token1155InventoryExample example);

    int updateByExample(@Param("record") Token1155Inventory record, @Param("example") Token1155InventoryExample example);

    int updateByPrimaryKeySelective(Token1155Inventory record);

    int updateByPrimaryKey(Token1155Inventory record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table token_inventory
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int batchInsert(@Param("list") List<Token1155Inventory> list);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table token_inventory
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int batchInsertSelective(@Param("list") List<Token1155Inventory> list, @Param("selective") Token1155Inventory.Column ... selective);
}