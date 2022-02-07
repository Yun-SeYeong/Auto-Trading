package demo.coin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Orderbook {
    String market;
    Long timestamp;
    BigDecimal totalAskSize;
    BigDecimal totalBidSize;
    List<OrderbookUnit> orderbookUnits;

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class OrderbookUnit {
        BigDecimal askPrice;
        BigDecimal bidPrice;
        BigDecimal askSize;
        BigDecimal bidSize;
    }
}
