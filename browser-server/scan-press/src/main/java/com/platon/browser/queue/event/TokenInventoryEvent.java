package com.platon.browser.queue.event;

import com.platon.browser.dao.entity.Token721Inventory;
import lombok.Data;

import java.util.List;

@Data
public class TokenInventoryEvent implements Event {

    private List<Token721Inventory> tokenList;

}
