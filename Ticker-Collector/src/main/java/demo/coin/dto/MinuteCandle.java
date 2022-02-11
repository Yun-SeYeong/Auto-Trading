package demo.coin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class MinuteCandle {
    String market;
    LocalDateTime candleDateTimeUtc;
    LocalDateTime candleDateTimeKst;
    BigDecimal openingPrice;
    BigDecimal highPrice;
    BigDecimal lowPrice;
    BigDecimal tradePrice;
    Long timestamp;
    BigDecimal candleAccTradePrice;
    BigDecimal candleAccTradeVolume;
    int unit;
}
