alter table address
    add column `erc1155_tx_qty` int(11) NOT NULL DEFAULT '0' COMMENT 'erc1155 token对应的交易数' after proposal_qty;

alter table network_stat
    add column `erc1155_tx_qty` int(11) NOT NULL DEFAULT '0' COMMENT 'erc1155 token对应的交易数' after avg_pack_time;


alter table token
    add column `is_support_erc1155` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否支持erc1155接口： 0-不支持 1-支持' after is_support_erc721_metadata;

alter table token
    add column `is_support_erc1155_metadata` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否支持metadata接口： 0-不支持 1-支持' after is_support_erc1155;


DROP TABLE IF EXISTS `tx_erc_1155_bak`;
CREATE TABLE `tx_erc_1155_bak`
(
    `id`        bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `seq`       bigint(20)   NOT NULL COMMENT '序号ID',
    `name`      varchar(64)  NOT NULL COMMENT '合约名称',
    `symbol`    varchar(64)  DEFAULT NULL COMMENT '单位',
    `decimal`   int(20)      DEFAULT NULL COMMENT '精度',
    `contract`  varchar(42)  NOT NULL COMMENT '合约地址',
    `hash`      varchar(72)  NOT NULL COMMENT '交易哈希',
    `from`      varchar(42)  NOT NULL COMMENT 'from地址',
    `from_type` int(1)       NOT NULL COMMENT '发送方类型',
    `to`        varchar(42)  NOT NULL COMMENT 'to地址',
    `to_type`   int(1)       NOT NULL COMMENT '接收方类型',
    `value`     varchar(255) NOT NULL COMMENT '交易value',
    `bn`        bigint(20)   DEFAULT NULL COMMENT '区块高度',
    `b_time`    datetime     DEFAULT NULL COMMENT '区块时间',
    `tx_fee`    varchar(255) DEFAULT NULL COMMENT '手续费',
    PRIMARY KEY (`id`)
) COMMENT ='erc1155交易备份表';


alter table token_inventory
    add column `balance` varchar(128) DEFAULT NULL COMMENT '地址代币余额, ERC20为金额, ERC721默认为1, ERC1155为token的数量' after token_id;
