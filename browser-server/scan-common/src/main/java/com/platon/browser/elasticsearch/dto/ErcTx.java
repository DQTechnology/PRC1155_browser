package com.platon.browser.elasticsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/**
 * @author AgentRJ
 * @create 2020-09-23 17:46
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ErcTx {

    private Long id;

    /**
     * 序号ID
     */
    private Long seq;

    /**
     * 合约名称
     */
    private String name;

    /**
     * 单位
     */
    private String symbol;

    /**
     * 精度
     */
    private Integer decimal;

    /**
     * 合约地址（也是交易to地址）
     */
    private String contract;

    /**
     * 交易哈希
     */
    private String hash;

    /**
     * erc1155的操作者
     */
    private String operator;

    /**
     * 交易发起者（也是代币扣除方）
     */
    private String from;

    private String to;

    /**
     * erc721, erc1155的标识符
     */
    private String tokenId;
    /**
     * 交易value
     */
    private String value;

    /**
     * 区块高度
     */
    private Long bn;

    /**
     * 区块时间
     */
    private Date bTime;

    /**
     * 接收方类型
     */
    private Integer toType;

    /**
     * 发送方类型
     */
    private Integer fromType;

    private String remark;

    /**
     * 手续费
     */
    private String txFee;

    /**
     * This enum was generated by MyBatis Generator.
     * This enum corresponds to the database table tx_erc_721_bak
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    public enum Column {
        id("id", "id", "BIGINT", false),
        seq("seq", "seq", "BIGINT", false),
        name("name", "name", "VARCHAR", true),
        symbol("symbol", "symbol", "VARCHAR", false),
        decimal("decimal", "decimal", "INTEGER", true),
        contract("contract", "contract", "VARCHAR", false),
        hash("hash", "hash", "VARCHAR", false),
        operator("operator", "operator", "VARCHAR", true),
        from("from", "from", "VARCHAR", true),
        fromType("from_type", "fromType", "INTEGER", false),
        to("to", "to", "VARCHAR", true),
        toType("to_type", "toType", "INTEGER", false),
        tokenId("token_id", "tokenId", "VARCHAR", true),
        value("value", "value", "VARCHAR", true),
        bn("bn", "bn", "BIGINT", false),
        bTime("b_time", "bTime", "TIMESTAMP", false),
        txFee("tx_fee", "txFee", "VARCHAR", false);

        /**
         * This field was generated by MyBatis Generator.
         * This field corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        private static final String BEGINNING_DELIMITER = "`";

        /**
         * This field was generated by MyBatis Generator.
         * This field corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        private static final String ENDING_DELIMITER = "`";

        /**
         * This field was generated by MyBatis Generator.
         * This field corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        private final String column;

        /**
         * This field was generated by MyBatis Generator.
         * This field corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        private final boolean isColumnNameDelimited;

        /**
         * This field was generated by MyBatis Generator.
         * This field corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        private final String javaProperty;

        /**
         * This field was generated by MyBatis Generator.
         * This field corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        private final String jdbcType;

        /**
         * This method was generated by MyBatis Generator.
         * This method corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        public String value() {
            return this.column;
        }

        /**
         * This method was generated by MyBatis Generator.
         * This method corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        public String getValue() {
            return this.column;
        }

        /**
         * This method was generated by MyBatis Generator.
         * This method corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        public String getJavaProperty() {
            return this.javaProperty;
        }

        /**
         * This method was generated by MyBatis Generator.
         * This method corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        public String getJdbcType() {
            return this.jdbcType;
        }

        /**
         * This method was generated by MyBatis Generator.
         * This method corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        Column(String column, String javaProperty, String jdbcType, boolean isColumnNameDelimited) {
            this.column = column;
            this.javaProperty = javaProperty;
            this.jdbcType = jdbcType;
            this.isColumnNameDelimited = isColumnNameDelimited;
        }

        /**
         * This method was generated by MyBatis Generator.
         * This method corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        public String desc() {
            return this.getEscapedColumnName() + " DESC";
        }

        /**
         * This method was generated by MyBatis Generator.
         * This method corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        public String asc() {
            return this.getEscapedColumnName() + " ASC";
        }

        /**
         * This method was generated by MyBatis Generator.
         * This method corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        public static Column[] excludes(Column... excludes) {
            ArrayList<Column> columns = new ArrayList<>(Arrays.asList(Column.values()));
            if (excludes != null && excludes.length > 0) {
                columns.removeAll(new ArrayList<>(Arrays.asList(excludes)));
            }
            return columns.toArray(new Column[]{});
        }

        /**
         * This method was generated by MyBatis Generator.
         * This method corresponds to the database table tx_erc_721_bak
         *
         * @mbg.generated
         * @project https://github.com/itfsw/mybatis-generator-plugin
         */
        public String getEscapedColumnName() {
            if (this.isColumnNameDelimited) {
                return new StringBuilder().append(BEGINNING_DELIMITER).append(this.column).append(ENDING_DELIMITER).toString();
            } else {
                return this.column;
            }
        }
    }

}
