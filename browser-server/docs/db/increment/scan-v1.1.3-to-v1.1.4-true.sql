USE `scan_platon`;
-- 确保show variables like 'innodb_autoinc_lock_mode';是1
DROP TABLE IF EXISTS `point_log`;
CREATE TABLE `point_log` (
                             `id` int(64) NOT NULL AUTO_INCREMENT COMMENT '主键id',
                             `type` int(1) NOT NULL COMMENT '类型,1-mysql,2-es',
                             `name` varchar(255) NOT NULL COMMENT '表名或索引名',
                             `desc` varchar(255) NOT NULL COMMENT '用途描述',
                             `position` varchar(128) NOT NULL COMMENT '已统计的位置(MySQL为自增id,es为seq)',
                             `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`)
) COMMENT='断点统计表';
INSERT INTO `point_log`(`id`, `type`, `name`, `desc`, `position`, `create_time`, `update_time`) VALUES (1, 1, 'n_opt_bak', '节点操作迁移至es的断点记录', '0', '2021-12-01 07:50:41', '2021-12-01 07:50:41');
INSERT INTO `point_log`(`id`, `type`, `name`, `desc`, `position`, `create_time`, `update_time`) VALUES (2, 1, 'tx_bak', '从交易备份表统计地址表交易数的断点记录', '0', '2021-12-03 06:33:27', '2021-12-03 06:33:27');
INSERT INTO `point_log`(`id`, `type`, `name`, `desc`, `position`, `create_time`, `update_time`) VALUES (3, 1, 'tx_20_bak', '从erc20交易备份表统计地址表和token表交易数的断点记录', '0', '2021-12-06 02:47:26', '2021-12-06 02:47:26');
INSERT INTO `point_log`(`id`, `type`, `name`, `desc`, `position`, `create_time`, `update_time`) VALUES (4, 1, 'tx_721_bak', '从erc721交易备份表统计地址表和token表交易数的断点记录', '0', '2021-12-06 02:47:41', '2021-12-06 02:47:41');
INSERT INTO `point_log`(`id`, `type`, `name`, `desc`, `position`, `create_time`, `update_time`) VALUES (5, 1, 'tx_20_bak', '从erc20交易备份表统计TokenHolder余额的断点记录', '0', '2021-12-06 10:23:58', '2021-12-06 10:25:49');
INSERT INTO `point_log`(`id`, `type`, `name`, `desc`, `position`, `create_time`, `update_time`) VALUES (6, 1, 'tx_721_bak', '从erc721交易备份表统计TokenHolder持有者数的断点记录', '0', '2021-12-06 10:25:34', '2021-12-06 10:25:39');
INSERT INTO `point_log`(`id`, `type`, `name`, `desc`, `position`, `create_time`, `update_time`) VALUES (7, 1, 'token_inventory', '增量更新token库存信息断点记录', '0', '2021-12-10 02:44:32', '2021-12-10 02:44:32');


DROP TABLE IF EXISTS `slash`;
CREATE TABLE `slash` (
                              `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
                              `slash_data` longtext NOT NULL COMMENT '举报证据',
                              `node_id` varchar(130) NOT NULL COMMENT '节点Id',
                              `tx_hash` varchar(128) NOT NULL COMMENT '交易hash',
                              `time` datetime NOT NULL COMMENT '时间',
                              `setting_epoch` int(16) NOT NULL COMMENT '通过（block_number/每个结算周期出块数）向上取整',
                              `staking_block_num` bigint(20) NOT NULL COMMENT '质押交易所在块高',
                              `slash_rate` decimal(65,2) NOT NULL DEFAULT '0' COMMENT '双签惩罚比例',
                              `slash_report_rate` decimal(65,2) NOT NULL DEFAULT '0' COMMENT '惩罚金分配给举报人比例',
                              `benefit_address` varchar(255) NOT NULL COMMENT '交易发送者',
                              `code_remain_redeem_amount` decimal(65,2) NOT NULL DEFAULT '0' COMMENT '双签惩罚后剩下的质押金额，因为双签处罚后节点就被置为退出中，所有金额都会移动到待赎回字段中',
                              `code_reward_value` decimal(65,2) NOT NULL DEFAULT '0' COMMENT '奖励的金额',
                              `code_status` int(1) DEFAULT NULL COMMENT '节点状态:1候选中,2退出中,3已退出,4已锁定',
                              `code_staking_reduction_epoch` int(16) DEFAULT NULL COMMENT '当前退出中',
                              `code_slash_value` decimal(65,2) NOT NULL DEFAULT '0' COMMENT '惩罚的金额',
                              `un_stake_freeze_duration` int(16) NOT NULL COMMENT '解质押需要经过的结算周期数',
                              `un_stake_end_block` bigint(20) NOT NULL COMMENT '解质押冻结的最后一个区块：理论结束块与投票结束块中的最大者',
                              `block_num` bigint(20) NOT NULL COMMENT '双签的区块',
                              `is_quit` int(11) NOT NULL DEFAULT '1' COMMENT '是否退出:1是,2否',
                              `is_handle` tinyint(1) NOT NULL COMMENT '是否已处理，1-是，0-否',
                              `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`)
) COMMENT='惩罚记录表';

DROP TABLE IF EXISTS `tx_bak`;
CREATE TABLE `tx_bak` (
                          `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
                          `hash` varchar(72) NOT NULL COMMENT '交易Hash',
                          `tx_type` int(10) NOT NULL COMMENT '交易类型',
                          `num` bigint(20) NOT NULL COMMENT '区块号',
                          `info` text COMMENT '交易信息',
                          `from_address` varchar(42) NOT NULL COMMENT 'from地址',
                          `to_address` varchar(42) NOT NULL COMMENT 'to地址',
                          `contract_address` varchar(42) DEFAULT NULL COMMENT '合约地址',
                          `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          KEY `block_number` (`num`) USING BTREE,
                          KEY `id` (`id`)
) COMMENT='交易备份表';

DROP TABLE IF EXISTS `tx_erc_20_bak`;
CREATE TABLE `tx_erc_20_bak` (
                                 `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
                                 `seq` bigint(20) NOT NULL COMMENT '序号ID',
                                 `name` varchar(64) NOT NULL COMMENT '合约名称',
                                 `symbol` varchar(64) DEFAULT NULL COMMENT '单位',
                                 `decimal` int(20) DEFAULT NULL COMMENT '精度',
                                 `contract` varchar(42) NOT NULL COMMENT '合约地址',
                                 `hash` varchar(72) NOT NULL COMMENT '交易哈希',
                                 `from` varchar(42) NOT NULL COMMENT 'from地址',
                                 `from_type` int(1) NOT NULL COMMENT '发送方类型',
                                 `to` varchar(42) NOT NULL COMMENT 'to地址',
                                 `to_type` int(1) NOT NULL COMMENT '接收方类型',
                                 `value` varchar(255) NOT NULL COMMENT '交易value',
                                 `bn` bigint(20) DEFAULT NULL COMMENT '区块高度',
                                 `b_time` datetime DEFAULT NULL COMMENT '区块时间',
                                 `tx_fee` varchar(255) DEFAULT NULL COMMENT '手续费',
                                 PRIMARY KEY (`id`)
) COMMENT='erc20交易备份表';

DROP TABLE IF EXISTS `tx_erc_721_bak`;
CREATE TABLE `tx_erc_721_bak` (
                                 `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
                                 `seq` bigint(20) NOT NULL COMMENT '序号ID',
                                 `name` varchar(64) NOT NULL COMMENT '合约名称',
                                 `symbol` varchar(64) DEFAULT NULL COMMENT '单位',
                                 `decimal` int(20) DEFAULT NULL COMMENT '精度',
                                 `contract` varchar(42) NOT NULL COMMENT '合约地址',
                                 `hash` varchar(72) NOT NULL COMMENT '交易哈希',
                                 `from` varchar(42) NOT NULL COMMENT 'from地址',
                                 `from_type` int(1) NOT NULL COMMENT '发送方类型',
                                 `to` varchar(42) NOT NULL COMMENT 'to地址',
                                 `to_type` int(1) NOT NULL COMMENT '接收方类型',
                                 `value` varchar(255) NOT NULL COMMENT '交易value',
                                 `bn` bigint(20) DEFAULT NULL COMMENT '区块高度',
                                 `b_time` datetime DEFAULT NULL COMMENT '区块时间',
                                 `tx_fee` varchar(255) DEFAULT NULL COMMENT '手续费',
                                 PRIMARY KEY (`id`)
) COMMENT='erc721交易备份表';