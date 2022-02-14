package com.platon.browser.queue.handler;

import cn.hutool.core.collection.ListUtil;
import com.platon.browser.dao.entity.Token;
import com.platon.browser.dao.mapper.TokenMapper;
import com.platon.browser.elasticsearch.dto.ErcTx;
import com.platon.browser.queue.event.TokenEvent;
import com.platon.browser.service.elasticsearch.EsERC1155Service;
import com.platon.browser.service.elasticsearch.EsERC20Service;
import com.platon.browser.service.elasticsearch.EsERC721Service;
import com.platon.browser.service.redis.RedisErc1155TxService;
import com.platon.browser.service.redis.RedisErc20TxService;
import com.platon.browser.service.redis.RedisErc721TxService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;

/**
 * token事件处理器
 *
 * @author huangyongpeng@matrixelements.com
 * @date 2021/3/22
 */
@Slf4j
@Component
public class TokenHandler extends AbstractHandler<TokenEvent> {

    @Resource
    private TokenMapper tokenMapper;

    @Autowired
    private EsERC20Service esERC20Service;

    @Autowired
    private EsERC721Service esERC721Service;

    @Autowired
    private EsERC1155Service esERC1155Service;

    @Autowired
    private RedisErc20TxService redisErc20TxService;

    @Autowired
    private RedisErc721TxService redisErc721TxService;

    @Autowired
    private RedisErc1155TxService redisErc1155TxService;

    @Setter
    @Getter
    @Value("${disruptor.queue.token.batch-size}")
    private volatile int batchSize;

    private Set<Token> stage = new HashSet<>();

    StageCache<ErcTx> erc20Stage = new StageCache<>();

    StageCache<ErcTx> erc721Stage = new StageCache<>();

    StageCache<ErcTx> erc1155Stage = new StageCache<>();

    @PostConstruct
    private void init() {
        this.setLogger(log);
    }

    @Retryable(value = Exception.class, maxAttempts = Integer.MAX_VALUE)
    @Override
    public void onEvent(TokenEvent event, long sequence, boolean endOfBatch) throws Exception {
        long startTime = System.currentTimeMillis();
        try {
            stage.addAll(event.getTokenList());
            erc20Stage.getData().addAll(event.getErc20TxList());
            erc721Stage.getData().addAll(event.getErc721TxList());
            erc1155Stage.getData().addAll(event.getErc1155TxList());
            if (stage.size() < batchSize)
                return;
            tokenMapper.batchInsert(ListUtil.list(true, stage));
            esERC20Service.save(erc20Stage);
            esERC721Service.save(erc721Stage);
            esERC1155Service.save(erc1155Stage);

            redisErc20TxService.save(erc20Stage.getData(), false);
            redisErc721TxService.save(erc721Stage.getData(), false);
            redisErc1155TxService.save(erc1155Stage.getData(), false);
            long endTime = System.currentTimeMillis();
            printTps("token", stage.size(), startTime, endTime);
            stage.clear();
        } catch (Exception e) {
            log.error("", e);
            throw e;
        }
    }

}
