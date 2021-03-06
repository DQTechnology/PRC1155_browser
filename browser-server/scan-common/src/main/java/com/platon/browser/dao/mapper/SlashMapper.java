package com.platon.browser.dao.mapper;

import com.platon.browser.dao.entity.Slash;
import com.platon.browser.dao.entity.SlashExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SlashMapper {
    long countByExample(SlashExample example);

    int deleteByExample(SlashExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Slash record);

    int insertSelective(Slash record);

    List<Slash> selectByExampleWithBLOBs(SlashExample example);

    List<Slash> selectByExample(SlashExample example);

    Slash selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Slash record, @Param("example") SlashExample example);

    int updateByExampleWithBLOBs(@Param("record") Slash record, @Param("example") SlashExample example);

    int updateByExample(@Param("record") Slash record, @Param("example") SlashExample example);

    int updateByPrimaryKeySelective(Slash record);

    int updateByPrimaryKeyWithBLOBs(Slash record);

    int updateByPrimaryKey(Slash record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table slash
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int batchInsert(@Param("list") List<Slash> list);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table slash
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int batchInsertSelective(@Param("list") List<Slash> list, @Param("selective") Slash.Column ... selective);
}