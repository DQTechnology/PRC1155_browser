package com.platon.browser.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.Page;
import com.platon.browser.bean.*;
import com.platon.browser.bean.http.CustomHttpClient;
import com.platon.browser.dao.custommapper.*;
import com.platon.browser.dao.entity.*;
import com.platon.browser.dao.mapper.*;
import com.platon.browser.elasticsearch.dto.ErcTx;
import com.platon.browser.enums.AddressTypeEnum;
import com.platon.browser.enums.ErcTypeEnum;
import com.platon.browser.service.elasticsearch.AbstractEsRepository;
import com.platon.browser.service.elasticsearch.bean.ESResult;
import com.platon.browser.service.elasticsearch.query.ESQueryBuilderConstructor;
import com.platon.browser.service.elasticsearch.query.ESQueryBuilders;
import com.platon.browser.service.erc.ErcServiceImpl;
import com.platon.browser.utils.AddressUtil;
import com.platon.browser.utils.AppStatusUtil;
import com.platon.browser.utils.TaskUtil;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * token????????????
 *
 * @date: 2021/11/30
 */
@Slf4j
@Component
public class ErcTokenUpdateTask {

    /**
     * token_inventory???????????????
     */
    @Value("${platon.token-retry-num:3}")
    private int tokenRetryNum;

    @Resource
    private TokenInventoryMapper token721InventoryMapper;

    @Resource
    private Token1155InventoryMapper token1155InventoryMapper;

    @Resource
    private CustomTokenInventoryMapper customToken721InventoryMapper;

    @Resource
    private CustomToken1155InventoryMapper customToken1155InventoryMapper;


    @Resource
    private TokenHolderMapper tokenHolderMapper;

    @Resource
    private CustomTokenHolderMapper customTokenHolderMapper;

    @Resource
    private CustomTokenMapper customTokenMapper;

    @Resource
    private TokenMapper tokenMapper;

    @Resource
    private CustomAddressMapper customAddressMapper;

    @Resource
    private ErcServiceImpl ercServiceImpl;

    @Resource
    private PointLogMapper pointLogMapper;

    @Resource
    private TxErc20BakMapper txErc20BakMapper;

    @Resource
    private TxErc721BakMapper txErc721BakMapper;


    @Resource
    private TxErc1155BakMapper txErc1155BakMapper;


    private static final int TOKEN_BATCH_SIZE = 10;

    private static final ExecutorService TOKEN_UPDATE_POOL = Executors.newFixedThreadPool(TOKEN_BATCH_SIZE);

    private static final int HOLDER_BATCH_SIZE = 10;

    private static final ExecutorService HOLDER_UPDATE_POOL = Executors.newFixedThreadPool(HOLDER_BATCH_SIZE);

    private final Lock lock = new ReentrantLock();

    private final Lock tokenInventoryLock = new ReentrantLock();

    private final Lock tokenHolderLock = new ReentrantLock();

    /**
     * ????????????token???????????????
     * ???5????????????
     *
     * @return void
     * @date 2021/1/18
     */
    @XxlJob("totalUpdateTokenTotalSupplyJobHandler")
    public void totalUpdateTokenTotalSupply() {
        lock.lock();
        try {
            updateTokenTotalSupply();
        } catch (Exception e) {
            log.warn("????????????token?????????????????????", e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    /**
     * ????????????token???????????????
     * ???1??????????????????
     *
     * @param
     * @return void
     * @date 2021/2/1
     */
    @XxlJob("incrementUpdateTokenHolderBalanceJobHandler")
    public void incrementUpdateTokenHolderBalance() {
        if (tokenHolderLock.tryLock()) {
            try {
                incrementUpdateErc20TokenHolderBalance();
                incrementUpdateErc721TokenHolderBalance();
                incrementUpdateErc1155TokenHolderBalance();
            } catch (Exception e) {
                log.error("????????????token?????????????????????", e);
            } finally {
                tokenHolderLock.unlock();
            }
        }
    }

    /**
     * ????????????token???????????????
     * ??????00:00:00????????????
     */
    @XxlJob("totalUpdateTokenHolderBalanceJobHandler")
    public void totalUpdateTokenHolderBalance() {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        tokenHolderLock.lock();
        try {
            // ????????????holder???balance
            List<TokenHolder> batch;
            int page = 0;
            do {
                TokenHolderExample condition = new TokenHolderExample();
                condition.setOrderByClause(" token_address asc, address asc limit " + page * HOLDER_BATCH_SIZE + "," + HOLDER_BATCH_SIZE);
                batch = tokenHolderMapper.selectByExample(condition);
                List<ErcToken> ercTokens = getErcTokens();
                // ?????????????????????
                List<TokenHolder> res = subtractToList(batch, getDestroyContracts());
                List<TokenHolder> updateParams = new ArrayList<>();
                if (CollUtil.isNotEmpty(res)) {
                    CountDownLatch latch = new CountDownLatch(res.size());
                    res.forEach(holder -> {
                        HOLDER_UPDATE_POOL.submit(() -> {
                            try {
                                // ?????????????????????
                                ErcToken token = CollUtil.findOne(ercTokens, ercToken -> ercToken.getAddress().equalsIgnoreCase(holder.getTokenAddress()));
                                if (token != null) {
                                    BigInteger balance = ercServiceImpl.getBalance(holder.getTokenAddress(), token.getTypeEnum(), holder.getAddress(), new BigInteger(holder.getTokenId()));
                                    if (ObjectUtil.isNull(holder.getBalance()) || new BigDecimal(holder.getBalance()).compareTo(new BigDecimal(balance)) != 0) {
                                        log.info("token[{}]address[{}]???????????????????????????,??????[{}]??????[{}]", holder.getTokenAddress(), holder.getAddress(), holder.getBalance(), balance.toString());
                                        // ????????????????????????????????????????????????????????????
                                        holder.setBalance(balance.toString());
                                        updateParams.add(holder);
                                    }
                                } else {
                                    String msg = StrUtil.format("??????????????????token[{}]", holder.getTokenAddress());
                                    XxlJobHelper.log(msg);
                                    log.error(msg);
                                }
                            } catch (Exception e) {
                                XxlJobHelper.log(StrUtil.format("??????token holder????????????????????????[{}]??????[{}]", holder.getTokenAddress(), holder.getAddress()));
                                log.warn(StrFormatter.format("??????????????????,??????[{}],????????????[{}]", holder.getAddress(), holder.getTokenAddress()), e);
                            } finally {
                                latch.countDown();
                            }
                        });
                    });
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        log.error("", e);
                    }
                }
                if (CollUtil.isNotEmpty(updateParams)) {
                    customTokenHolderMapper.batchUpdate(updateParams);
                    TaskUtil.console("??????token???????????????{}", JSONUtil.toJsonStr(updateParams));
                }
                page++;
            } while (!batch.isEmpty());
            XxlJobHelper.log("????????????token?????????????????????");
        } catch (Exception e) {
            log.error("??????????????????????????????", e);
        } finally {
            tokenHolderLock.unlock();
        }
    }

    /**
     * ????????????token????????????
     * ????????????1???????????????
     *
     * @param
     * @return void
     * @date 2021/4/17
     */
    @XxlJob("totalUpdateTokenInventoryJobHandler")
    public void totalUpdateTokenInventory() {
        tokenInventoryLock.lock();
        try {
            updateToken721Inventory(0);
            updateToken1155Inventory(0);
        } catch (Exception e) {
            log.error("??????token????????????", e);
        } finally {
            tokenInventoryLock.unlock();
        }
    }

    /**
     * ????????????token????????????
     * ???1??????????????????
     *
     * @param
     * @return void
     * @date 2021/2/1
     */
    @XxlJob("incrementUpdateTokenInventoryJobHandler")
    public void incrementUpdateTokenInventory() {
        if (tokenInventoryLock.tryLock()) {
            try {
                cronIncrementUpdateToken721Inventory();
                cronIncrementUpdateToken1155Inventory();
            } catch (Exception e) {
                log.warn("????????????token??????????????????", e);
            } finally {
                tokenInventoryLock.unlock();
            }
        } else {
            log.warn("??????token??????????????????????????????");
        }
    }

    /**
     * ?????????721??????????????????
     * ???10??????????????????
     *
     * @param :
     * @return: void
     * @date: 2021/9/27
     */
    @XxlJob("contractDestroyUpdateBalanceJobHandler")
    public void contractDestroyUpdateBalance() {
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        contractErc20DestroyUpdateBalance();
        contractErc721DestroyUpdateBalance();
        contractErc1155DestroyUpdateBalance();
    }

    @XxlJob("contractDestroyUpdateInfoJobHandler")
    public void contractDestroyUpdateInfo() {
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        updateToken721InventoryInfo();
        updateToken1155InventoryInfo();
    }

    /**
     * ??????ERC20???Erc721Enumeration token???????????????===???????????????
     *
     * @return void
     * @date 2021/1/18
     */
    private void updateTokenTotalSupply() {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        Set<ErcToken> updateParams = new ConcurrentHashSet<>();
        List<List<ErcToken>> batchList = new ArrayList<>();
        List<ErcToken> batch = new ArrayList<>();
        batchList.add(batch);
        List<ErcToken> tokens = getErcTokens();
        for (ErcToken token : tokens) {
            if (token.isDirty()) {
                updateParams.add(token);
            }
            if (!(token.getTypeEnum() == ErcTypeEnum.ERC20 || token.getIsSupportErc721Enumeration())) {
                continue;
            }
            if (batch.size() == TOKEN_BATCH_SIZE) {
                // ?????????????????????????????????????????????????????????????????????
                batch = new ArrayList<>();
                batchList.add(batch);
            }
            // ???????????????
            batch.add(token);
        }
        // ??????????????????Token totalSupply
        batchList.forEach(b -> {
            // ?????????????????????
            List<ErcToken> res = tokenSubtractToList(b, getDestroyContracts());
            if (CollUtil.isNotEmpty(res)) {
                CountDownLatch latch = new CountDownLatch(res.size());
                for (ErcToken token : res) {
                    TOKEN_UPDATE_POOL.submit(() -> {
                        try {
                            // ??????????????????
                            BigInteger totalSupply = ercServiceImpl.getTotalSupply(token.getAddress());
                            totalSupply = totalSupply == null ? BigInteger.ZERO : totalSupply;
                            if (ObjectUtil.isNull(token.getTotalSupply()) || !token.getTotalSupply().equalsIgnoreCase(totalSupply.toString())) {
                                TaskUtil.console("token[{}]??????????????????????????????????????????[{}]??????[{}]", token.getAddress(), token.getTotalSupply(), totalSupply);
                                // ?????????????????????????????????
                                token.setTotalSupply(totalSupply.toString());
                                updateParams.add(token);
                            }
                        } catch (Exception e) {
                            XxlJobHelper.log(StrUtil.format("???token[{}]????????????????????????", token.getAddress()));
                            log.error("????????????????????????", e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    log.error("", e);
                }
            }
        });
        if (!updateParams.isEmpty()) {
            // ??????????????????????????????????????????
            customTokenMapper.batchUpdateTokenTotalSupply(new ArrayList<>(updateParams));
            updateParams.forEach(token -> token.setDirty(false));
        }
        XxlJobHelper.log("????????????token?????????????????????");
        updateTokenHolderCount();
    }

    /**
     * ??????erc??????
     *
     * @param abstractEsRepository:
     * @param txSeq:
     * @param pageSize:
     * @return: java.util.List<com.platon.browser.elasticsearch.dto.ErcTx>
     * @date: 2021/12/7
     */
    private List<ErcTx> getErcList(AbstractEsRepository abstractEsRepository, Long txSeq, int pageSize) throws Exception {
        try {
            ESQueryBuilderConstructor constructor = new ESQueryBuilderConstructor();
            ESResult<ErcTx> queryResultFromES = new ESResult<>();
            constructor.setAsc("seq");
            constructor.setResult(new String[]{"seq", "from", "contract", "to"});
            ESQueryBuilders esQueryBuilders = new ESQueryBuilders();
            esQueryBuilders.listBuilders().add(QueryBuilders.rangeQuery("seq").gt(txSeq));
            constructor.must(esQueryBuilders);
            constructor.setUnmappedType("long");
            queryResultFromES = abstractEsRepository.search(constructor, ErcTx.class, 1, pageSize);
            return queryResultFromES.getRsData();
        } catch (Exception e) {
            log.error("??????erc????????????", e);
            throw e;
        }
    }

    /**
     * ??????erc20???token holder?????????
     *
     * @param :
     * @return: void
     * @date: 2021/12/17
     */
    private void incrementUpdateErc20TokenHolderBalance() throws Exception {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        try {
            int pageSize = Convert.toInt(XxlJobHelper.getJobParam(), 500);
            PointLog pointLog = pointLogMapper.selectByPrimaryKey(5);
            long oldPosition = Convert.toLong(pointLog.getPosition());
            TxErc20BakExample example = new TxErc20BakExample();
            example.setOrderByClause("id");
            example.createCriteria().andIdGreaterThan(oldPosition).andIdLessThanOrEqualTo(oldPosition + pageSize);
            List<TxErc20Bak> list = txErc20BakMapper.selectByExample(example);
            List<TokenHolder> updateParams = new ArrayList<>();
            TaskUtil.console("[erc20]???????????????[{}]????????????[{}]", pageSize, oldPosition);
            if (CollUtil.isEmpty(list)) {
                TaskUtil.console("[erc20]?????????[{}]???????????????", oldPosition);
                return;
            }
            HashMap<String, HashSet<String>> map = new HashMap();
            list.sort(Comparator.comparing(ErcTx::getSeq));
            list.forEach(v -> {
                if (map.containsKey(v.getContract())) {
                    // ???????????????0??????
                    if (!AddressUtil.isAddrZero(v.getTo())) {
                        map.get(v.getContract()).add(v.getTo());
                    }
                    if (!AddressUtil.isAddrZero(v.getFrom())) {
                        map.get(v.getContract()).add(v.getFrom());
                    }
                } else {
                    HashSet<String> addressSet = new HashSet<String>();
                    // ???????????????0??????
                    if (!AddressUtil.isAddrZero(v.getTo())) {
                        addressSet.add(v.getTo());
                    }
                    if (!AddressUtil.isAddrZero(v.getFrom())) {
                        addressSet.add(v.getFrom());
                    }
                    map.put(v.getContract(), addressSet);
                }
            });
            // ?????????????????????
            HashMap<String, HashSet<String>> res = subtractToMap(map, getDestroyContracts());
            if (MapUtil.isNotEmpty(res)) {
                // ???????????????
                res.forEach((contract, addressSet) -> {
                    addressSet.forEach(address -> {
                        try {
                            BigInteger balance = ercServiceImpl.getBalance(contract, ErcTypeEnum.ERC20, address, BigInteger.ZERO);
                            TokenHolder holder = new TokenHolder();
                            holder.setTokenAddress(contract);
                            holder.setAddress(address);
                            holder.setBalance(balance.toString());
                            updateParams.add(holder);
                            log.info("[erc20] token holder???????????????[{}]?????????[{}]??????[{}]", balance, contract, address);
                            try {
                                TimeUnit.MILLISECONDS.sleep(100);
                            } catch (InterruptedException interruptedException) {
                                interruptedException.printStackTrace();
                            }
                        } catch (Exception e) {
                            String msg = StrFormatter.format("??????[erc20] token holder???????????????,contract:{},address:{}", contract, address);
                            XxlJobHelper.log(msg);
                            log.warn(msg, e);
                        }
                    });
                });
            }
            if (CollUtil.isNotEmpty(updateParams)) {
                customTokenHolderMapper.batchUpdate(updateParams);
                TaskUtil.console("??????[erc20] token holder?????????{}", JSONUtil.toJsonStr(updateParams));
            }
            String newPosition = CollUtil.getLast(list).getId().toString();
            pointLog.setPosition(newPosition);
            pointLogMapper.updateByPrimaryKeySelective(pointLog);
            XxlJobHelper.log("??????[erc20] token holder???????????????????????????[{}]->[{}]", oldPosition, newPosition);
        } catch (Exception e) {
            log.error("??????token?????????????????????", e);
            throw e;
        }
    }

    /**
     * ??????erc721???token holder?????????
     *
     * @param :
     * @return: void
     * @date: 2021/12/17
     */
    private void incrementUpdateErc721TokenHolderBalance() throws Exception {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        try {
            int pageSize = Convert.toInt(XxlJobHelper.getJobParam(), 500);
            PointLog pointLog = pointLogMapper.selectByPrimaryKey(6);
            long oldPosition = Convert.toLong(pointLog.getPosition());
            TxErc721BakExample example = new TxErc721BakExample();
            example.setOrderByClause("id");
            example.createCriteria().andIdGreaterThan(oldPosition).andIdLessThanOrEqualTo(oldPosition + pageSize);
            List<TxErc721Bak> list = txErc721BakMapper.selectByExample(example);
            List<TokenHolder> updateParams = new ArrayList<>();
            TaskUtil.console("[erc721]???????????????[{}]????????????[{}]", pageSize, oldPosition);
            if (CollUtil.isEmpty(list)) {
                TaskUtil.console("[erc721]?????????[{}]???????????????", oldPosition);
                return;
            }
            HashMap<String, HashSet<String>> map = new HashMap();
            list.sort(Comparator.comparing(ErcTx::getSeq));
            list.forEach(v -> {
                if (map.containsKey(v.getContract())) {
                    // ???????????????0??????
                    if (!AddressUtil.isAddrZero(v.getTo())) {
                        map.get(v.getContract()).add(v.getTo());
                    }
                    if (!AddressUtil.isAddrZero(v.getFrom())) {
                        map.get(v.getContract()).add(v.getFrom());
                    }
                } else {
                    HashSet<String> addressSet = new HashSet<String>();
                    // ???????????????0??????
                    if (!AddressUtil.isAddrZero(v.getTo())) {
                        addressSet.add(v.getTo());
                    }
                    if (!AddressUtil.isAddrZero(v.getFrom())) {
                        addressSet.add(v.getFrom());
                    }
                    map.put(v.getContract(), addressSet);
                }
            });
            // ?????????????????????
            HashMap<String, HashSet<String>> res = subtractToMap(map, getDestroyContracts());
            if (MapUtil.isNotEmpty(res)) {
                // ???????????????
                res.forEach((contract, addressSet) -> {
                    addressSet.forEach(address -> {
                        try {
                            BigInteger balance = ercServiceImpl.getBalance(contract, ErcTypeEnum.ERC721, address, BigInteger.ZERO);
                            TokenHolder holder = new TokenHolder();
                            holder.setTokenAddress(contract);
                            holder.setAddress(address);
                            holder.setBalance(balance.toString());
                            updateParams.add(holder);
                            log.info("[erc721] token holder???????????????[{}]?????????[{}]??????[{}]", balance, contract, address);
                            try {
                                TimeUnit.MILLISECONDS.sleep(100);
                            } catch (InterruptedException interruptedException) {
                                interruptedException.printStackTrace();
                            }
                        } catch (Exception e) {
                            String msg = StrFormatter.format("??????[erc721] token holder???????????????,contract:{},address:{}", contract, address);
                            XxlJobHelper.log(msg);
                            log.warn(msg, e);
                        }
                    });
                });
            }
            if (CollUtil.isNotEmpty(updateParams)) {
                customTokenHolderMapper.batchUpdate(updateParams);
                TaskUtil.console("??????[erc721] token holder?????????{}", JSONUtil.toJsonStr(updateParams));
            }
            String newPosition = CollUtil.getLast(list).getId().toString();
            pointLog.setPosition(newPosition);
            pointLogMapper.updateByPrimaryKeySelective(pointLog);
            XxlJobHelper.log("??????[erc721] token holder???????????????????????????[{}]->[{}]", oldPosition, newPosition);
        } catch (Exception e) {
            log.error("??????token?????????????????????", e);
            throw e;
        }
    }

    /**
     * ??????erc1155???token holder?????????
     *
     * @param :
     * @return: void
     * @date: 2022/2/12
     */
    private void incrementUpdateErc1155TokenHolderBalance() throws Exception {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        try {
            int pageSize = Convert.toInt(XxlJobHelper.getJobParam(), 500);
            PointLog pointLog = pointLogMapper.selectByPrimaryKey(10);
            long oldPosition = Convert.toLong(pointLog.getPosition());
            TxErc1155BakExample example = new TxErc1155BakExample();
            example.setOrderByClause("id");
            example.createCriteria().andIdGreaterThan(oldPosition).andIdLessThanOrEqualTo(oldPosition + pageSize);
            List<TxErc1155Bak> list = txErc1155BakMapper.selectByExample(example);
            List<TokenHolder> updateParams = new ArrayList<>();
            TaskUtil.console("[erc1155]???????????????[{}]????????????[{}]", pageSize, oldPosition);
            if (CollUtil.isEmpty(list)) {
                TaskUtil.console("[erc1155]?????????[{}]???????????????", oldPosition);
                return;
            }
            HashMap<String, HashMap<String, String>> map = new HashMap();
            list.sort(Comparator.comparing(ErcTx::getSeq));
            list.forEach(v -> {
                //
                if (map.containsKey(v.getContract())) {
                    // ???????????????0??????
                    if (!AddressUtil.isAddrZero(v.getTo())) {
                        map.get(v.getContract()).put(v.getTo(), v.getTokenId());
                    }
                    if (!AddressUtil.isAddrZero(v.getFrom())) {
                        map.get(v.getContract()).put(v.getFrom(), v.getTokenId());
                    }
                } else {
                    HashMap<String, String> addressTokenMap = new HashMap<>();
                    // ???????????????0??????
                    if (!AddressUtil.isAddrZero(v.getTo())) {
                        addressTokenMap.put(v.getTo(), v.getTokenId());
                    }
                    if (!AddressUtil.isAddrZero(v.getFrom())) {
                        addressTokenMap.put(v.getFrom(), v.getTokenId());
                    }
                    map.put(v.getContract(), addressTokenMap);
                }
            });
            // ?????????????????????
            HashMap<String, HashMap<String, String>> res = subtractErc1155ToMap(map, getDestroyContracts());
            if (MapUtil.isNotEmpty(res)) {
                // ???????????????
                res.forEach((contract, addressMap) -> {
                    addressMap.forEach((address, tokenId) -> {
                        try {
                            BigInteger balance = ercServiceImpl.getBalance(contract, ErcTypeEnum.ERC1155, address, new BigInteger(tokenId));
                            TokenHolder holder = new TokenHolder();
                            holder.setTokenAddress(contract);
                            holder.setAddress(address);
                            holder.setBalance(balance.toString());
                            updateParams.add(holder);
                            log.info("[erc1155] token holder???????????????[{}]?????????[{}]??????[{}]", balance, contract, address);
                            try {
                                TimeUnit.MILLISECONDS.sleep(100);
                            } catch (InterruptedException interruptedException) {
                                interruptedException.printStackTrace();
                            }
                        } catch (Exception e) {
                            String msg = StrFormatter.format("??????[erc1155] token holder???????????????,contract:{},address:{}", contract, address);
                            XxlJobHelper.log(msg);
                            log.warn(msg, e);
                        }
                    });
                });
            }
            if (CollUtil.isNotEmpty(updateParams)) {
                customTokenHolderMapper.batchUpdate(updateParams);
                TaskUtil.console("??????[erc1155] token holder?????????{}", JSONUtil.toJsonStr(updateParams));
            }
            String newPosition = CollUtil.getLast(list).getId().toString();
            pointLog.setPosition(newPosition);
            pointLogMapper.updateByPrimaryKeySelective(pointLog);
            XxlJobHelper.log("??????[erc1155] token holder???????????????????????????[{}]->[{}]", oldPosition, newPosition);
        } catch (Exception e) {
            log.error("??????token?????????????????????", e);
            throw e;
        }
    }

    /**
     * ??????token????????????
     *
     * @param pageNum ????????????
     * @return void
     * @date 2021/2/2
     */
    private void updateToken721Inventory(int pageNum) {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        // ????????????
        int page = pageNum;
        // ????????????token??????????????????
        List<TokenInventoryWithBLOBs> batch = null;
        do {
            // ????????????????????????
            int batchNum = 0;
            // ?????????????????????
            AtomicInteger errorNum = new AtomicInteger(0);
            // ?????????????????????
            AtomicInteger updateNum = new AtomicInteger(0);
            try {
                int batchSize = Convert.toInt(XxlJobHelper.getJobParam(), 100);
                TokenInventoryExample condition = new TokenInventoryExample();
                condition.setOrderByClause(" id asc limit " + page * batchSize + "," + batchSize);
                condition.createCriteria().andRetryNumLessThan(tokenRetryNum);
                batch = token721InventoryMapper.selectByExampleWithBLOBs(condition);
                // ?????????????????????
                List<TokenInventoryWithBLOBs> res = tokenInventorySubtractToList(batch, getDestroyContracts());
                List<TokenInventoryWithBLOBs> updateParams = new ArrayList<>();
                if (CollUtil.isNotEmpty(res)) {
                    batchNum = res.size();
                    int finalPage = page;
                    res.forEach(inventory -> {
                        try {
                            if (StrUtil.isNotBlank(inventory.getTokenUrl())) {
                                Request request = new Request.Builder().url(inventory.getTokenUrl()).build();
                                Response response = CustomHttpClient.getOkHttpClient().newCall(request).execute();
                                if (response.code() == 200) {
                                    String resp = response.body().string();
                                    TokenInventoryWithBLOBs newTi = JSONUtil.toBean(resp, TokenInventoryWithBLOBs.class);
                                    newTi.setTokenId(inventory.getTokenId());
                                    newTi.setTokenAddress(inventory.getTokenAddress());
                                    boolean changed = false;
                                    // ??????????????????????????????????????????????????????
                                    if (ObjectUtil.isNull(inventory.getImage()) || !newTi.getImage().equals(inventory.getImage())) {
                                        inventory.setImage(newTi.getImage());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDescription()) || !newTi.getDescription().equals(inventory.getDescription())) {
                                        inventory.setDescription(newTi.getDescription());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getName()) || !newTi.getName().equals(inventory.getName())) {
                                        inventory.setName(newTi.getName());
                                        changed = true;
                                    }
                                    if (changed) {
                                        updateNum.getAndIncrement();
                                        inventory.setRetryNum(0);
                                        updateParams.add(inventory);
                                        log.info("token[{}]?????????????????????????????????,tokenURL[{}],tokenName[{}],tokenDesc[{}],tokenImage[{}]",
                                                inventory.getTokenAddress(),
                                                inventory.getTokenUrl(),
                                                inventory.getName(),
                                                inventory.getDescription(),
                                                inventory.getImage());
                                    }
                                } else {
                                    errorNum.getAndIncrement();
                                    inventory.setRetryNum(inventory.getRetryNum() + 1);
                                    updateParams.add(inventory);
                                    log.warn("http???????????????http?????????:{},http??????:{},???????????????:{},token_address:{},token_id:{},tokenURI:{},????????????:{}",
                                            response.code(),
                                            response.message(),
                                            pageNum,
                                            inventory.getTokenAddress(),
                                            inventory.getTokenId(),
                                            inventory.getTokenUrl(),
                                            inventory.getRetryNum());
                                }
                            } else {
                                errorNum.getAndIncrement();
                                inventory.setRetryNum(inventory.getRetryNum() + 1);
                                updateParams.add(inventory);
                                String msg = StrUtil.format("??????TokenURI??????,???????????????:{},token_address???{},token_id:{},????????????:{}",
                                        finalPage,
                                        inventory.getTokenAddress(),
                                        inventory.getTokenId(),
                                        inventory.getRetryNum());
                                XxlJobHelper.log(msg);
                                log.warn(msg);
                            }
                        } catch (Exception e) {
                            errorNum.getAndIncrement();
                            inventory.setRetryNum(inventory.getRetryNum() + 1);
                            updateParams.add(inventory);
                            log.warn(StrUtil.format("????????????token??????????????????,???????????????:{},token_address???{},token_id:{},tokenURI:{},????????????:{}",
                                    finalPage,
                                    inventory.getTokenAddress(),
                                    inventory.getTokenId(),
                                    inventory.getTokenUrl(),
                                    inventory.getRetryNum()), e);
                        }
                    });
                }
                if (CollUtil.isNotEmpty(updateParams)) {
                    customToken721InventoryMapper.batchInsertOrUpdateSelective(updateParams, TokenInventory.Column.values());
                    XxlJobHelper.log("????????????token????????????{}", JSONUtil.toJsonStr(updateParams));
                }
                String msg = StrUtil.format("????????????token????????????:???????????????:{},?????????????????????{},??????????????????:{},?????????????????????:{},??????????????????:{}", page, batch.size(), batchNum, updateNum.get(), errorNum.get());
                XxlJobHelper.log(msg);
                log.info(msg);
            } catch (Exception e) {
                log.error(StrUtil.format("????????????token??????????????????,???????????????:{}", page), e);
            } finally {
                page++;
            }
        } while (CollUtil.isNotEmpty(batch));
    }

    /**
     * ??????token????????????
     *
     * @param pageNum ????????????
     * @return void
     * @date 2022/2/14
     */
    private void updateToken1155Inventory(int pageNum) {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        // ????????????
        int page = pageNum;
        // ????????????token??????????????????
        List<Token1155InventoryWithBLOBs> batch = null;
        do {
            // ????????????????????????
            int batchNum = 0;
            // ?????????????????????
            AtomicInteger errorNum = new AtomicInteger(0);
            // ?????????????????????
            AtomicInteger updateNum = new AtomicInteger(0);
            try {
                int batchSize = Convert.toInt(XxlJobHelper.getJobParam(), 100);
                Token1155InventoryExample condition = new Token1155InventoryExample();
                condition.setOrderByClause(" id asc limit " + page * batchSize + "," + batchSize);
                condition.createCriteria().andRetryNumLessThan(tokenRetryNum);
                batch = token1155InventoryMapper.selectByExampleWithBLOBs(condition);
                // ?????????????????????
                List<Token1155InventoryWithBLOBs> res = token1155InventorySubtractToList(batch, getDestroyContracts());
                List<Token1155InventoryWithBLOBs> updateParams = new ArrayList<>();
                if (CollUtil.isNotEmpty(res)) {
                    batchNum = res.size();
                    int finalPage = page;
                    res.forEach(inventory -> {
                        try {
                            if (StrUtil.isNotBlank(inventory.getTokenUrl())) {
                                Request request = new Request.Builder().url(inventory.getTokenUrl()).build();
                                Response response = CustomHttpClient.getOkHttpClient().newCall(request).execute();
                                if (response.code() == 200) {
                                    String resp = response.body().string();
                                    Token1155InventoryWithBLOBs newTi = JSONUtil.toBean(resp, Token1155InventoryWithBLOBs.class);
                                    newTi.setTokenId(inventory.getTokenId());
                                    newTi.setTokenAddress(inventory.getTokenAddress());
                                    boolean changed = false;
                                    // ??????????????????????????????????????????????????????
                                    if (ObjectUtil.isNull(inventory.getImage()) || !newTi.getImage().equals(inventory.getImage())) {
                                        inventory.setImage(newTi.getImage());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDescription()) || !newTi.getDescription().equals(inventory.getDescription())) {
                                        inventory.setDescription(newTi.getDescription());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getName()) || !newTi.getName().equals(inventory.getName())) {
                                        inventory.setName(newTi.getName());
                                        changed = true;
                                    }
                                    if (changed) {
                                        updateNum.getAndIncrement();
                                        inventory.setRetryNum(0);
                                        updateParams.add(inventory);
                                        log.info("token[{}]?????????????????????????????????,tokenURL[{}],tokenName[{}],tokenDesc[{}],tokenImage[{}]",
                                                inventory.getTokenAddress(),
                                                inventory.getTokenUrl(),
                                                inventory.getName(),
                                                inventory.getDescription(),
                                                inventory.getImage());
                                    }
                                } else {
                                    errorNum.getAndIncrement();
                                    inventory.setRetryNum(inventory.getRetryNum() + 1);
                                    updateParams.add(inventory);
                                    log.warn("http???????????????http?????????:{},http??????:{},???????????????:{},token_address:{},token_id:{},tokenURI:{},????????????:{}",
                                            response.code(),
                                            response.message(),
                                            pageNum,
                                            inventory.getTokenAddress(),
                                            inventory.getTokenId(),
                                            inventory.getTokenUrl(),
                                            inventory.getRetryNum());
                                }
                            } else {
                                errorNum.getAndIncrement();
                                inventory.setRetryNum(inventory.getRetryNum() + 1);
                                updateParams.add(inventory);
                                String msg = StrUtil.format("??????TokenURI??????,???????????????:{},token_address???{},token_id:{},????????????:{}",
                                        finalPage,
                                        inventory.getTokenAddress(),
                                        inventory.getTokenId(),
                                        inventory.getRetryNum());
                                XxlJobHelper.log(msg);
                                log.warn(msg);
                            }
                        } catch (Exception e) {
                            errorNum.getAndIncrement();
                            inventory.setRetryNum(inventory.getRetryNum() + 1);
                            updateParams.add(inventory);
                            log.warn(StrUtil.format("????????????token??????????????????,???????????????:{},token_address???{},token_id:{},tokenURI:{},????????????:{}",
                                    finalPage,
                                    inventory.getTokenAddress(),
                                    inventory.getTokenId(),
                                    inventory.getTokenUrl(),
                                    inventory.getRetryNum()), e);
                        }
                    });
                }
                if (CollUtil.isNotEmpty(updateParams)) {
                    customToken1155InventoryMapper.batchInsertOrUpdateSelective(updateParams, Token1155Inventory.Column.values());
                    XxlJobHelper.log("????????????token????????????{}", JSONUtil.toJsonStr(updateParams));
                }
                String msg = StrUtil.format("????????????token????????????:???????????????:{},?????????????????????{},??????????????????:{},?????????????????????:{},??????????????????:{}", page, batch.size(), batchNum, updateNum.get(), errorNum.get());
                XxlJobHelper.log(msg);
                log.info(msg);
            } catch (Exception e) {
                log.error(StrUtil.format("????????????token??????????????????,???????????????:{}", page), e);
            } finally {
                page++;
            }
        } while (CollUtil.isNotEmpty(batch));
    }

    /**
     * ??????token????????????=>????????????
     *
     * @return void
     * @date 2021/4/26
     */
    private void cronIncrementUpdateToken721Inventory() {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        // ????????????????????????
        int batchNum = 0;
        // ?????????????????????
        AtomicInteger errorNum = new AtomicInteger(0);
        // ?????????????????????
        AtomicInteger updateNum = new AtomicInteger(0);
        PointLog pointLog = pointLogMapper.selectByPrimaryKey(7);
        Long oldPosition = Convert.toLong(pointLog.getPosition());
        int batchSize = Convert.toInt(XxlJobHelper.getJobParam(), 100);
        XxlJobHelper.log("???????????????[{}]????????????[{}]", batchSize, oldPosition);
        try {
            TokenInventoryExample condition = new TokenInventoryExample();
            condition.setOrderByClause("id");
            condition.createCriteria().andIdGreaterThan(oldPosition).andIdLessThanOrEqualTo(oldPosition + batchSize).andRetryNumLessThan(tokenRetryNum);
            // ????????????token??????????????????
            List<TokenInventoryWithBLOBs> batch = token721InventoryMapper.selectByExampleWithBLOBs(condition);
            if (CollUtil.isNotEmpty(batch)) {
                List<TokenInventoryWithBLOBs> res = tokenInventorySubtractToList(batch, getDestroyContracts());
                List<TokenInventoryWithBLOBs> updateParams = new ArrayList<>();
                if (CollUtil.isNotEmpty(res)) {
                    batchNum = res.size();
                    res.forEach(inventory -> {
                        try {
                            if (StrUtil.isNotBlank(inventory.getTokenUrl())) {
                                Request request = new Request.Builder().url(inventory.getTokenUrl()).build();
                                Response response = CustomHttpClient.getOkHttpClient().newCall(request).execute();
                                if (response.code() == 200) {
                                    String resp = response.body().string();
                                    TokenInventoryWithBLOBs newTi = JSONUtil.toBean(resp, TokenInventoryWithBLOBs.class);
                                    newTi.setTokenId(inventory.getTokenId());
                                    newTi.setTokenAddress(inventory.getTokenAddress());
                                    boolean changed = false;
                                    // ??????????????????????????????????????????????????????
                                    if (ObjectUtil.isNull(inventory.getImage()) || !newTi.getImage().equals(inventory.getImage())) {
                                        inventory.setImage(newTi.getImage());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDescription()) || !newTi.getDescription().equals(inventory.getDescription())) {
                                        inventory.setDescription(newTi.getDescription());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getName()) || !newTi.getName().equals(inventory.getName())) {
                                        inventory.setName(newTi.getName());
                                        changed = true;
                                    }
                                    if (changed) {
                                        updateNum.getAndIncrement();
                                        inventory.setRetryNum(0);
                                        updateParams.add(inventory);
                                        String msg = StrUtil.format("token[{}]?????????????????????????????????,tokenURL[{}],tokenName[{}],tokenDesc[{}],tokenImage[{}]",
                                                inventory.getTokenAddress(),
                                                inventory.getTokenUrl(),
                                                inventory.getName(),
                                                inventory.getDescription(),
                                                inventory.getImage());
                                        XxlJobHelper.log(msg);
                                        log.info(msg);
                                    }
                                } else {
                                    errorNum.getAndIncrement();
                                    inventory.setRetryNum(inventory.getRetryNum() + 1);
                                    updateParams.add(inventory);
                                    String msg = StrUtil.format("http???????????????http?????????:{},http??????:{},??????:{},token_address:{},token_id:{},tokenURI:{},????????????:{}",
                                            response.code(),
                                            response.message(),
                                            oldPosition,
                                            inventory.getTokenAddress(),
                                            inventory.getTokenId(),
                                            inventory.getTokenUrl(),
                                            inventory.getRetryNum());
                                    XxlJobHelper.log(msg);
                                    log.warn(msg);
                                }
                            } else {
                                errorNum.getAndIncrement();
                                inventory.setRetryNum(inventory.getRetryNum() + 1);
                                updateParams.add(inventory);
                                String msg = StrUtil.format("??????TokenURI??????,??????:{},token_address???{},token_id:{},????????????:{}",
                                        oldPosition,
                                        inventory.getTokenAddress(),
                                        inventory.getTokenId(),
                                        inventory.getRetryNum());
                                XxlJobHelper.log(msg);
                                log.warn(msg);
                            }
                        } catch (Exception e) {
                            errorNum.getAndIncrement();
                            inventory.setRetryNum(inventory.getRetryNum() + 1);
                            updateParams.add(inventory);
                            log.warn(StrUtil.format("????????????token??????????????????,??????:{},token_address???{},token_id:{},tokenURI:{},????????????:{}",
                                    oldPosition,
                                    inventory.getTokenAddress(),
                                    inventory.getTokenId(),
                                    inventory.getTokenUrl(),
                                    inventory.getRetryNum()), e);
                        }
                    });
                }
                if (CollUtil.isNotEmpty(updateParams)) {
                    customToken721InventoryMapper.batchInsertOrUpdateSelective(updateParams, TokenInventory.Column.values());
                }
                TokenInventory lastTokenInventory = CollUtil.getLast(batch);
                String newPosition = Convert.toStr(lastTokenInventory.getId());
                pointLog.setPosition(newPosition);
                pointLogMapper.updateByPrimaryKeySelective(pointLog);
                String msg = StrUtil.format("????????????token????????????:?????????[{}]->[{}],?????????????????????:{},??????????????????:{},?????????????????????:{},??????????????????:{}", oldPosition, newPosition, batch.size(), batchNum, updateNum.get(), errorNum.get());
                XxlJobHelper.log(msg);
                log.info(msg);
            } else {
                XxlJobHelper.log("????????????token????????????????????????????????????????????????[{}]", oldPosition);
            }
        } catch (Exception e) {
            log.error(StrUtil.format("????????????token??????????????????,??????:{}", oldPosition), e);
        }
    }
    /**
     * ??????token1155????????????=>????????????
     *
     * @return void
     * @date 2022/2/14
     */
    /**
     * ??????token????????????=>????????????
     *
     * @return void
     * @date 2022/2/14
     */
    private void cronIncrementUpdateToken1155Inventory() {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        // ????????????????????????
        int batchNum = 0;
        // ?????????????????????
        AtomicInteger errorNum = new AtomicInteger(0);
        // ?????????????????????
        AtomicInteger updateNum = new AtomicInteger(0);
        PointLog pointLog = pointLogMapper.selectByPrimaryKey(11);
        Long oldPosition = Convert.toLong(pointLog.getPosition());
        int batchSize = Convert.toInt(XxlJobHelper.getJobParam(), 100);
        XxlJobHelper.log("???????????????[{}]????????????[{}]", batchSize, oldPosition);
        try {
            Token1155InventoryExample condition = new Token1155InventoryExample();
            condition.setOrderByClause("id");
            condition.createCriteria().andIdGreaterThan(oldPosition).andIdLessThanOrEqualTo(oldPosition + batchSize).andRetryNumLessThan(tokenRetryNum);
            // ????????????token??????????????????
            List<Token1155InventoryWithBLOBs> batch = token1155InventoryMapper.selectByExampleWithBLOBs(condition);
            if (CollUtil.isNotEmpty(batch)) {
                List<Token1155InventoryWithBLOBs> res = token1155InventorySubtractToList(batch, getDestroyContracts());
                List<Token1155InventoryWithBLOBs> updateParams = new ArrayList<>();
                if (CollUtil.isNotEmpty(res)) {
                    batchNum = res.size();
                    res.forEach(inventory -> {
                        try {
                            if (StrUtil.isNotBlank(inventory.getTokenUrl())) {
                                Request request = new Request.Builder().url(inventory.getTokenUrl()).build();
                                Response response = CustomHttpClient.getOkHttpClient().newCall(request).execute();
                                if (response.code() == 200) {
                                    String resp = response.body().string();
                                    Token1155InventoryWithBLOBs newTi = JSONUtil.toBean(resp, Token1155InventoryWithBLOBs.class);
                                    newTi.setTokenId(inventory.getTokenId());
                                    newTi.setTokenAddress(inventory.getTokenAddress());

                                    boolean changed = false;
                                    // ??????????????????????????????????????????????????????
                                    if (ObjectUtil.isNull(inventory.getImage()) || !newTi.getImage().equals(inventory.getImage())) {
                                        inventory.setImage(newTi.getImage());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDescription()) || !newTi.getDescription().equals(inventory.getDescription())) {
                                        inventory.setDescription(newTi.getDescription());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getName()) || !newTi.getName().equals(inventory.getName())) {
                                        inventory.setName(newTi.getName());
                                        changed = true;
                                    }
                                    if (changed) {
                                        updateNum.getAndIncrement();
                                        inventory.setRetryNum(0);
                                        updateParams.add(inventory);
                                        String msg = StrUtil.format("token[{}]?????????????????????????????????,tokenURL[{}],tokenName[{}],tokenDesc[{}],tokenImage[{}]",
                                                inventory.getTokenAddress(),
                                                inventory.getTokenUrl(),
                                                inventory.getName(),
                                                inventory.getDescription(),
                                                inventory.getImage());
                                        XxlJobHelper.log(msg);
                                        log.info(msg);
                                    }
                                } else {
                                    errorNum.getAndIncrement();
                                    inventory.setRetryNum(inventory.getRetryNum() + 1);
                                    updateParams.add(inventory);
                                    String msg = StrUtil.format("http???????????????http?????????:{},http??????:{},??????:{},token_address:{},token_id:{},tokenURI:{},????????????:{}",
                                            response.code(),
                                            response.message(),
                                            oldPosition,
                                            inventory.getTokenAddress(),
                                            inventory.getTokenId(),
                                            inventory.getTokenUrl(),
                                            inventory.getRetryNum());
                                    XxlJobHelper.log(msg);
                                    log.warn(msg);
                                }
                            } else {
                                errorNum.getAndIncrement();
                                inventory.setRetryNum(inventory.getRetryNum() + 1);
                                updateParams.add(inventory);
                                String msg = StrUtil.format("??????TokenURI??????,??????:{},token_address???{},token_id:{},????????????:{}",
                                        oldPosition,
                                        inventory.getTokenAddress(),
                                        inventory.getTokenId(),
                                        inventory.getRetryNum());
                                XxlJobHelper.log(msg);
                                log.warn(msg);
                            }
                        } catch (Exception e) {
                            errorNum.getAndIncrement();
                            inventory.setRetryNum(inventory.getRetryNum() + 1);
                            updateParams.add(inventory);
                            log.warn(StrUtil.format("????????????token??????????????????,??????:{},token_address???{},token_id:{},tokenURI:{},????????????:{}",
                                    oldPosition,
                                    inventory.getTokenAddress(),
                                    inventory.getTokenId(),
                                    inventory.getTokenUrl(),
                                    inventory.getRetryNum()), e);
                        }
                    });
                }
                if (CollUtil.isNotEmpty(updateParams)) {
                    customToken1155InventoryMapper.batchInsertOrUpdateSelective(updateParams, Token1155Inventory.Column.values());
                }
                Token1155Inventory lastTokenInventory = CollUtil.getLast(batch);
                String newPosition = Convert.toStr(lastTokenInventory.getId());
                pointLog.setPosition(newPosition);
                pointLogMapper.updateByPrimaryKeySelective(pointLog);
                String msg = StrUtil.format("????????????token????????????:?????????[{}]->[{}],?????????????????????:{},??????????????????:{},?????????????????????:{},??????????????????:{}", oldPosition, newPosition, batch.size(), batchNum, updateNum.get(), errorNum.get());
                XxlJobHelper.log(msg);
                log.info(msg);
            } else {
                XxlJobHelper.log("????????????token????????????????????????????????????????????????[{}]", oldPosition);
            }
        } catch (Exception e) {
            log.error(StrUtil.format("????????????token??????????????????,??????:{}", oldPosition), e);
        }
    }

    /**
     * ?????????????????????
     *
     * @param list:
     * @param destroyContracts:
     * @return: java.util.List<com.platon.browser.dao.entity.TokenInventory>
     * @date: 2022/2/14
     */
    private List<Token1155InventoryWithBLOBs> token1155InventorySubtractToList(List<Token1155InventoryWithBLOBs> list, Set<String> destroyContracts) {
        List<Token1155InventoryWithBLOBs> res = CollUtil.newArrayList();
        if (CollUtil.isNotEmpty(list)) {
            for (Token1155InventoryWithBLOBs tokenInventory : list) {
                if (!destroyContracts.contains(tokenInventory.getTokenAddress())) {
                    res.add(tokenInventory);
                }
            }
        }
        return res;
    }

    private void contractErc20DestroyUpdateBalance() {
        try {
            List<DestroyContract> tokenList = customTokenMapper.findDestroyContract(ErcTypeEnum.ERC20.getDesc());
            if (CollUtil.isNotEmpty(tokenList)) {
                List<TokenHolder> updateList = new ArrayList<>();
                for (DestroyContract destroyContract : tokenList) {
                    try {
                        BigInteger balance = ercServiceImpl.getErc20HistoryBalance(destroyContract.getTokenAddress(),
                                destroyContract.getAccount(),
                                BigInteger.valueOf(destroyContract.getContractDestroyBlock() - 1));
                        TokenHolder tokenHolder = new TokenHolder();
                        tokenHolder.setTokenAddress(destroyContract.getTokenAddress());
                        tokenHolder.setAddress(destroyContract.getAccount());
                        tokenHolder.setBalance(balance.toString());
                        updateList.add(tokenHolder);
                    } catch (Exception e) {
                        log.error(StrUtil.format("????????????erc20??????[{}]??????[{}]??????????????????", destroyContract.getTokenAddress(), destroyContract.getAccount()), e);
                    }
                }
                if (CollUtil.isNotEmpty(updateList)) {
                    customTokenHolderMapper.batchUpdate(updateList);
                    Set<String> destroyContractSet = updateList.stream().map(TokenHolderKey::getTokenAddress).collect(Collectors.toSet());
                    for (String destroyContract : destroyContractSet) {
                        Token token = new Token();
                        token.setAddress(destroyContract);
                        token.setContractDestroyUpdate(true);
                        tokenMapper.updateByPrimaryKeySelective(token);
                    }
                }
            }
        } catch (Exception e) {
            log.error("??????????????????erc20??????????????????", e);
        }
    }

    /**
     * ?????????erc721????????????
     *
     * @param :
     * @return: void
     * @date: 2021/9/27
     */
    private void contractErc721DestroyUpdateBalance() {
        try {
            List<String> contractErc721Destroys = customAddressMapper.findContractDestroy(AddressTypeEnum.ERC721_EVM_CONTRACT.getCode());
            if (CollUtil.isNotEmpty(contractErc721Destroys)) {
                for (String tokenAddress : contractErc721Destroys) {
                    List<Erc721ContractDestroyBalanceVO> list = customToken721InventoryMapper.findErc721ContractDestroyBalance(tokenAddress);
                    Page<CustomTokenHolder> ids = customTokenHolderMapper.selectERC721Holder(tokenAddress);
                    List<TokenHolder> updateParams = new ArrayList<>();
                    StringBuilder res = new StringBuilder();
                    for (CustomTokenHolder tokenHolder : ids) {
                        List<Erc721ContractDestroyBalanceVO> filterList = list.stream().filter(v -> v.getOwner().equalsIgnoreCase(tokenHolder.getAddress())).collect(Collectors.toList());
                        int balance = 0;
                        if (CollUtil.isNotEmpty(filterList)) {
                            balance = filterList.get(0).getNum();
                        }
                        if (!tokenHolder.getBalance().equalsIgnoreCase(cn.hutool.core.convert.Convert.toStr(balance))) {
                            TokenHolder updateTokenHolder = new TokenHolder();
                            updateTokenHolder.setTokenAddress(tokenHolder.getTokenAddress());
                            updateTokenHolder.setAddress(tokenHolder.getAddress());
                            updateTokenHolder.setBalance(cn.hutool.core.convert.Convert.toStr(balance));
                            updateParams.add(updateTokenHolder);
                            res.append(StrUtil.format("[??????{}?????????{}->{}] ", tokenHolder.getAddress(), tokenHolder.getBalance(), cn.hutool.core.convert.Convert.toStr(balance)));
                        }
                    }
                    if (CollUtil.isNotEmpty(updateParams)) {
                        customTokenHolderMapper.batchUpdate(updateParams);
                        log.info("?????????erc721[{}]??????????????????????????????{}", tokenAddress, res.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("?????????erc721??????????????????", e);
        }
    }

    /**
     * ?????????erc1155????????????
     *
     * @param :
     * @return: void
     * @date: 2022/2/14
     */
    private void contractErc1155DestroyUpdateBalance() {
        try {
            List<String> contractErc1155Destroys = customAddressMapper.findContractDestroy(AddressTypeEnum.ERC1155_EVM_CONTRACT.getCode());
            if (CollUtil.isNotEmpty(contractErc1155Destroys)) {
                for (String tokenAddress : contractErc1155Destroys) {
                    List<Erc1155ContractDestroyBalanceVO> list = customToken1155InventoryMapper.findErc1155ContractDestroyBalance(tokenAddress);
                    Page<CustomTokenHolder> ids = customTokenHolderMapper.selectERC1155Holder(tokenAddress);
                    List<TokenHolder> updateParams = new ArrayList<>();
                    StringBuilder res = new StringBuilder();
                    for (CustomTokenHolder tokenHolder : ids) {
                        List<Erc1155ContractDestroyBalanceVO> filterList = list.stream().filter(v -> v.getOwner().equalsIgnoreCase(tokenHolder.getAddress())).collect(Collectors.toList());
                        int balance = 0;
                        if (CollUtil.isNotEmpty(filterList)) {
                            balance = filterList.get(0).getNum();
                        }
                        if (!tokenHolder.getBalance().equalsIgnoreCase(cn.hutool.core.convert.Convert.toStr(balance))) {
                            TokenHolder updateTokenHolder = new TokenHolder();
                            updateTokenHolder.setTokenAddress(tokenHolder.getTokenAddress());
                            updateTokenHolder.setAddress(tokenHolder.getAddress());
                            updateTokenHolder.setBalance(cn.hutool.core.convert.Convert.toStr(balance));
                            updateParams.add(updateTokenHolder);
                            res.append(StrUtil.format("[??????{}?????????{}->{}] ", tokenHolder.getAddress(), tokenHolder.getBalance(), cn.hutool.core.convert.Convert.toStr(balance)));
                        }
                    }
                    if (CollUtil.isNotEmpty(updateParams)) {
                        customTokenHolderMapper.batchUpdate(updateParams);
                        log.info("?????????erc1155[{}]??????????????????????????????{}", tokenAddress, res.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("?????????erc1155??????????????????", e);
        }
    }

    /**
     * ??????ercToken
     *
     * @param :
     * @return: java.util.List<com.platon.browser.v0152.bean.ErcToken>
     * @date: 2021/11/30
     */
    private List<ErcToken> getErcTokens() {
        List<ErcToken> ercTokens = new ArrayList<>();
        List<Token> tokens = tokenMapper.selectByExample(null);
        tokens.forEach(token -> {
            ErcToken et = new ErcToken();
            BeanUtils.copyProperties(token, et);
            ErcTypeEnum typeEnum = ErcTypeEnum.valueOf(token.getType().toUpperCase());
            et.setTypeEnum(typeEnum);
            ercTokens.add(et);
        });
        return ercTokens;
    }

    /**
     * ?????????????????????
     *
     * @param :
     * @return: java.util.Set<java.lang.String>
     * @date: 2021/11/30
     */
    private Set<String> getDestroyContracts() {
        Set<String> destroyContracts = new HashSet<>();
        List<String> list = customAddressMapper.findContractDestroy(null);
        destroyContracts.addAll(list);
        return destroyContracts;
    }

    /**
     * ?????????????????????
     *
     * @param ercTokens:
     * @param destroyContracts:
     * @return: java.util.List<com.platon.browser.v0152.bean.ErcToken>
     * @date: 2021/10/14
     */
    private List<ErcToken> tokenSubtractToList(List<ErcToken> ercTokens, Set<String> destroyContracts) {
        List<ErcToken> res = CollUtil.newArrayList();
        for (ErcToken ercToken : ercTokens) {
            if (!destroyContracts.contains(ercToken.getAddress())) {
                res.add(ercToken);
            }
        }
        return res;
    }

    /**
     * ??????token???????????????????????????
     *
     * @param
     * @return void
     * @date 2021/3/17
     */
    private void updateTokenHolderCount() {
        List<Token> updateTokenList = new ArrayList<>();
        List<TokenHolderCount> list = customTokenHolderMapper.findTokenHolderCount();
        List<Token> tokenList = tokenMapper.selectByExample(null);
        if (CollUtil.isNotEmpty(list) && CollUtil.isNotEmpty(tokenList)) {
            list.forEach(tokenHolderCount -> {
                tokenList.forEach(token -> {
                    if (token.getAddress().equalsIgnoreCase(tokenHolderCount.getTokenAddress()) && !token.getHolder().equals(tokenHolderCount.getTokenHolderCount())) {
                        token.setHolder(tokenHolderCount.getTokenHolderCount());
                        updateTokenList.add(token);
                        TaskUtil.console("??????token[{}]???????????????????????????[{}]", token.getAddress(), token.getHolder());
                    }
                });
            });
        }
        if (CollUtil.isNotEmpty(updateTokenList)) {
            customTokenMapper.batchUpdateTokenHolder(updateTokenList);
        }
        XxlJobHelper.log("??????token?????????????????????????????????");
    }

    private HashMap<String, HashSet<String>> subtractToMap(HashMap<String, HashSet<String>> map, Set<String> destroyContracts) {
        HashMap<String, HashSet<String>> res = CollUtil.newHashMap();
        if (CollUtil.isNotEmpty(map)) {
            for (Map.Entry<String, HashSet<String>> entry : map.entrySet()) {
                if (!destroyContracts.contains(entry.getKey())) {
                    res.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return res;
    }

    /**
     * ??????erc1155??????????????????
     *
     * @param map
     * @param destroyContracts
     * @return
     */
    private HashMap<String, HashMap<String, String>> subtractErc1155ToMap(HashMap<String, HashMap<String, String>> map, Set<String> destroyContracts) {
        HashMap<String, HashMap<String, String>> res = CollUtil.newHashMap();
        if (CollUtil.isNotEmpty(map)) {
            for (Map.Entry<String, HashMap<String, String>> entry : map.entrySet()) {
                if (!destroyContracts.contains(entry.getKey())) {
                    res.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return res;
    }

    /**
     * ?????????????????????
     *
     * @param list:
     * @param destroyContracts:
     * @return: java.util.List<com.platon.browser.dao.entity.TokenHolder>
     * @date: 2021/10/14
     */
    private List<TokenHolder> subtractToList(List<TokenHolder> list, Set<String> destroyContracts) {
        List<TokenHolder> res = CollUtil.newArrayList();
        if (CollUtil.isNotEmpty(list)) {
            for (TokenHolder tokenHolder : list) {
                if (!destroyContracts.contains(tokenHolder.getTokenAddress())) {
                    res.add(tokenHolder);
                }
            }
        }
        return res;
    }

    /**
     * ?????????????????????
     *
     * @param list:
     * @param destroyContracts:
     * @return: java.util.List<com.platon.browser.dao.entity.TokenInventory>
     * @date: 2021/10/15
     */
    private List<TokenInventoryWithBLOBs> tokenInventorySubtractToList(List<TokenInventoryWithBLOBs> list, Set<String> destroyContracts) {
        List<TokenInventoryWithBLOBs> res = CollUtil.newArrayList();
        if (CollUtil.isNotEmpty(list)) {
            for (TokenInventoryWithBLOBs tokenInventory : list) {
                if (!destroyContracts.contains(tokenInventory.getTokenAddress())) {
                    res.add(tokenInventory);
                }
            }
        }
        return res;
    }

    /**
     * ?????????????????????????????????
     *
     * @param :
     * @return: void
     * @date: 2022/2/10
     */
    private void updateToken721InventoryInfo() {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        // ????????????????????????
        int batchNum = 0;
        // ?????????????????????
        AtomicInteger errorNum = new AtomicInteger(0);
        // ?????????????????????
        AtomicInteger updateNum = new AtomicInteger(0);
        PointLog pointLog = pointLogMapper.selectByPrimaryKey(8);
        Long oldPosition = Convert.toLong(pointLog.getPosition());
        int batchSize = Convert.toInt(XxlJobHelper.getJobParam(), 100);
        XxlJobHelper.log("???????????????[{}]????????????[{}]", batchSize, oldPosition);
        try {
            List<TokenInventoryWithBLOBs> batch = customToken721InventoryMapper.findDestroyContracts(oldPosition, oldPosition + batchSize, tokenRetryNum);
            if (CollUtil.isNotEmpty(batch)) {
                List<TokenInventoryWithBLOBs> updateParams = new ArrayList<>();
                if (CollUtil.isNotEmpty(batch)) {
                    batchNum = batch.size();
                    batch.forEach(inventory -> {
                        try {
                            if (StrUtil.isNotBlank(inventory.getTokenUrl())) {
                                Request request = new Request.Builder().url(inventory.getTokenUrl()).build();
                                Response response = CustomHttpClient.getOkHttpClient().newCall(request).execute();
                                if (response.code() == 200) {
                                    String resp = response.body().string();
                                    TokenInventoryWithBLOBs newTi = JSONUtil.toBean(resp, TokenInventoryWithBLOBs.class);
                                    newTi.setTokenId(inventory.getTokenId());
                                    newTi.setTokenAddress(inventory.getTokenAddress());
                                    boolean changed = false;
                                    // ??????????????????????????????????????????????????????
                                    if (ObjectUtil.isNull(inventory.getImage()) || !newTi.getImage().equals(inventory.getImage())) {
                                        inventory.setImage(newTi.getImage());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDescription()) || !newTi.getDescription().equals(inventory.getDescription())) {
                                        inventory.setDescription(newTi.getDescription());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getName()) || !newTi.getName().equals(inventory.getName())) {
                                        inventory.setName(newTi.getName());
                                        changed = true;
                                    }
                                    if (changed) {
                                        updateNum.getAndIncrement();
                                        inventory.setRetryNum(0);
                                        updateParams.add(inventory);
                                        String msg = StrUtil.format("token[{}]?????????????????????????????????,tokenURL[{}],tokenName[{}],tokenDesc[{}],tokenImage[{}]",
                                                inventory.getTokenAddress(),
                                                inventory.getTokenUrl(),
                                                inventory.getName(),
                                                inventory.getDescription(),
                                                inventory.getImage());
                                        XxlJobHelper.log(msg);
                                        log.info(msg);
                                    }
                                } else {
                                    errorNum.getAndIncrement();
                                    inventory.setRetryNum(inventory.getRetryNum() + 1);
                                    updateParams.add(inventory);
                                    String msg = StrUtil.format("http???????????????http?????????:{},http??????:{},??????:{},token_address:{},token_id:{},tokenURI:{},????????????:{}",
                                            response.code(),
                                            response.message(),
                                            oldPosition,
                                            inventory.getTokenAddress(),
                                            inventory.getTokenId(),
                                            inventory.getTokenUrl(),
                                            inventory.getRetryNum());
                                    XxlJobHelper.log(msg);
                                    log.warn(msg);
                                }
                            } else {
                                errorNum.getAndIncrement();
                                inventory.setRetryNum(inventory.getRetryNum() + 1);
                                updateParams.add(inventory);
                                String msg = StrUtil.format("??????TokenURI??????,??????:{},token_address???{},token_id:{},????????????:{}",
                                        oldPosition,
                                        inventory.getTokenAddress(),
                                        inventory.getTokenId(),
                                        inventory.getRetryNum());
                                XxlJobHelper.log(msg);
                                log.warn(msg);
                            }
                        } catch (Exception e) {
                            errorNum.getAndIncrement();
                            inventory.setRetryNum(inventory.getRetryNum() + 1);
                            updateParams.add(inventory);
                            log.warn(StrUtil.format("???????????????-??????token??????????????????,??????:{},token_address???{},token_id:{},tokenURI:{},????????????:{}",
                                    oldPosition,
                                    inventory.getTokenAddress(),
                                    inventory.getTokenId(),
                                    inventory.getTokenUrl(),
                                    inventory.getRetryNum()), e);
                        }
                    });
                }
                if (CollUtil.isNotEmpty(updateParams)) {
                    customToken721InventoryMapper.batchInsertOrUpdateSelective(updateParams, TokenInventory.Column.values());
                }
                TokenInventory lastTokenInventory = CollUtil.getLast(batch);
                String newPosition = Convert.toStr(lastTokenInventory.getId());
                pointLog.setPosition(newPosition);
                pointLogMapper.updateByPrimaryKeySelective(pointLog);
                String msg = StrUtil.format("???????????????-??????token????????????:?????????[{}]->[{}],?????????????????????:{},??????????????????:{},?????????????????????:{},??????????????????:{}",
                        oldPosition,
                        newPosition,
                        batch.size(),
                        batchNum,
                        updateNum.get(),
                        errorNum.get());
                XxlJobHelper.log(msg);
                log.info(msg);
            } else {
                XxlJobHelper.log("???????????????-??????token????????????????????????????????????????????????[{}]", oldPosition);
            }
        } catch (Exception e) {
            log.error(StrUtil.format("???????????????-??????token??????????????????,??????:{}", oldPosition), e);
        }
    }


    /**
     * ?????????????????????????????????
     *
     * @param :
     * @return: void
     * @date: 2022/2/14
     */
    private void updateToken1155InventoryInfo() {
        // ???????????????????????????????????????
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        // ????????????????????????
        int batchNum = 0;
        // ?????????????????????
        AtomicInteger errorNum = new AtomicInteger(0);
        // ?????????????????????
        AtomicInteger updateNum = new AtomicInteger(0);
        PointLog pointLog = pointLogMapper.selectByPrimaryKey(12);
        Long oldPosition = Convert.toLong(pointLog.getPosition());
        int batchSize = Convert.toInt(XxlJobHelper.getJobParam(), 100);
        XxlJobHelper.log("???????????????[{}]????????????[{}]", batchSize, oldPosition);
        try {
            List<Token1155InventoryWithBLOBs> batch = customToken1155InventoryMapper.findDestroyContracts(oldPosition, oldPosition + batchSize, tokenRetryNum);
            if (CollUtil.isNotEmpty(batch)) {
                List<Token1155InventoryWithBLOBs> updateParams = new ArrayList<>();
                if (CollUtil.isNotEmpty(batch)) {
                    batchNum = batch.size();
                    batch.forEach(inventory -> {
                        try {
                            if (StrUtil.isNotBlank(inventory.getTokenUrl())) {
                                Request request = new Request.Builder().url(inventory.getTokenUrl()).build();
                                Response response = CustomHttpClient.getOkHttpClient().newCall(request).execute();
                                if (response.code() == 200) {
                                    String resp = response.body().string();
                                    Token1155InventoryWithBLOBs newTi = JSONUtil.toBean(resp, Token1155InventoryWithBLOBs.class);
                                    newTi.setTokenId(inventory.getTokenId());
                                    newTi.setTokenAddress(inventory.getTokenAddress());
                                    boolean changed = false;
                                    // ??????????????????????????????????????????????????????
                                    if (ObjectUtil.isNull(inventory.getImage()) || !newTi.getImage().equals(inventory.getImage())) {
                                        inventory.setImage(newTi.getImage());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDescription()) || !newTi.getDescription().equals(inventory.getDescription())) {
                                        inventory.setDescription(newTi.getDescription());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getName()) || !newTi.getName().equals(inventory.getName())) {
                                        inventory.setName(newTi.getName());
                                        changed = true;
                                    }
                                    if (changed) {
                                        updateNum.getAndIncrement();
                                        inventory.setRetryNum(0);
                                        updateParams.add(inventory);
                                        String msg = StrUtil.format("token[{}]?????????????????????????????????,tokenURL[{}],tokenName[{}],tokenDesc[{}],tokenImage[{}]",
                                                inventory.getTokenAddress(),
                                                inventory.getTokenUrl(),
                                                inventory.getName(),
                                                inventory.getDescription(),
                                                inventory.getImage());
                                        XxlJobHelper.log(msg);
                                        log.info(msg);
                                    }
                                } else {
                                    errorNum.getAndIncrement();
                                    inventory.setRetryNum(inventory.getRetryNum() + 1);
                                    updateParams.add(inventory);
                                    String msg = StrUtil.format("http???????????????http?????????:{},http??????:{},??????:{},token_address:{},token_id:{},tokenURI:{},????????????:{}",
                                            response.code(),
                                            response.message(),
                                            oldPosition,
                                            inventory.getTokenAddress(),
                                            inventory.getTokenId(),
                                            inventory.getTokenUrl(),
                                            inventory.getRetryNum());
                                    XxlJobHelper.log(msg);
                                    log.warn(msg);
                                }
                            } else {
                                errorNum.getAndIncrement();
                                inventory.setRetryNum(inventory.getRetryNum() + 1);
                                updateParams.add(inventory);
                                String msg = StrUtil.format("??????TokenURI??????,??????:{},token_address???{},token_id:{},????????????:{}",
                                        oldPosition,
                                        inventory.getTokenAddress(),
                                        inventory.getTokenId(),
                                        inventory.getRetryNum());
                                XxlJobHelper.log(msg);
                                log.warn(msg);
                            }
                        } catch (Exception e) {
                            errorNum.getAndIncrement();
                            inventory.setRetryNum(inventory.getRetryNum() + 1);
                            updateParams.add(inventory);
                            log.warn(StrUtil.format("???????????????-??????token??????????????????,??????:{},token_address???{},token_id:{},tokenURI:{},????????????:{}",
                                    oldPosition,
                                    inventory.getTokenAddress(),
                                    inventory.getTokenId(),
                                    inventory.getTokenUrl(),
                                    inventory.getRetryNum()), e);
                        }
                    });
                }
                if (CollUtil.isNotEmpty(updateParams)) {
                    customToken1155InventoryMapper.batchInsertOrUpdateSelective(updateParams, Token1155Inventory.Column.values());
                }
                Token1155Inventory lastTokenInventory = CollUtil.getLast(batch);
                String newPosition = Convert.toStr(lastTokenInventory.getId());
                pointLog.setPosition(newPosition);
                pointLogMapper.updateByPrimaryKeySelective(pointLog);
                String msg = StrUtil.format("???????????????-??????token????????????:?????????[{}]->[{}],?????????????????????:{},??????????????????:{},?????????????????????:{},??????????????????:{}",
                        oldPosition,
                        newPosition,
                        batch.size(),
                        batchNum,
                        updateNum.get(),
                        errorNum.get());
                XxlJobHelper.log(msg);
                log.info(msg);
            } else {
                XxlJobHelper.log("???????????????-??????token????????????????????????????????????????????????[{}]", oldPosition);
            }
        } catch (Exception e) {
            log.error(StrUtil.format("???????????????-??????token??????????????????,??????:{}", oldPosition), e);
        }
    }

}
