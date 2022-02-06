package demo.coin.dao;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
@IdClass(DayCandleKey.class)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class DayCandle {

    @Id
    private String market;

    @Id
    private Double timestamp;

    private LocalDateTime candleDateTimeUtc;

    private LocalDateTime candleDateTimeKst;

    private BigDecimal openingPrice;

    private BigDecimal highPrice;

    private BigDecimal lowPrice;

    private BigDecimal tradePrice;

    private BigDecimal candleAccTradePrice;

    private BigDecimal candleAccTradeVolume;

    private BigDecimal prevClosingPrice;

    private BigDecimal changeRate;
}
