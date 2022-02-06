package demo.coin.dao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@IdClass(MarketOrderKey.class)
public class MarketOrder {
    @Id
    private String market;

    @Id
    private LocalDateTime candleDateTimeUtc;

    private BigDecimal targetPrice;

    private LocalDateTime buyTime;
}
