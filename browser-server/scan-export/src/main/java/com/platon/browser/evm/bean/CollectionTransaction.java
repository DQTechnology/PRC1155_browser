package com.platon.browser.evm.bean;

import com.platon.contracts.ppos.dto.common.ErrorCode;
import com.platon.protocol.core.DefaultBlockParameter;
import com.platon.protocol.core.methods.response.Log;
import com.platon.protocol.core.methods.response.PlatonGetCode;
import com.platon.rlp.solidity.RlpDecoder;
import com.platon.rlp.solidity.RlpList;
import com.platon.rlp.solidity.RlpString;
import com.platon.rlp.solidity.RlpType;
import com.platon.utils.Numeric;
import com.platon.browser.bean.Receipt;
import com.platon.browser.client.PlatOnClient;
import com.platon.browser.elasticsearch.dto.Transaction;
import com.platon.browser.enums.ContractDescEnum;
import com.platon.browser.enums.ContractTypeEnum;
import com.platon.browser.enums.InnerContractAddrEnum;
import com.platon.browser.exception.BeanCreateOrUpdateException;
import com.platon.browser.param.DelegateExitParam;
import com.platon.browser.param.DelegateRewardClaimParam;
import com.platon.browser.decoder.TxInputDecodeUtil;
import com.platon.browser.decoder.TxInputDecodeResult;
import com.platon.browser.decoder.PPOSTxDecodeUtil;
import com.platon.browser.decoder.PPOSTxDecodeResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
public class CollectionTransaction extends Transaction {
    private CollectionTransaction(){}
    public static CollectionTransaction newInstance(){
        Date date = new Date();
        CollectionTransaction transaction = new CollectionTransaction();
        transaction.setCreTime(date)
            .setUpdTime(date)
            .setCost(BigDecimal.ZERO.toString())
            .setGasLimit(BigDecimal.ZERO.toString())
            .setGasPrice(BigDecimal.ZERO.toString())
            .setGasUsed(BigDecimal.ZERO.toString())
            .setStatus(StatusEnum.FAILURE.getCode())
            .setValue(BigDecimal.ZERO.toString())
            .setIndex(0);
        return transaction;
    }

    CollectionTransaction updateWithBlock(CollectionBlock block){
        this.setTime(block.getTime());
        return this;
    }

    CollectionTransaction updateWithRawTransaction(com.platon.protocol.core.methods.response.Transaction transaction){
        this.setNum(transaction.getBlockNumber().longValue())
            .setBHash(transaction.getBlockHash())
            .setHash(transaction.getHash())
            .setValue(transaction.getValue().toString())
            .setIndex(transaction.getTransactionIndex().intValue())
            .setGasPrice(transaction.getGasPrice().toString())
            .setInput(transaction.getInput())
            .setTo(transaction.getTo())
            .setFrom(transaction.getFrom())
            .setGasLimit(transaction.getGas().toString())
            .setNonce(transaction.getNonce().toString());
        return this;
    }

    static class ComplementInfo{
        // ????????????
        Integer type=null;
        Integer toType=null;
        // ????????????
        String binCode = null;
        // ????????????
        String method = null;
        // ????????????
        Integer contractType = null;
        // tx info??????
        String info = "{}";
    }
    CollectionTransaction updateWithBlockAndReceipt(CollectionBlock block, Receipt receipt) throws BeanCreateOrUpdateException {


        //============???????????????????????????????????????============
        ComplementInfo ci = new ComplementInfo();
        String inputWithoutPrefix = StringUtils.isNotBlank(getInput())?getInput().replace("0x",""):"";
        if(InnerContractAddrEnum.getAddresses().contains(getTo())&&StringUtils.isNotBlank(inputWithoutPrefix)){
            // ??????to???????????????????????????????????????????????????
            resolveInnerContractInvokeTxComplementInfo(receipt.getLogs(),ci);
        }else{
            if(StringUtils.isBlank(getTo())) {
                // ??????to????????????????????????????????????
                resolveGeneralContractCreateTxComplementInfo(receipt.getContractAddress(),ci);
                // ?????????????????????????????????????????????to??????
                setTo(receipt.getContractAddress());
            }else{
                if(inputWithoutPrefix.length()>=8){
                    // ??????????????????????????????EVM||WASM???
                    resolveGeneralContractInvokeTxComplementInfo(ci);
                }else {
                    BigInteger value = StringUtils.isNotBlank(getValue())?new BigInteger(getValue()):BigInteger.ZERO;
                    if(value.compareTo(BigInteger.ZERO)>=0){
                        // ?????????????????????value??????0?????????????????????
                        resolveGeneralTransferTxComplementInfo(ci);
                    }
                }
            }
        }
        
        if(ci.type==null){
            throw new BeanCreateOrUpdateException("??????????????????,??????????????????:[blockNumber="+getNum()+",txHash="+getHash()+"]");
        }
        if(ci.toType==null){
            throw new BeanCreateOrUpdateException("To????????????:[blockNumber="+getNum()+",txHash="+getHash()+"]");
        }
        // ??????????????????????????????????????????????????????
        int status = receipt.getStatus();
        if (InnerContractAddrEnum.getAddresses().contains(getTo()) && ci.type.intValue() != TypeEnum.TRANSFER.getCode()) {
            // ?????????????????????????????????????????????, ??????????????????????????????????????????????????????
            status=receipt.getLogStatus();
        }

        // ????????????
        this.setGasUsed(receipt.getGasUsed().toString())
                .setCost(decimalGasUsed().multiply(decimalGasPrice()).toString())
                .setFailReason(receipt.getFailReason())
                .setStatus(status)
                .setSeq(getNum()*100000+getIndex())
                .setInfo(ci.info)
                .setType(ci.type)
                .setToType(ci.toType)
                .setContractAddress(receipt.getContractAddress())
                .setContractType(ci.contractType)
                .setBin(ci.binCode)
                .setMethod(ci.method);

        // ??????????????????
        block.setTxQty(block.getTxQty()+1);
        // ???????????????????????????
        switch (getTypeEnum()){
            case TRANSFER: // ???????????????from???????????????????????????
                block.setTranQty(block.getTranQty()+1);
                break;
            case STAKE_CREATE:// ???????????????
            case STAKE_INCREASE:// ??????????????????
            case STAKE_MODIFY:// ???????????????
            case STAKE_EXIT:// ???????????????
            case REPORT:// ???????????????
                block.setSQty(block.getSQty()+1);
                break;
            case DELEGATE_CREATE:// ????????????
                block.setDQty(block.getDQty()+1);
                break;
            case DELEGATE_EXIT:// ????????????
                if(status==Receipt.SUCCESS){
                    // ??????????????????????????????info??????
                    // ???????????????????????????
                    DelegateExitParam param = getTxParam(DelegateExitParam.class);
                    BigDecimal reward = new BigDecimal(getDelegateReward(receipt.getLogs()));
                    param.setReward(reward);
                    setInfo(param.toJSONString());
                }
                block.setDQty(block.getDQty()+1);
                break;
            case CLAIM_REWARDS: // ??????????????????
                DelegateRewardClaimParam param = DelegateRewardClaimParam.builder()
                        .rewardList(new ArrayList<>())
                        .build();
                if(status==Receipt.SUCCESS){
                    // ??????????????????????????????info??????
                    param = getTxParam(DelegateRewardClaimParam.class);
                }
                setInfo(param.toJSONString());
                block.setDQty(block.getDQty()+1);
                break;
            case PROPOSAL_TEXT:// ??????????????????
            case PROPOSAL_UPGRADE:// ??????????????????
            case PROPOSAL_PARAMETER:// ??????????????????
            case PROPOSAL_VOTE:// ????????????
            case PROPOSAL_CANCEL:// ????????????
            case VERSION_DECLARE:// ????????????
                block.setPQty(block.getPQty()+1);
                break;
            default:
        }
        // ????????????????????????????????????????????????txFee
        block.setTxFee(block.decimalTxFee().add(decimalCost()).toString());
        // ???????????????????????????????????????????????????txGasLimit
        block.setTxGasLimit(block.decimalTxGasLimit().add(decimalGasLimit()).toString());
        return this;
    }

    /**
     *  ?????????????????????????????????????????????
     */
    private BigInteger getDelegateReward(List<Log> logs) {
        if(logs==null||logs.isEmpty()) return BigInteger.ZERO;

        String logData = logs.get(0).getData();
        if(null == logData || "".equals(logData) ) return BigInteger.ZERO;

        RlpList rlp = RlpDecoder.decode(Numeric.hexStringToByteArray(logData));
        List<RlpType> rlpList = ((RlpList)(rlp.getValues().get(0))).getValues();
        String decodedStatus = new String(((RlpString)rlpList.get(0)).getBytes());
        int statusCode = Integer.parseInt(decodedStatus);

        if(statusCode != ErrorCode.SUCCESS) return BigInteger.ZERO;

        return ((RlpString)(RlpDecoder.decode(((RlpString)rlpList.get(1)).getBytes())).getValues().get(0)).asPositiveBigInteger();
    }

    /**
     * ????????????????????????,??????????????????
     */
    private void resolveInnerContractInvokeTxComplementInfo(List<Log> logs,ComplementInfo ci) throws BeanCreateOrUpdateException {
        PPOSTxDecodeResult decodedResult;
        try {
            // ????????????????????????????????????log??????
            decodedResult = PPOSTxDecodeUtil.decode(getInput(),logs);
            ci.type = decodedResult.getTypeEnum().getCode();
            ci.info = decodedResult.getParam().toJSONString();
            ci.toType = ToTypeEnum.INNER_CONTRACT.getCode();
            ci.contractType = ContractTypeEnum.INNER.getCode();
            ci.method = null;
            ci.binCode = null;
        } catch (Exception e) {
            throw new BeanCreateOrUpdateException("??????[hash:" + this.getHash() + "]?????????????????????:" + e.getMessage());
        }
    }


    private String getContractBinCode(PlatOnClient platOnClient,String contractAddress) throws BeanCreateOrUpdateException {
        try {
            PlatonGetCode platonGetCode = platOnClient.getWeb3jWrapper().getWeb3j().platonGetCode(contractAddress,
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(getNum()))).send();
            return platonGetCode.getCode();
        }catch (Exception e){
            platOnClient.updateCurrentWeb3jWrapper();
            String error = "????????????????????????["+contractAddress+"]:"+e.getMessage();
            log.error("{}",error);
            throw new BeanCreateOrUpdateException(error);
        }
    }

    /**
     * ????????????????????????,??????????????????
     * @param contractAddress
     * @param ci
     * @throws IOException
     */
    private void resolveGeneralContractCreateTxComplementInfo(String contractAddress,ComplementInfo ci) throws BeanCreateOrUpdateException {
        ci.info="";
        //?????????????????????????????????????????????EVM||WASM
        TxInputDecodeResult decodedResult = TxInputDecodeUtil.decode(getInput());
        ci.type = decodedResult.getTypeEnum().getCode();
        ci.toType=ToTypeEnum.ACCOUNT.getCode();
        ci.contractType = ContractTypeEnum.UNKNOWN.getCode();
        if(decodedResult.getTypeEnum()==TypeEnum.EVM_CONTRACT_CREATE){
            ci.toType = ToTypeEnum.EVM_CONTRACT.getCode();
            ci.contractType = ContractTypeEnum.EVM.getCode();
        }
        if(decodedResult.getTypeEnum()==TypeEnum.WASM_CONTRACT_CREATE){
            ci.toType = ToTypeEnum.WASM_CONTRACT.getCode();
            ci.contractType = ContractTypeEnum.WASM.getCode();
        }
    }

    /**
     * ????????????????????????,??????????????????
     * @param ci
     * @throws IOException
     */
    private void resolveGeneralContractInvokeTxComplementInfo(ComplementInfo ci) throws BeanCreateOrUpdateException {
        ci.info="";
        ci.toType = ToTypeEnum.EVM_CONTRACT.getCode();
        ci.contractType = ContractTypeEnum.EVM.getCode();
        ci.type = TypeEnum.CONTRACT_EXEC.getCode();
    }

    /**
     * ??????????????????,??????????????????
     * @param ci
     */
    private void resolveGeneralTransferTxComplementInfo(ComplementInfo ci){
        ci.type = TypeEnum.TRANSFER.getCode();
        ci.contractType = null;
        ci.method = null;
        ci.info = "{}";
        ci.binCode = null;
        // ?????????????????????to????????????????????????????????????
        if(InnerContractAddrEnum.getAddresses().contains(getTo())) {
        	ci.toType = ToTypeEnum.INNER_CONTRACT.getCode();
        	ci.contractType = ContractTypeEnum.INNER.getCode();
        	ci.method = ContractDescEnum.getMap().get(getTo()).getContractName();
        } else {
        	ci.toType = ToTypeEnum.ACCOUNT.getCode();
        }
    }
}
