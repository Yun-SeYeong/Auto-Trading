package demo.coin.dao;

import javax.persistence.Id;
import java.io.Serializable;
import java.time.LocalDateTime;

public class MarketOrderKey implements Serializable {
    @Id
    private String market;

    @Id
    private LocalDateTime candleDateTimeUtc;
}
