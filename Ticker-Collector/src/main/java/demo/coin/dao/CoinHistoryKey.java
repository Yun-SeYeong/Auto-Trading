package demo.coin.dao;

import java.io.Serializable;
import java.time.LocalDate;

public class CoinHistoryKey implements Serializable {
    private LocalDate createdTime;
    private String name;
}
